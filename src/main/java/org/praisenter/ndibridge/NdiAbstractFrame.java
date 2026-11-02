package org.praisenter.ndibridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Map;
import java.util.function.Function;

abstract class NdiAbstractFrame implements AutoCloseable {
    private static final long MAX_STRING_BYTES = 1L << 20;

    private final Arena structArena = Arena.ofShared();
    private final MemorySegment struct;
    private Arena borrowedArena;
    private ByteBuffer borrowedData;
    private ByteBuffer callerData;
    private NdiFrameBufferOwner bufferOwner;
    private final Map<Object, Integer> asyncLeases = new IdentityHashMap<>();
    private boolean closed;

    NdiAbstractFrame(Function<SegmentAllocator, MemorySegment> allocator) {
        struct = allocator.apply(structArena);
    }

    final MemorySegment struct() {
        ensureOpen();
        return struct;
    }

    protected final synchronized void ensureOpen() {
        if (closed) throw new IllegalStateException("Frame is closed");
    }

    protected final synchronized void ensureMutable() {
        ensureOpen();
        if (!asyncLeases.isEmpty() && bufferOwner != null) {
            throw new IllegalStateException("A received frame cannot be changed while an asynchronous send retains it");
        }
    }

    final synchronized void acquireAsyncLease(Object owner) {
        ensureOpen();
        asyncLeases.merge(Objects.requireNonNull(owner), 1, Integer::sum);
    }

    final synchronized void releaseAsyncLease(Object owner) {
        Integer leases = asyncLeases.get(owner);
        if (leases == null) return;
        if (leases == 1) asyncLeases.remove(owner);
        else asyncLeases.put(owner, leases - 1);
    }

    final synchronized Object asyncDataReference() {
        ensureOpen();
        if (callerData != null) return callerData;
        if (borrowedData != null) return borrowedData;
        return struct;
    }

    final synchronized void prepareForReceive() {
        ensureMutable();
        releaseReceivedBuffer();
        callerData = null;
        setDataPointer(MemorySegment.NULL);
    }

    final synchronized void attachBufferOwner(NdiFrameBufferOwner owner) {
        ensureOpen();
        bufferOwner = Objects.requireNonNull(owner);
        callerData = null;
    }

    protected final synchronized void setCallerData(ByteBuffer data, long requiredBytes) {
        ensureMutable();
        Objects.requireNonNull(data, "Frame data must not be null");
        if (!data.isDirect()) throw new IllegalArgumentException("Frame data must be a direct ByteBuffer");
        if (requiredBytes < 0 || data.capacity() < requiredBytes) {
            throw new IllegalArgumentException("Frame buffer capacity " + data.capacity()
                    + " is smaller than required size " + requiredBytes);
        }
        releaseReceivedBuffer();
        ByteBuffer base = data.duplicate();
        base.clear();
        setDataPointer(MemorySegment.ofBuffer(base));
        callerData = data;
    }

    protected final synchronized ByteBuffer dataBuffer(MemorySegment pointer, long size) {
        ensureOpen();
        if (pointer.equals(MemorySegment.NULL)) return null;
        if (callerData != null) {
            ByteBuffer base = callerData.duplicate();
            base.clear();
            return base;
        }
        if (size < 0 || size > Integer.MAX_VALUE) {
            throw new IllegalStateException("Frame buffer cannot be represented as a ByteBuffer: " + size);
        }
        if (borrowedArena == null) borrowedArena = Arena.ofShared();
        if (borrowedData == null) borrowedData = pointer.reinterpret(size, borrowedArena, null).asByteBuffer();
        return borrowedData.duplicate();
    }

    protected final synchronized String readUtf8(MemorySegment pointer) {
        ensureOpen();
        if (pointer.equals(MemorySegment.NULL)) return null;
        try (Arena temporary = Arena.ofConfined()) {
            return pointer.reinterpret(MAX_STRING_BYTES, temporary, null).getString(0, StandardCharsets.UTF_8);
        }
    }

    protected final synchronized MemorySegment allocateUtf8(String value) {
        ensureMutable();
        return value == null ? MemorySegment.NULL : structArena.allocateFrom(value, StandardCharsets.UTF_8);
    }

    public final synchronized void freeBuffer() {
        ensureOpen();
        if (!asyncLeases.isEmpty()) throw new IllegalStateException("Flush asynchronous sends before freeing frame data");
        releaseReceivedBuffer();
    }

    private void releaseReceivedBuffer() {
        if (borrowedArena != null) {
            borrowedArena.close();
            borrowedArena = null;
            borrowedData = null;
        }
        if (bufferOwner != null) {
            NdiFrameBufferOwner owner = bufferOwner;
            bufferOwner = null;
            try {
                owner.release(this);
            } finally {
                setDataPointer(MemorySegment.NULL);
                callerData = null;
            }
        }
    }

    protected abstract void setDataPointer(MemorySegment pointer);

    @Override
    public final synchronized void close() {
        if (closed) return;
        if (!asyncLeases.isEmpty()) throw new IllegalStateException("Flush the asynchronous sender before closing this frame");
        releaseReceivedBuffer();
        closed = true;
        callerData = null;
        structArena.close();
    }
}