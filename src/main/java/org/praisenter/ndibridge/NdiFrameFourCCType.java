package org.praisenter.ndibridge;

/** FourCC pixel format of a video frame. */
public enum NdiFrameFourCCType {
    UYVY('U', 'Y', 'V', 'Y'),
    YV12('Y', 'V', '1', '2'),
    NV12('N', 'V', '1', '2'),
    I420('I', '4', '2', '0'),
    BGRA('B', 'G', 'R', 'A'),
    BGRX('B', 'G', 'R', 'X'),
    RGBA('R', 'G', 'B', 'A'),
    RGBX('R', 'G', 'B', 'X'),
    UYVA('U', 'Y', 'V', 'A');

    public final int id;

    NdiFrameFourCCType(char c1, char c2, char c3, char c4) {
        id = (c1 & 0xff) | ((c2 & 0xff) << 8) | ((c3 & 0xff) << 16) | ((c4 & 0xff) << 24);
    }

    public static NdiFrameFourCCType valueOf(int id) {
        for (NdiFrameFourCCType type : values()) {
            if (type.id == id) return type;
        }
        return null;
    }
}