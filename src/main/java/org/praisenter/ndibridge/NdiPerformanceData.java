package org.praisenter.ndibridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.praisenter.ndibridge.internal.generated.NDIlib_recv_performance_t;

/** Total and dropped frame counts updated by {@link NdiReceiver#queryPerformance(NdiPerformanceData)}. */
public final class NdiPerformanceData implements AutoCloseable {
    private final Arena arena = Arena.ofShared();
    private final MemorySegment total = NDIlib_recv_performance_t.allocate(arena);
    private final MemorySegment dropped = NDIlib_recv_performance_t.allocate(arena);
    private boolean closed;

    public synchronized long getTotalVideoFrames() { ensureOpen(); return NDIlib_recv_performance_t.video_frames(total); }
    public synchronized long getTotalAudioFrames() { ensureOpen(); return NDIlib_recv_performance_t.audio_frames(total); }
    public synchronized long getTotalMetadataFrames() { ensureOpen(); return NDIlib_recv_performance_t.metadata_frames(total); }
    public synchronized long getDroppedVideoFrames() { ensureOpen(); return NDIlib_recv_performance_t.video_frames(dropped); }
    public synchronized long getDroppedAudioFrames() { ensureOpen(); return NDIlib_recv_performance_t.audio_frames(dropped); }
    public synchronized long getDroppedMetadataFrames() { ensureOpen(); return NDIlib_recv_performance_t.metadata_frames(dropped); }

    synchronized MemorySegment totalStruct() { ensureOpen(); return total; }
    synchronized MemorySegment droppedStruct() { ensureOpen(); return dropped; }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Performance data is closed");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        arena.close();
    }
}