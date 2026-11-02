package org.praisenter.ndibridge;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.praisenter.ndibridge.internal.generated.NDIlib_video_frame_v2_t;

/** Mutable video frame with FFM-managed fields and direct-buffer data. */
public final class NdiVideoFrame extends NdiAbstractFrame {
    private static final long SYNTHESIZE_TIMECODE = Long.MAX_VALUE;

    public NdiVideoFrame() {
        super(NDIlib_video_frame_v2_t::allocate);
        NDIlib_video_frame_v2_t.xres(struct(), 0);
        NDIlib_video_frame_v2_t.yres(struct(), 0);
        NDIlib_video_frame_v2_t.FourCC(struct(), NdiFrameFourCCType.UYVY.id);
        NDIlib_video_frame_v2_t.frame_rate_N(struct(), 30000);
        NDIlib_video_frame_v2_t.frame_rate_D(struct(), 1001);
        NDIlib_video_frame_v2_t.picture_aspect_ratio(struct(), 0f);
        NDIlib_video_frame_v2_t.frame_format_type(struct(), NdiFrameFormatType.PROGRESSIVE.id);
        NDIlib_video_frame_v2_t.timecode(struct(), SYNTHESIZE_TIMECODE);
        NDIlib_video_frame_v2_t.p_data(struct(), MemorySegment.NULL);
        NDIlib_video_frame_v2_t.line_stride_in_bytes(struct(), 0);
        NDIlib_video_frame_v2_t.p_metadata(struct(), MemorySegment.NULL);
        NDIlib_video_frame_v2_t.timestamp(struct(), 0);
    }

    public synchronized void setResolution(int width, int height) {
        ensureMutable();
        if (width < 0 || height < 0) throw new IllegalArgumentException("Resolution must not be negative");
        NDIlib_video_frame_v2_t.xres(struct(), width);
        NDIlib_video_frame_v2_t.yres(struct(), height);
    }

    public synchronized int getXResolution() { ensureOpen(); return NDIlib_video_frame_v2_t.xres(struct()); }
    public synchronized int getYResolution() { ensureOpen(); return NDIlib_video_frame_v2_t.yres(struct()); }

    public synchronized void setFourCCType(NdiFrameFourCCType type) {
        ensureMutable();
        NDIlib_video_frame_v2_t.FourCC(struct(), Objects.requireNonNull(type).id);
    }

    public synchronized NdiFrameFourCCType getFourCCType() {
        ensureOpen();
        return NdiFrameFourCCType.valueOf(NDIlib_video_frame_v2_t.FourCC(struct()));
    }

    public synchronized void setFrameRate(int numerator, int denominator) {
        ensureMutable();
        if (numerator <= 0 || denominator <= 0) throw new IllegalArgumentException("Frame-rate values must be positive");
        NDIlib_video_frame_v2_t.frame_rate_N(struct(), numerator);
        NDIlib_video_frame_v2_t.frame_rate_D(struct(), denominator);
    }

    public synchronized int getFrameRateN() { ensureOpen(); return NDIlib_video_frame_v2_t.frame_rate_N(struct()); }
    public synchronized int getFrameRateD() { ensureOpen(); return NDIlib_video_frame_v2_t.frame_rate_D(struct()); }

    public synchronized void setAspectRatio(float aspectRatio) {
        ensureMutable();
        if (!Float.isFinite(aspectRatio) || aspectRatio < 0) throw new IllegalArgumentException("Aspect ratio must be finite and non-negative");
        NDIlib_video_frame_v2_t.picture_aspect_ratio(struct(), aspectRatio);
    }

    public synchronized float getAspectRatio() { ensureOpen(); return NDIlib_video_frame_v2_t.picture_aspect_ratio(struct()); }

    public synchronized void setFormatType(NdiFrameFormatType type) {
        ensureMutable();
        NDIlib_video_frame_v2_t.frame_format_type(struct(), Objects.requireNonNull(type).id);
    }

    public synchronized NdiFrameFormatType getFormatType() {
        ensureOpen();
        return NdiFrameFormatType.valueOf(NDIlib_video_frame_v2_t.frame_format_type(struct()));
    }

    public synchronized void setTimecode(long timecode) { ensureMutable(); NDIlib_video_frame_v2_t.timecode(struct(), timecode); }
    public synchronized long getTimecode() { ensureOpen(); return NDIlib_video_frame_v2_t.timecode(struct()); }
    public synchronized void setLineStride(int lineStride) { ensureMutable(); if (lineStride < 0) throw new IllegalArgumentException("Line stride must not be negative"); NDIlib_video_frame_v2_t.line_stride_in_bytes(struct(), lineStride); }
    public synchronized int getLineStride() { ensureOpen(); return NDIlib_video_frame_v2_t.line_stride_in_bytes(struct()); }

    public synchronized void setMetadata(String metadata) {
        ensureMutable();
        NDIlib_video_frame_v2_t.p_metadata(struct(), allocateUtf8(metadata));
    }

    public synchronized String getMetadata() { ensureOpen(); return readUtf8(NDIlib_video_frame_v2_t.p_metadata(struct())); }
    public synchronized void setTimestamp(int timestamp) { setTimestamp64(timestamp); }
    public synchronized int getTimestamp() { return (int) getTimestamp64(); }
    public synchronized void setTimestamp64(long timestamp) { ensureMutable(); NDIlib_video_frame_v2_t.timestamp(struct(), timestamp); }
    public synchronized long getTimestamp64() { ensureOpen(); return NDIlib_video_frame_v2_t.timestamp(struct()); }

    public synchronized void setData(ByteBuffer buffer) {
        setCallerData(buffer, requiredDataBytes());
    }

    public synchronized ByteBuffer getData() {
        MemorySegment pointer = NDIlib_video_frame_v2_t.p_data(struct());
        return pointer.equals(MemorySegment.NULL) ? null : dataBuffer(pointer, requiredDataBytes());
    }

    private long requiredDataBytes() {
        int width = NDIlib_video_frame_v2_t.xres(struct());
        int height = NDIlib_video_frame_v2_t.yres(struct());
        int fourCC = NDIlib_video_frame_v2_t.FourCC(struct());
        int stride = NDIlib_video_frame_v2_t.line_stride_in_bytes(struct());
        if (width < 0 || height < 0 || stride < 0) throw new IllegalStateException("Invalid video frame dimensions");
        long rowBytes = stride > 0 ? stride : switch (NdiFrameFourCCType.valueOf(fourCC)) {
            case UYVY -> Math.multiplyExact((long) width, 2);
            case BGRA, BGRX, RGBA, RGBX -> Math.multiplyExact((long) width, 4);
            case UYVA -> Math.multiplyExact((long) width, 2);
            case YV12, NV12, I420 -> width;
            case null -> throw new IllegalStateException("Set a line stride for this video format");
        };
        long bytes = Math.multiplyExact(rowBytes, height);
        NdiFrameFourCCType type = NdiFrameFourCCType.valueOf(fourCC);
        if (type == NdiFrameFourCCType.UYVA || type == NdiFrameFourCCType.YV12
                || type == NdiFrameFourCCType.NV12 || type == NdiFrameFourCCType.I420) {
            bytes = Math.addExact(bytes, (bytes + 1) / 2);
        }
        return bytes;
    }

    @Override
    protected synchronized void setDataPointer(MemorySegment pointer) {
        NDIlib_video_frame_v2_t.p_data(struct(), pointer);
    }
}