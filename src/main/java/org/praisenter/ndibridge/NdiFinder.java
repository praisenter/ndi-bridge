package org.praisenter.ndibridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import org.praisenter.ndibridge.internal.generated.NDIlib_v6_3;
import org.praisenter.ndibridge.internal.NdiRuntime;
import org.praisenter.ndibridge.internal.generated.NDIlib_find_create_t;
import org.praisenter.ndibridge.internal.generated.NDIlib_source_t;

/** Discovers sources visible on the local network. */
public final class NdiFinder implements AutoCloseable {
    private final NdiRuntime runtime;
    private final MemorySegment instance;
    private final MemorySegment destroy;
    private final MemorySegment getSources;
    private final MemorySegment waitForSources;
    private boolean closed;

    public NdiFinder() { this(true, null, null); }
    public NdiFinder(boolean showLocalSources) { this(showLocalSources, null, null); }
    public NdiFinder(boolean showLocalSources, String groups) { this(showLocalSources, groups, null); }

    public NdiFinder(boolean showLocalSources, String groups, String extraIps) {
        runtime = NdiBridge.runtime();
        MemorySegment table = runtime.table();
        destroy = NdiRuntime.requireFunction(NDIlib_v6_3.find_destroy(table), "find_destroy");
        getSources = NdiRuntime.requireFunction(NDIlib_v6_3.find_get_current_sources(table), "find_get_current_sources");
        waitForSources = NdiRuntime.requireFunction(NDIlib_v6_3.find_wait_for_sources(table), "find_wait_for_sources");
        try (Arena settingsArena = Arena.ofConfined()) {
            MemorySegment settings = NDIlib_find_create_t.allocate(settingsArena);
            NDIlib_find_create_t.show_local_sources(settings, showLocalSources);
            NDIlib_find_create_t.p_groups(settings, groups == null ? MemorySegment.NULL : settingsArena.allocateFrom(groups));
            NDIlib_find_create_t.p_extra_ips(settings, extraIps == null ? MemorySegment.NULL : settingsArena.allocateFrom(extraIps));
                MemorySegment create = NdiRuntime.requireFunction(NDIlib_v6_3.find_create_v2(table), "find_create_v2");
                instance = NDIlib_v6_3.find_create_v2.invoke(create, settings);
        }
        if (instance.equals(MemorySegment.NULL)) throw new IllegalStateException("NDI finder creation failed");
    }

    /** Returns independent copies; callers should close each returned source. */
    public synchronized NdiSource[] getCurrentSources() {
        ensureOpen();
        try (Arena outputArena = Arena.ofConfined()) {
            MemorySegment count = outputArena.allocate(ValueLayout.JAVA_INT);
            MemorySegment sources = NDIlib_v6_3.find_get_current_sources.invoke(getSources, instance, count);
            long sourceCount = Integer.toUnsignedLong(count.get(ValueLayout.JAVA_INT, 0));
            if (sourceCount == 0 || sources.equals(MemorySegment.NULL)) return new NdiSource[0];
            if (sourceCount > 100_000) throw new IllegalStateException("NDI finder returned an unreasonable source count: " + sourceCount);
            long structSize = NDIlib_source_t.sizeof();
            MemorySegment array = sources.reinterpret(Math.multiplyExact(sourceCount, structSize), outputArena, null);
            List<NdiSource> result = new ArrayList<>((int) sourceCount);
            for (long index = 0; index < sourceCount; index++) {
                result.add(NdiSource.copyOf(array.asSlice(Math.multiplyExact(index, structSize), structSize)));
            }
            return result.toArray(NdiSource[]::new);
        }
    }

    /** Waits until the source list changes or the timeout expires. */
    public synchronized boolean waitForSources(int timeoutMs) {
        ensureOpen();
        if (timeoutMs < 0) throw new IllegalArgumentException("Timeout must not be negative");
        return NDIlib_v6_3.find_wait_for_sources.invoke(waitForSources, instance, timeoutMs);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("NdiFinder is closed");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        NDIlib_v6_3.find_destroy.invoke(destroy, instance);
    }
}