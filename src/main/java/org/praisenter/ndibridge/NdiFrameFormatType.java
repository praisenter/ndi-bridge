package org.praisenter.ndibridge;

/** Scan format of a video frame. */
public enum NdiFrameFormatType {
    INTERLEAVED(0), PROGRESSIVE(1), FIELD_0(2), FIELD_1(3);

    final int id;

    NdiFrameFormatType(int id) {
        this.id = id;
    }

    public static NdiFrameFormatType valueOf(int id) {
        for (NdiFrameFormatType type : values()) {
            if (type.id == id) return type;
        }
        throw new IllegalArgumentException("Unknown frame format id: " + id);
    }
}