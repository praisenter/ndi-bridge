package org.praisenter.ndibridge;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import org.praisenter.ndibridge.internal.generated.NDIlib_metadata_frame_t;

/** UTF-8 metadata frame. */
public final class NdiMetadataFrame extends NdiAbstractFrame {
    public NdiMetadataFrame() {
        super(NDIlib_metadata_frame_t::allocate);
        NDIlib_metadata_frame_t.length(struct(), 0);
        NDIlib_metadata_frame_t.timecode(struct(), Long.MAX_VALUE);
        NDIlib_metadata_frame_t.p_data(struct(), MemorySegment.NULL);
    }

    public synchronized String getData() {
        return readUtf8(NDIlib_metadata_frame_t.p_data(struct()));
    }

    public synchronized void setData(String data) {
        ensureMutable();
        MemorySegment encoded = allocateUtf8(data);
        NDIlib_metadata_frame_t.p_data(struct(), encoded);
        if (data == null) {
            NDIlib_metadata_frame_t.length(struct(), 0);
        } else {
            long length = data.getBytes(StandardCharsets.UTF_8).length + 1L;
            if (length > Integer.MAX_VALUE) throw new IllegalArgumentException("Metadata frame is too large");
            NDIlib_metadata_frame_t.length(struct(), (int) length);
        }
    }

    public synchronized long getTimecode() { ensureOpen(); return NDIlib_metadata_frame_t.timecode(struct()); }
    public synchronized void setTimecode(long timecode) { ensureMutable(); NDIlib_metadata_frame_t.timecode(struct(), timecode); }

    @Override
    protected synchronized void setDataPointer(MemorySegment pointer) {
        NDIlib_metadata_frame_t.p_data(struct(), pointer);
    }
}