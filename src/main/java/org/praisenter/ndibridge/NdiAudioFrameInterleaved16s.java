package org.praisenter.ndibridge;

import java.lang.foreign.MemorySegment;
import org.praisenter.ndibridge.internal.generated.NDIlib_audio_frame_interleaved_16s_t;

/** Interleaved 16-bit signed integer audio frame. */
public final class NdiAudioFrameInterleaved16s extends NdiInterleavedAudioFrame {
    public NdiAudioFrameInterleaved16s() {
        super(NDIlib_audio_frame_interleaved_16s_t::allocate, Short.BYTES);
        sampleRateValue(48000);
        channelsValue(2);
        samplesValue(0);
        timecodeValue(Long.MAX_VALUE);
        NDIlib_audio_frame_interleaved_16s_t.reference_level(struct(), 0);
        NDIlib_audio_frame_interleaved_16s_t.p_data(struct(), MemorySegment.NULL);
    }

    public synchronized void setReferenceLevel(int referenceLevel) { ensureMutable(); NDIlib_audio_frame_interleaved_16s_t.reference_level(struct(), referenceLevel); }
    public synchronized long getReferenceLevel() { ensureOpen(); return NDIlib_audio_frame_interleaved_16s_t.reference_level(struct()); }

    @Override protected int sampleRateValue() { return NDIlib_audio_frame_interleaved_16s_t.sample_rate(struct()); }
    @Override protected void sampleRateValue(int value) { NDIlib_audio_frame_interleaved_16s_t.sample_rate(struct(), value); }
    @Override protected int channelsValue() { return NDIlib_audio_frame_interleaved_16s_t.no_channels(struct()); }
    @Override protected void channelsValue(int value) { NDIlib_audio_frame_interleaved_16s_t.no_channels(struct(), value); }
    @Override protected int samplesValue() { return NDIlib_audio_frame_interleaved_16s_t.no_samples(struct()); }
    @Override protected void samplesValue(int value) { NDIlib_audio_frame_interleaved_16s_t.no_samples(struct(), value); }
    @Override protected long timecodeValue() { return NDIlib_audio_frame_interleaved_16s_t.timecode(struct()); }
    @Override protected void timecodeValue(long value) { NDIlib_audio_frame_interleaved_16s_t.timecode(struct(), value); }
    @Override protected MemorySegment dataPointer() { return NDIlib_audio_frame_interleaved_16s_t.p_data(struct()); }
    @Override protected void setDataPointer(MemorySegment pointer) { NDIlib_audio_frame_interleaved_16s_t.p_data(struct(), pointer); }
}