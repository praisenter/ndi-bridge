package org.praisenter.ndibridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.praisenter.ndibridge.internal.NdiRuntime;
import org.praisenter.ndibridge.internal.generated.NDIlib_send_create_t;
import org.praisenter.ndibridge.internal.generated.NDIlib_tally_t;
import org.praisenter.ndibridge.internal.generated.NDIlib_v6_3;

/** Sends video, audio and metadata frames. */
public final class NdiSender implements NdiFrameBufferOwner, AutoCloseable {
    private final NdiRuntime runtime;
    private final MemorySegment instance;
    private final MemorySegment destroy;
    private final MemorySegment sendVideo;
    private final MemorySegment sendVideoAsync;
    private final MemorySegment sendAudio;
    private final MemorySegment sendAudio16s;
    private final MemorySegment sendAudio32s;
    private final MemorySegment sendAudio32f;
    private final MemorySegment sendMetadata;
    private final MemorySegment sendCapture;
    private final MemorySegment freeMetadata;
    private final MemorySegment getTally;
    private final MemorySegment getConnections;
    private final MemorySegment clearConnectionMetadata;
    private final MemorySegment addConnectionMetadata;
    private final MemorySegment setFailover;
    private final MemorySegment getSource;
    private final Set<NdiMetadataFrame> ownedMetadata = Collections.newSetFromMap(new IdentityHashMap<>());
    private NdiVideoFrame pendingAsyncFrame;
    private Object pendingAsyncReference;
    private boolean closed;

    public NdiSender(String ndiName, String groups, boolean clockVideo, boolean clockAudio) {
        runtime = NdiBridge.runtime();
        MemorySegment table = runtime.table();
        destroy = NdiRuntime.requireFunction(NDIlib_v6_3.send_destroy(table), "send_destroy");
        sendVideo = NdiRuntime.requireFunction(NDIlib_v6_3.send_send_video_v2(table), "send_send_video_v2");
        sendVideoAsync = NdiRuntime.requireFunction(NDIlib_v6_3.send_send_video_async_v2(table), "send_send_video_async_v2");
        sendAudio = NdiRuntime.requireFunction(NDIlib_v6_3.send_send_audio_v2(table), "send_send_audio_v2");
        sendAudio16s = NdiRuntime.requireFunction(NDIlib_v6_3.util_send_send_audio_interleaved_16s(table), "util_send_send_audio_interleaved_16s");
        sendAudio32s = NdiRuntime.requireFunction(NDIlib_v6_3.util_send_send_audio_interleaved_32s(table), "util_send_send_audio_interleaved_32s");
        sendAudio32f = NdiRuntime.requireFunction(NDIlib_v6_3.util_send_send_audio_interleaved_32f(table), "util_send_send_audio_interleaved_32f");
        sendMetadata = NdiRuntime.requireFunction(NDIlib_v6_3.send_send_metadata(table), "send_send_metadata");
        sendCapture = NdiRuntime.requireFunction(NDIlib_v6_3.send_capture(table), "send_capture");
        freeMetadata = NdiRuntime.requireFunction(NDIlib_v6_3.send_free_metadata(table), "send_free_metadata");
        getTally = NdiRuntime.requireFunction(NDIlib_v6_3.send_get_tally(table), "send_get_tally");
        getConnections = NdiRuntime.requireFunction(NDIlib_v6_3.send_get_no_connections(table), "send_get_no_connections");
        clearConnectionMetadata = NdiRuntime.requireFunction(NDIlib_v6_3.send_clear_connection_metadata(table), "send_clear_connection_metadata");
        addConnectionMetadata = NdiRuntime.requireFunction(NDIlib_v6_3.send_add_connection_metadata(table), "send_add_connection_metadata");
        setFailover = NdiRuntime.requireFunction(NDIlib_v6_3.send_set_failover(table), "send_set_failover");
        getSource = NdiRuntime.requireFunction(NDIlib_v6_3.send_get_source_name(table), "send_get_source_name");

        try (Arena settingsArena = Arena.ofConfined()) {
            MemorySegment settings = NDIlib_send_create_t.allocate(settingsArena);
            NDIlib_send_create_t.p_ndi_name(settings, ndiName == null ? MemorySegment.NULL : settingsArena.allocateFrom(ndiName));
            NDIlib_send_create_t.p_groups(settings, groups == null ? MemorySegment.NULL : settingsArena.allocateFrom(groups));
            NDIlib_send_create_t.clock_video(settings, clockVideo);
            NDIlib_send_create_t.clock_audio(settings, clockAudio);
                MemorySegment create = NdiRuntime.requireFunction(NDIlib_v6_3.send_create(table), "send_create");
                instance = NDIlib_v6_3.send_create.invoke(create, settings);
        }
        if (instance.equals(MemorySegment.NULL)) throw new IllegalStateException("NDI sender creation failed");
    }

    public NdiSender(String ndiName, String groups) { this(ndiName, groups, true, true); }
    public NdiSender(String ndiName) { this(ndiName, null, true, true); }
    public NdiSender() { this(null, null, true, true); }

    public synchronized void sendVideoFrame(NdiVideoFrame frame) {
        ensureOpen();
        if (frame == null) throw new NullPointerException("Video frame is required");
        NDIlib_v6_3.send_send_video_v2.invoke(sendVideo, instance, frame.struct());
        releasePendingAsync();
    }

    public synchronized void sendVideoFrameAsync(NdiVideoFrame frame) {
        ensureOpen();
        Object newReference = null;
        if (frame != null) {
            frame.acquireAsyncLease(this);
            newReference = frame.asyncDataReference();
        }
        try {
            NDIlib_v6_3.send_send_video_async_v2.invoke(sendVideoAsync, instance, frame == null ? MemorySegment.NULL : frame.struct());
        } catch (RuntimeException failure) {
            if (frame != null) frame.releaseAsyncLease(this);
            throw failure;
        }
        releasePendingAsync();
        if (frame != null) {
            pendingAsyncFrame = frame;
            pendingAsyncReference = newReference;
        }
    }

    public synchronized void sendAudioFrame(NdiAudioFrame frame) {
        ensureOpen();
        NDIlib_v6_3.send_send_audio_v2.invoke(sendAudio, instance, requireFrame(frame).struct());
    }

    public synchronized void sendAudioFrameInterleaved16s(NdiAudioFrameInterleaved16s frame) {
        ensureOpen();
        NDIlib_v6_3.util_send_send_audio_interleaved_16s.invoke(sendAudio16s, instance, requireFrame(frame).struct());
    }

    public synchronized void sendAudioFrameInterleaved32s(NdiAudioFrameInterleaved32s frame) {
        ensureOpen();
        NDIlib_v6_3.util_send_send_audio_interleaved_32s.invoke(sendAudio32s, instance, requireFrame(frame).struct());
    }

    public synchronized void sendAudioFrameInterleaved32f(NdiAudioFrameInterleaved32f frame) {
        ensureOpen();
        NDIlib_v6_3.util_send_send_audio_interleaved_32f.invoke(sendAudio32f, instance, requireFrame(frame).struct());
    }

    public synchronized void sendMetadataFrame(NdiMetadataFrame frame) {
        ensureOpen();
        NDIlib_v6_3.send_send_metadata.invoke(sendMetadata, instance, requireFrame(frame).struct());
    }

    public synchronized NdiFrameType sendCapture(NdiMetadataFrame frame, int timeoutMs) {
        ensureOpen();
        if (timeoutMs < 0) throw new IllegalArgumentException("Timeout must not be negative");
        if (frame != null) frame.freeBuffer();
        int result = NDIlib_v6_3.send_capture.invoke(sendCapture, instance, frame == null ? MemorySegment.NULL : frame.struct(), timeoutMs);
        NdiFrameType type = NdiFrameType.valueOf(result);
        if (type == NdiFrameType.METADATA && frame != null) {
            frame.attachBufferOwner(this);
            ownedMetadata.add(frame);
        }
        return type;
    }

    public synchronized NdiTally getTally(int timeoutMs) {
        ensureOpen();
        if (timeoutMs < 0) throw new IllegalArgumentException("Timeout must not be negative");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment tally = NDIlib_tally_t.allocate(arena);
            if (!NDIlib_v6_3.send_get_tally.invoke(getTally, instance, tally, timeoutMs)) return null;
            return new NdiTally(NDIlib_tally_t.on_program(tally), NDIlib_tally_t.on_preview(tally));
        }
    }

    public synchronized int getConnectionCount(int timeoutMs) {
        ensureOpen();
        if (timeoutMs < 0) throw new IllegalArgumentException("Timeout must not be negative");
        return NDIlib_v6_3.send_get_no_connections.invoke(getConnections, instance, timeoutMs);
    }

    public synchronized void clearConnectionMetadata() {
        ensureOpen();
        NDIlib_v6_3.send_clear_connection_metadata.invoke(clearConnectionMetadata, instance);
    }

    public synchronized void addConnectionMetadata(NdiMetadataFrame frame) {
        ensureOpen();
        NDIlib_v6_3.send_add_connection_metadata.invoke(addConnectionMetadata, instance, requireFrame(frame).struct());
    }

    public synchronized void setFailoverSource(NdiSource source) {
        ensureOpen();
        NDIlib_v6_3.send_set_failover.invoke(setFailover, instance, source == null ? MemorySegment.NULL : source.struct());
    }

    public synchronized NdiSource getSource() {
        ensureOpen();
        return NdiSource.copyOf(NDIlib_v6_3.send_get_source_name.invoke(getSource, instance));
    }

    private <T extends NdiAbstractFrame> T requireFrame(T frame) {
        if (frame == null) throw new NullPointerException("Frame is required");
        return frame;
    }

    private void releasePendingAsync() {
        if (pendingAsyncFrame != null) {
            pendingAsyncFrame.releaseAsyncLease(this);
            pendingAsyncFrame = null;
            pendingAsyncReference = null;
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("NdiSender is closed");
    }

    @Override
    public synchronized void release(NdiAbstractFrame frame) {
        ensureOpen();
        if (!(frame instanceof NdiMetadataFrame metadata)) throw new IllegalArgumentException("NdiSender only owns captured metadata frames");
        NDIlib_v6_3.send_free_metadata.invoke(freeMetadata, instance, metadata.struct());
        ownedMetadata.remove(metadata);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        RuntimeException failure = null;
        if (pendingAsyncFrame != null) {
            try { NDIlib_v6_3.send_send_video_async_v2.invoke(sendVideoAsync, instance, MemorySegment.NULL); }
            catch (RuntimeException error) { failure = error; }
        }
        for (NdiMetadataFrame frame : new ArrayList<>(ownedMetadata)) {
            try { frame.freeBuffer(); }
            catch (RuntimeException error) { if (failure == null) failure = error; }
        }
        try { NDIlib_v6_3.send_destroy.invoke(destroy, instance); }
        catch (RuntimeException error) { if (failure == null) failure = error; }
        finally {
            releasePendingAsync();
            closed = true;
        }
        if (failure != null) throw failure;
    }
}