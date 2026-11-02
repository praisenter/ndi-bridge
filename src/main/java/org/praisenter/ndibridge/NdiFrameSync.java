package org.praisenter.ndibridge;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.praisenter.ndibridge.internal.NdiRuntime;
import org.praisenter.ndibridge.internal.generated.NDIlib_v6_3;

/** Captures time-corrected audio and video from a receiver. */
public final class NdiFrameSync implements NdiFrameBufferOwner, AutoCloseable {
    private final MemorySegment instance;
    private final MemorySegment destroy;
    private final MemorySegment captureAudio;
    private final MemorySegment freeAudio;
    private final MemorySegment captureVideo;
    private final MemorySegment freeVideo;
    private final Set<NdiAbstractFrame> ownedFrames = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;

    public NdiFrameSync(NdiReceiver receiver) {
        MemorySegment receiverInstance = receiver.instance();
        NdiRuntime runtime = NdiBridge.runtime();
        MemorySegment table = runtime.table();
        destroy = NdiRuntime.requireFunction(NDIlib_v6_3.framesync_destroy(table), "framesync_destroy");
        captureAudio = NdiRuntime.requireFunction(NDIlib_v6_3.framesync_capture_audio(table), "framesync_capture_audio");
        freeAudio = NdiRuntime.requireFunction(NDIlib_v6_3.framesync_free_audio(table), "framesync_free_audio");
        captureVideo = NdiRuntime.requireFunction(NDIlib_v6_3.framesync_capture_video(table), "framesync_capture_video");
        freeVideo = NdiRuntime.requireFunction(NDIlib_v6_3.framesync_free_video(table), "framesync_free_video");
        MemorySegment create = NdiRuntime.requireFunction(NDIlib_v6_3.framesync_create(table), "framesync_create");
        instance = NDIlib_v6_3.framesync_create.invoke(create, receiverInstance);
        if (instance.equals(MemorySegment.NULL)) throw new IllegalStateException("NDI frame-sync creation failed");
    }

    public synchronized void captureAudio(NdiAudioFrame frame, int sampleRate, int channelCount, int sampleCount) {
        ensureOpen();
        if (sampleRate < 0 || channelCount < 0 || sampleCount < 0) throw new IllegalArgumentException("Audio format values must not be negative");
        frame.prepareForReceive();
        NDIlib_v6_3.framesync_capture_audio.invoke(captureAudio, instance, frame.struct(), sampleRate, channelCount, sampleCount);
        ownedFrames.add(frame);
        frame.attachBufferOwner(this);
    }

    public synchronized boolean captureVideo(NdiVideoFrame frame, NdiFrameFormatType formatType) {
        ensureOpen();
        frame.prepareForReceive();
        NDIlib_v6_3.framesync_capture_video.invoke(captureVideo, instance, frame.struct(), formatType.id);
        if (frame.getData() == null) return false;
        ownedFrames.add(frame);
        frame.attachBufferOwner(this);
        return true;
    }

    public boolean captureVideo(NdiVideoFrame frame) {
        return captureVideo(frame, NdiFrameFormatType.PROGRESSIVE);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Frame sync is closed");
    }

    @Override
    public synchronized void release(NdiAbstractFrame frame) {
        ensureOpen();
        if (frame instanceof NdiVideoFrame video) NDIlib_v6_3.framesync_free_video.invoke(freeVideo, instance, video.struct());
        else if (frame instanceof NdiAudioFrame audio) NDIlib_v6_3.framesync_free_audio.invoke(freeAudio, instance, audio.struct());
        else throw new IllegalArgumentException("Frame sync does not own this frame type");
        ownedFrames.remove(frame);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        RuntimeException failure = null;
        for (NdiAbstractFrame frame : new ArrayList<>(ownedFrames)) {
            try { frame.freeBuffer(); }
            catch (RuntimeException error) { if (failure == null) failure = error; }
        }
        try { NDIlib_v6_3.framesync_destroy.invoke(destroy, instance); }
        catch (RuntimeException error) { if (failure == null) failure = error; }
        finally { closed = true; }
        if (failure != null) throw failure;
    }
}