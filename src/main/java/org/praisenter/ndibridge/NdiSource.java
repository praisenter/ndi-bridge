package org.praisenter.ndibridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import org.praisenter.ndibridge.internal.NdiStrings;
import org.praisenter.ndibridge.internal.generated.NDIlib_source_t;

/** An owned copy of an NDI source descriptor. */
public final class NdiSource implements AutoCloseable {
    private final Arena arena = Arena.ofShared();
    private final MemorySegment struct = NDIlib_source_t.allocate(arena);
    private final String sourceName;
    private final String urlAddress;
    private boolean closed;

    public NdiSource(String sourceName, String urlAddress) {
        this.sourceName = Objects.requireNonNull(sourceName, "NdiSource name is required");
        this.urlAddress = urlAddress;
        NDIlib_source_t.p_ndi_name(struct, arena.allocateFrom(sourceName));
        NDIlib_source_t.p_url_address(struct, urlAddress == null ? MemorySegment.NULL : arena.allocateFrom(urlAddress));
    }

    static NdiSource copyOf(MemorySegment source) {
        if (source == null || source.equals(MemorySegment.NULL)) return null;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment boundedSource = source.reinterpret(NDIlib_source_t.sizeof(), arena, null);
            String name = NdiStrings.copy(NDIlib_source_t.p_ndi_name(boundedSource));
            String url = NdiStrings.copy(NDIlib_source_t.p_url_address(boundedSource));
            return new NdiSource(name == null ? "" : name, url);
        }
    }

    public synchronized String getSourceName() {
        ensureOpen();
        return sourceName;
    }

    public synchronized String getUrlAddress() {
        ensureOpen();
        return urlAddress;
    }

    synchronized MemorySegment struct() {
        ensureOpen();
        return struct;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("NdiSource is closed");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        arena.close();
    }
}