package org.praisenter.ndibridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.praisenter.ndibridge.internal.NdiRuntime;
import org.praisenter.ndibridge.internal.generated.NDIlib_v6_3;
import org.praisenter.ndibridge.internal.generated.NDIlib_routing_create_t;

/** Publishes a routed source that can switch between source descriptors. */
public final class NdiRouter implements AutoCloseable {
    private final MemorySegment instance;
    private final MemorySegment destroy;
    private final MemorySegment change;
    private final MemorySegment clear;
    private final MemorySegment connections;
    private final MemorySegment source;
    private boolean closed;

    public NdiRouter(String ndiName, String groups) {
        NdiRuntime runtime = NdiBridge.runtime();
        MemorySegment table = runtime.table();
        destroy = NdiRuntime.requireFunction(NDIlib_v6_3.routing_destroy(table), "routing_destroy");
        change = NdiRuntime.requireFunction(NDIlib_v6_3.routing_change(table), "routing_change");
        clear = NdiRuntime.requireFunction(NDIlib_v6_3.routing_clear(table), "routing_clear");
        connections = NdiRuntime.requireFunction(NDIlib_v6_3.routing_get_no_connections(table), "routing_get_no_connections");
        source = NdiRuntime.requireFunction(NDIlib_v6_3.routing_get_source_name(table), "routing_get_source_name");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment settings = NDIlib_routing_create_t.allocate(arena);
            NDIlib_routing_create_t.p_ndi_name(settings, ndiName == null ? MemorySegment.NULL : arena.allocateFrom(ndiName));
            NDIlib_routing_create_t.p_groups(settings, groups == null ? MemorySegment.NULL : arena.allocateFrom(groups));
                MemorySegment create = NdiRuntime.requireFunction(NDIlib_v6_3.routing_create(table), "routing_create");
                instance = NDIlib_v6_3.routing_create.invoke(create, settings);
        }
        if (instance.equals(MemorySegment.NULL)) throw new IllegalStateException("NDI router creation failed");
    }

    public NdiRouter(String ndiName) { this(ndiName, null); }
    public NdiRouter() { this(null, null); }

    public synchronized void setSource(NdiSource source) {
        ensureOpen();
        if (source == null) throw new NullPointerException("Use clearSource() to disconnect the router");
        if (!NDIlib_v6_3.routing_change.invoke(change, instance, source.struct())) throw new IllegalStateException("NDI rejected the routed source");
    }

    public synchronized NdiSource getSource() {
        ensureOpen();
        return NdiSource.copyOf(NDIlib_v6_3.routing_get_source_name.invoke(source, instance));
    }

    public synchronized void clearSource() {
        ensureOpen();
        NDIlib_v6_3.routing_clear.invoke(clear, instance);
    }

    public synchronized int getConnectionCount(int timeoutMs) {
        ensureOpen();
        if (timeoutMs < 0) throw new IllegalArgumentException("Timeout must not be negative");
        return NDIlib_v6_3.routing_get_no_connections.invoke(connections, instance, timeoutMs);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("NdiRouter is closed");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        NDIlib_v6_3.routing_destroy.invoke(destroy, instance);
    }
}