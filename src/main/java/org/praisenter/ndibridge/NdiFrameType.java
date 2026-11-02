package org.praisenter.ndibridge;

/** Type of frame returned by an NDI capture operation. */
public enum NdiFrameType {
    NONE(0), VIDEO(1), AUDIO(2), METADATA(3), ERROR(4), STATUS_CHANGE(100), SOURCE_CHANGE(101);

    final int id;

    NdiFrameType(int id) {
        this.id = id;
    }

    public static NdiFrameType valueOf(int id) {
        for (NdiFrameType type : values()) {
            if (type.id == id) return type;
        }
        throw new IllegalArgumentException("Unknown frame type id: " + id);
    }
}