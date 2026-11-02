package org.praisenter.ndibridge;

import java.lang.foreign.MemorySegment;
import java.util.function.Function;
import org.praisenter.ndibridge.internal.NdiRuntime;
import org.praisenter.ndibridge.internal.generated.NDIlib_v6_3;

/** Audio format conversions provided by the NDI runtime. */
public final class NdiUtilities {
    private NdiUtilities() {
    }

    public static void planarFloatToInterleaved16s(NdiAudioFrame source, NdiAudioFrameInterleaved16s target) {
        convert(source, target, "util_audio_to_interleaved_16s_v2",
            NDIlib_v6_3::util_audio_to_interleaved_16s_v2, NDIlib_v6_3.util_audio_to_interleaved_16s_v2::invoke);
    }

    public static void interleaved16sToPlanarFloat(NdiAudioFrameInterleaved16s source, NdiAudioFrame target) {
        convert(source, target, "util_audio_from_interleaved_16s_v2",
            NDIlib_v6_3::util_audio_from_interleaved_16s_v2, NDIlib_v6_3.util_audio_from_interleaved_16s_v2::invoke);
    }

    public static void planarFloatToInterleaved32s(NdiAudioFrame source, NdiAudioFrameInterleaved32s target) {
        convert(source, target, "util_audio_to_interleaved_32s_v2",
            NDIlib_v6_3::util_audio_to_interleaved_32s_v2, NDIlib_v6_3.util_audio_to_interleaved_32s_v2::invoke);
    }

    public static void interleaved32sToPlanarFloat(NdiAudioFrameInterleaved32s source, NdiAudioFrame target) {
        convert(source, target, "util_audio_from_interleaved_32s_v2",
            NDIlib_v6_3::util_audio_from_interleaved_32s_v2, NDIlib_v6_3.util_audio_from_interleaved_32s_v2::invoke);
    }

    public static void planarFloatToInterleavedFloat(NdiAudioFrame source, NdiAudioFrameInterleaved32f target) {
        convert(source, target, "util_audio_to_interleaved_32f_v2",
            NDIlib_v6_3::util_audio_to_interleaved_32f_v2, NDIlib_v6_3.util_audio_to_interleaved_32f_v2::invoke);
    }

    public static void interleavedFloatToPlanarFloat(NdiAudioFrameInterleaved32f source, NdiAudioFrame target) {
        convert(source, target, "util_audio_from_interleaved_32f_v2",
            NDIlib_v6_3::util_audio_from_interleaved_32f_v2, NDIlib_v6_3.util_audio_from_interleaved_32f_v2::invoke);
    }

    private static void convert(NdiAbstractFrame source, NdiAbstractFrame target, String name,
                                Function<MemorySegment, MemorySegment> lookup, AudioConversion conversion) {
        if (source == null || target == null) throw new NullPointerException("Audio frames are required");
        NdiRuntime runtime = NdiBridge.runtime();
        MemorySegment pointer = NdiRuntime.requireFunction(lookup.apply(runtime.table()), name);
        conversion.invoke(pointer, source.struct(), target.struct());
    }

    @FunctionalInterface
    private interface AudioConversion {
        void invoke(MemorySegment function, MemorySegment source, MemorySegment target);
    }
}