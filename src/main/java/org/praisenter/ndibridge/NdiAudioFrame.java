package org.praisenter.ndibridge;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import org.praisenter.ndibridge.internal.generated.NDIlib_audio_frame_v2_t;

/** Planar floating-point audio frame. */
public final class NdiAudioFrame extends NdiAbstractFrame {
    public NdiAudioFrame() {
        super(NDIlib_audio_frame_v2_t::allocate);
        NDIlib_audio_frame_v2_t.sample_rate(struct(), 48000);
        NDIlib_audio_frame_v2_t.no_channels(struct(), 2);
        NDIlib_audio_frame_v2_t.no_samples(struct(), 0);
        NDIlib_audio_frame_v2_t.timecode(struct(), Long.MAX_VALUE);
        NDIlib_audio_frame_v2_t.p_data(struct(), MemorySegment.NULL);
        NDIlib_audio_frame_v2_t.channel_stride_in_bytes(struct(), 0);
        NDIlib_audio_frame_v2_t.p_metadata(struct(), MemorySegment.NULL);
        NDIlib_audio_frame_v2_t.timestamp(struct(), 0);
    }

    public synchronized void setSampleRate(int sampleRate) { ensureMutable(); if(sampleRate <= 0) throw new IllegalArgumentException("Sample rate must be positive"); NDIlib_audio_frame_v2_t.sample_rate(struct(), sampleRate); }
    public synchronized int getSampleRate() { ensureOpen(); return NDIlib_audio_frame_v2_t.sample_rate(struct()); }
    public synchronized void setChannels(int channels) { ensureMutable(); if(channels < 0) throw new IllegalArgumentException("Channel count must not be negative"); NDIlib_audio_frame_v2_t.no_channels(struct(), channels); }
    public synchronized int getChannels() { ensureOpen(); return NDIlib_audio_frame_v2_t.no_channels(struct()); }
    public synchronized void setSamples(int samples) { ensureMutable(); if(samples < 0) throw new IllegalArgumentException("Sample count must not be negative"); NDIlib_audio_frame_v2_t.no_samples(struct(), samples); }
    public synchronized int getSamples() { ensureOpen(); return NDIlib_audio_frame_v2_t.no_samples(struct()); }
    public synchronized void setTimecode(long timecode) { ensureMutable(); NDIlib_audio_frame_v2_t.timecode(struct(), timecode); }
    public synchronized long getTimecode() { ensureOpen(); return NDIlib_audio_frame_v2_t.timecode(struct()); }
    public synchronized void setChannelStride(int channelStride) { ensureMutable(); if(channelStride < 0) throw new IllegalArgumentException("Channel stride must not be negative"); NDIlib_audio_frame_v2_t.channel_stride_in_bytes(struct(), channelStride); }
    public synchronized int getChannelStride() { ensureOpen(); return NDIlib_audio_frame_v2_t.channel_stride_in_bytes(struct()); }
    public synchronized void setMetadata(String metadata) { ensureMutable(); NDIlib_audio_frame_v2_t.p_metadata(struct(), allocateUtf8(metadata)); }
    public synchronized String getMetadata() { ensureOpen(); return readUtf8(NDIlib_audio_frame_v2_t.p_metadata(struct())); }
    public synchronized void setTimestamp(long timestamp) { ensureMutable(); NDIlib_audio_frame_v2_t.timestamp(struct(), timestamp); }
    public synchronized long getTimestamp() { ensureOpen(); return NDIlib_audio_frame_v2_t.timestamp(struct()); }

    public synchronized void setData(ByteBuffer data) { setCallerData(data, requiredDataBytes()); }

    public synchronized ByteBuffer getData() {
        MemorySegment pointer = NDIlib_audio_frame_v2_t.p_data(struct());
        return pointer.equals(MemorySegment.NULL) ? null : dataBuffer(pointer, requiredDataBytes());
    }

    private long requiredDataBytes() {
        int channels = NDIlib_audio_frame_v2_t.no_channels(struct());
        int samples = NDIlib_audio_frame_v2_t.no_samples(struct());
        int stride = NDIlib_audio_frame_v2_t.channel_stride_in_bytes(struct());
        if (channels < 0 || samples < 0 || stride < 0) throw new IllegalStateException("Invalid audio frame dimensions");
        if (channels == 0 || samples == 0) return 0;
        long channelBytes = Math.multiplyExact((long) samples, Float.BYTES);
        long channelStride = stride == 0 ? channelBytes : stride;
        if (channelStride < channelBytes) throw new IllegalStateException("Channel stride is smaller than the audio samples");
        return Math.addExact(Math.multiplyExact((long) channels - 1, channelStride), channelBytes);
    }

    @Override
    protected synchronized void setDataPointer(MemorySegment pointer) {
        NDIlib_audio_frame_v2_t.p_data(struct(), pointer);
    }
}