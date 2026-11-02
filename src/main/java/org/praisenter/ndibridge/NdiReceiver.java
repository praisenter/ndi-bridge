package org.praisenter.ndibridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import org.praisenter.ndibridge.internal.NdiRuntime;
import org.praisenter.ndibridge.internal.NdiStrings;
import org.praisenter.ndibridge.internal.generated.NDIlib_recv_create_v3_t;
import org.praisenter.ndibridge.internal.generated.NDIlib_source_t;
import org.praisenter.ndibridge.internal.generated.NDIlib_tally_t;
import org.praisenter.ndibridge.internal.generated.NDIlib_v6_3;

/** Receives frames from an NDI source. */
public final class NdiReceiver implements NdiFrameBufferOwner, AutoCloseable {
    public static final int RECEIVE_BANDWIDTH_METADATA_ONLY = -10;
    public static final int RECEIVE_BANDWIDTH_AUDIO_ONLY = 10;
    public static final int RECEIVE_BANDWIDTH_LOWEST = 0;
    public static final int RECEIVE_BANDWIDTH_HIGHEST = 100;

    public enum NdiColorFormat {
        BGRX_BGRA(0), UYVY_BGRA(1), RGBX_RGBA(2), UYVY_RGBA(3), FASTEST(100), BEST(101);
        final int id;
        NdiColorFormat(int id) { this.id = id; }
    }

    private final MemorySegment instance;
    private final MemorySegment destroy;
    private final MemorySegment connect;
    private final MemorySegment capture;
    private final MemorySegment freeVideo;
    private final MemorySegment freeAudio;
    private final MemorySegment freeMetadata;
    private final MemorySegment sendMetadata;
    private final MemorySegment setTally;
    private final MemorySegment getPerformance;
    private final MemorySegment clearConnectionMetadata;
    private final MemorySegment addConnectionMetadata;
    private final MemorySegment getConnections;
    private final MemorySegment getWebControl;
    private final MemorySegment freeString;
    private final Set<NdiAbstractFrame> ownedFrames = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;

    public NdiReceiver(NdiSource source, NdiColorFormat colorFormat, int receiveBandwidth,
                    boolean allowVideoFields, String name) {
        NdiRuntime runtime = NdiBridge.runtime();
        MemorySegment table = runtime.table();
        destroy = NdiRuntime.requireFunction(NDIlib_v6_3.recv_destroy(table), "recv_destroy");
        connect = NdiRuntime.requireFunction(NDIlib_v6_3.recv_connect(table), "recv_connect");
        capture = NdiRuntime.requireFunction(NDIlib_v6_3.recv_capture_v2(table), "recv_capture_v2");
        freeVideo = NdiRuntime.requireFunction(NDIlib_v6_3.recv_free_video_v2(table), "recv_free_video_v2");
        freeAudio = NdiRuntime.requireFunction(NDIlib_v6_3.recv_free_audio_v2(table), "recv_free_audio_v2");
        freeMetadata = NdiRuntime.requireFunction(NDIlib_v6_3.recv_free_metadata(table), "recv_free_metadata");
        sendMetadata = NdiRuntime.requireFunction(NDIlib_v6_3.recv_send_metadata(table), "recv_send_metadata");
        setTally = NdiRuntime.requireFunction(NDIlib_v6_3.recv_set_tally(table), "recv_set_tally");
        getPerformance = NdiRuntime.requireFunction(NDIlib_v6_3.recv_get_performance(table), "recv_get_performance");
        clearConnectionMetadata = NdiRuntime.requireFunction(NDIlib_v6_3.recv_clear_connection_metadata(table), "recv_clear_connection_metadata");
        addConnectionMetadata = NdiRuntime.requireFunction(NDIlib_v6_3.recv_add_connection_metadata(table), "recv_add_connection_metadata");
        getConnections = NdiRuntime.requireFunction(NDIlib_v6_3.recv_get_no_connections(table), "recv_get_no_connections");
        getWebControl = NdiRuntime.requireFunction(NDIlib_v6_3.recv_get_web_control(table), "recv_get_web_control");
        freeString = NdiRuntime.requireFunction(NDIlib_v6_3.recv_free_string(table), "recv_free_string");

        if (colorFormat == null) throw new NullPointerException("Color format is required");
        try (Arena settingsArena = Arena.ofConfined()) {
            MemorySegment settings = NDIlib_recv_create_v3_t.allocate(settingsArena);
            MemorySegment nestedSource = NDIlib_recv_create_v3_t.source_to_connect_to(settings);
            if (source != null) {
                MemorySegment sourceStruct = source.struct();
                NDIlib_source_t.p_ndi_name(nestedSource, NDIlib_source_t.p_ndi_name(sourceStruct));
                NDIlib_source_t.p_url_address(nestedSource, NDIlib_source_t.p_url_address(sourceStruct));
            }
            NDIlib_recv_create_v3_t.color_format(settings, colorFormat.id);
            NDIlib_recv_create_v3_t.bandwidth(settings, receiveBandwidth);
            NDIlib_recv_create_v3_t.allow_video_fields(settings, allowVideoFields);
            NDIlib_recv_create_v3_t.p_ndi_recv_name(settings, name == null ? MemorySegment.NULL : settingsArena.allocateFrom(name));
                MemorySegment create = NdiRuntime.requireFunction(NDIlib_v6_3.recv_create_v3(table), "recv_create_v3");
                instance = NDIlib_v6_3.recv_create_v3.invoke(create, settings);
        }
        if (instance.equals(MemorySegment.NULL)) throw new IllegalStateException("NDI receiver creation failed");
    }

    public NdiReceiver(NdiSource source) { this(source, NdiColorFormat.UYVY_BGRA, RECEIVE_BANDWIDTH_HIGHEST, true, null); }
    public NdiReceiver(NdiColorFormat colorFormat, int receiveBandwidth, boolean allowVideoFields, String name) {
        this(null, colorFormat, receiveBandwidth, allowVideoFields, name);
    }
    public NdiReceiver() { this(null, NdiColorFormat.UYVY_BGRA, RECEIVE_BANDWIDTH_HIGHEST, true, null); }

    public synchronized void connect(NdiSource source) {
        ensureOpen();
        NDIlib_v6_3.recv_connect.invoke(connect, instance, source == null ? MemorySegment.NULL : source.struct());
    }

    public synchronized NdiFrameType receiveCapture(NdiVideoFrame video, NdiAudioFrame audio, NdiMetadataFrame metadata, int timeoutMs) {
        ensureOpen();
        if (timeoutMs < 0) throw new IllegalArgumentException("Timeout must not be negative");
        if (video != null) video.prepareForReceive();
        if (audio != null) audio.prepareForReceive();
        if (metadata != null) metadata.prepareForReceive();
        int result = NDIlib_v6_3.recv_capture_v2.invoke(capture, instance,
                video == null ? MemorySegment.NULL : video.struct(),
                audio == null ? MemorySegment.NULL : audio.struct(),
                metadata == null ? MemorySegment.NULL : metadata.struct(), timeoutMs);
        NdiFrameType type = NdiFrameType.valueOf(result);
        NdiAbstractFrame received = switch (type) {
            case VIDEO -> video;
            case AUDIO -> audio;
            case METADATA -> metadata;
            default -> null;
        };
        if (received != null) {
            ownedFrames.add(received);
            received.attachBufferOwner(this);
        }
        return type;
    }

    public synchronized boolean sendMetadata(NdiMetadataFrame frame) {
        ensureOpen();
        return NDIlib_v6_3.recv_send_metadata.invoke(sendMetadata, instance, Objects.requireNonNull(frame).struct());
    }

    public synchronized void freeMetadata(NdiMetadataFrame frame) {
        ensureOpen();
        Objects.requireNonNull(frame).freeBuffer();
    }

    public synchronized boolean setTally(NdiTally tally) {
        ensureOpen();
        Objects.requireNonNull(tally);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = NDIlib_tally_t.allocate(arena);
            NDIlib_tally_t.on_program(value, tally.isOnProgram());
            NDIlib_tally_t.on_preview(value, tally.isOnPreview());
            return NDIlib_v6_3.recv_set_tally.invoke(setTally, instance, value);
        }
    }

    public synchronized void queryPerformance(NdiPerformanceData performanceData) {
        ensureOpen();
        Objects.requireNonNull(performanceData);
        NDIlib_v6_3.recv_get_performance.invoke(getPerformance, instance, performanceData.totalStruct(), performanceData.droppedStruct());
    }

    public synchronized void clearConnectionMetadata() {
        ensureOpen();
        NDIlib_v6_3.recv_clear_connection_metadata.invoke(clearConnectionMetadata, instance);
    }

    public synchronized void addConnectionMetadata(NdiMetadataFrame frame) {
        ensureOpen();
        NDIlib_v6_3.recv_add_connection_metadata.invoke(addConnectionMetadata, instance, Objects.requireNonNull(frame).struct());
    }

    public synchronized int getConnectionCount() {
        ensureOpen();
        return NDIlib_v6_3.recv_get_no_connections.invoke(getConnections, instance);
    }

    synchronized MemorySegment instance() {
        ensureOpen();
        return instance;
    }

    public synchronized String getWebControl() {
        ensureOpen();
        MemorySegment url = NDIlib_v6_3.recv_get_web_control.invoke(getWebControl, instance);
        if (url.equals(MemorySegment.NULL)) return null;
        try {
            return NdiStrings.copy(url);
        } finally {
            NDIlib_v6_3.recv_free_string.invoke(freeString, instance, url);
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("NdiReceiver is closed");
    }

    @Override
    public synchronized void release(NdiAbstractFrame frame) {
        ensureOpen();
        if (frame instanceof NdiVideoFrame video) NDIlib_v6_3.recv_free_video_v2.invoke(freeVideo, instance, video.struct());
        else if (frame instanceof NdiAudioFrame audio) NDIlib_v6_3.recv_free_audio_v2.invoke(freeAudio, instance, audio.struct());
        else if (frame instanceof NdiMetadataFrame metadata) NDIlib_v6_3.recv_free_metadata.invoke(freeMetadata, instance, metadata.struct());
        else throw new IllegalArgumentException("NdiReceiver does not own this frame type");
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
        try { NDIlib_v6_3.recv_destroy.invoke(destroy, instance); }
        catch (RuntimeException error) { if (failure == null) failure = error; }
        finally { closed = true; }
        if (failure != null) throw failure;
    }
}