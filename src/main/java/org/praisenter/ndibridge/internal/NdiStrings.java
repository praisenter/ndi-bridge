package org.praisenter.ndibridge.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

public final class NdiStrings {
    private static final long MAX_BYTES = 1L << 20;

    private NdiStrings() {
    }

    public static String copy(MemorySegment pointer) {
        if (pointer == null || pointer.equals(MemorySegment.NULL)) return null;
        try (Arena arena = Arena.ofConfined()) {
            return pointer.reinterpret(MAX_BYTES, arena, null).getString(0, StandardCharsets.UTF_8);
        }
    }
}