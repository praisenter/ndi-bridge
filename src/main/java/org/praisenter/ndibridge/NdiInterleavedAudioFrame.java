package org.praisenter.ndibridge;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.nio.ByteBuffer;
import java.util.function.Function;

abstract class NdiInterleavedAudioFrame extends NdiAbstractFrame {
    private final int bytesPerSample;

    NdiInterleavedAudioFrame(Function<SegmentAllocator, MemorySegment> allocator, int bytesPerSample) {
        super(allocator);
        this.bytesPerSample = bytesPerSample;
    }

    protected abstract int sampleRateValue();
    protected abstract void sampleRateValue(int value);
    protected abstract int channelsValue();
    protected abstract void channelsValue(int value);
    protected abstract int samplesValue();
    protected abstract void samplesValue(int value);
    protected abstract long timecodeValue();
    protected abstract void timecodeValue(long value);

    public final synchronized void setSampleRate(int sampleRate) {
        ensureMutable();
        if (sampleRate <= 0) throw new IllegalArgumentException("Sample rate must be positive");
        sampleRateValue(sampleRate);
    }

    public final synchronized int getSampleRate() { ensureOpen(); return sampleRateValue(); }
    public final synchronized void setChannels(int channels) { ensureMutable(); if(channels < 0) throw new IllegalArgumentException("Channel count must not be negative"); channelsValue(channels); }
    public final synchronized int getChannels() { ensureOpen(); return channelsValue(); }
    public final synchronized void setSamples(int samples) { ensureMutable(); if(samples < 0) throw new IllegalArgumentException("Sample count must not be negative"); samplesValue(samples); }
    public final synchronized int getSamples() { ensureOpen(); return samplesValue(); }
    public final synchronized void setTimecode(long timecode) { ensureMutable(); timecodeValue(timecode); }
    public final synchronized long getTimecode() { ensureOpen(); return timecodeValue(); }

    public final synchronized void setData(ByteBuffer data) {
        setCallerData(data, requiredDataBytes());
    }

    public final synchronized ByteBuffer getData() {
        MemorySegment pointer = dataPointer();
        return pointer.equals(MemorySegment.NULL) ? null : dataBuffer(pointer, requiredDataBytes());
    }

    protected abstract MemorySegment dataPointer();

    private long requiredDataBytes() {
        int channels = channelsValue();
        int samples = samplesValue();
        if (channels < 0 || samples < 0) throw new IllegalStateException("Invalid audio frame dimensions");
        return Math.multiplyExact(Math.multiplyExact((long) channels, samples), bytesPerSample);
    }
}