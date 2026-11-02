package org.praisenter.ndibridge;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrameTest {
    @Test
    void videoFramePreservesDefaultsAndDirectBufferBaseAddress() {
        try (NdiVideoFrame frame = new NdiVideoFrame()) {
            assertEquals(30000, frame.getFrameRateN());
            assertEquals(1001, frame.getFrameRateD());
            assertEquals(NdiFrameFourCCType.UYVY, frame.getFourCCType());
            frame.setResolution(2, 2);
            ByteBuffer source = ByteBuffer.allocateDirect(8);
            source.position(3);
            frame.setData(source);
            ByteBuffer data = frame.getData();
            assertEquals(8, data.capacity());
            assertEquals(0, data.position());
            data.put(0, (byte) 42);
            assertEquals(42, source.get(0));
        }
    }

    @Test
    void emptyVideoFrameHasNoData() {
        try (NdiVideoFrame frame = new NdiVideoFrame()) {
            assertNull(frame.getData());
        }
    }

    @Test
    void frameRejectsHeapAndUndersizedBuffers() {
        try (NdiVideoFrame video = new NdiVideoFrame(); NdiAudioFrame audio = new NdiAudioFrame()) {
            video.setResolution(2, 2);
            assertThrows(IllegalArgumentException.class, () -> video.setData(ByteBuffer.allocate(16)));
            assertThrows(IllegalArgumentException.class, () -> video.setData(ByteBuffer.allocateDirect(7)));
            audio.setSamples(2);
            assertThrows(IllegalArgumentException.class, () -> audio.setData(ByteBuffer.allocateDirect(15)));
        }
    }

    @Test
    void metadataRoundTripsUtf8AndPreservesTimecode() {
        try (NdiMetadataFrame frame = new NdiMetadataFrame()) {
            frame.setData("<text>Worship \u00e9</text>");
            frame.setTimecode(123456789L);
            assertEquals("<text>Worship \u00e9</text>", frame.getData());
            assertEquals(123456789L, frame.getTimecode());
            frame.setData(null);
            assertNull(frame.getData());
        }
    }

    @Test
    void interleavedAudioUsesSampleWidthForCapacityChecks() {
        try (NdiAudioFrameInterleaved16s frame = new NdiAudioFrameInterleaved16s();
             NdiAudioFrameInterleaved32f floatFrame = new NdiAudioFrameInterleaved32f()) {
            frame.setSamples(4);
            frame.setData(ByteBuffer.allocateDirect(16));
            assertEquals(16, frame.getData().capacity());
            floatFrame.setSamples(4);
            assertThrows(IllegalArgumentException.class, () -> floatFrame.setData(ByteBuffer.allocateDirect(31)));
            try (NdiAudioFrameInterleaved32s integerFrame = new NdiAudioFrameInterleaved32s()) {
                assertEquals(0, integerFrame.getReferenceLevel());
            }
        }
    }

    @Test
    void closedFramesRejectFurtherAccess() {
        NdiVideoFrame frame = new NdiVideoFrame();
        frame.close();
        frame.close();
        assertThrows(IllegalStateException.class, frame::getXResolution);
    }

    @Test
    void asyncLeaseRetainsStorageButAllowsFrameReuse() {
        try (NdiVideoFrame frame = new NdiVideoFrame()) {
            frame.setResolution(2, 2);
            frame.setData(ByteBuffer.allocateDirect(16));
            Object sender = new Object();
            frame.acquireAsyncLease(sender);
            assertThrows(IllegalStateException.class, frame::close);
            frame.setData(ByteBuffer.allocateDirect(16));
            assertNotNull(frame.getData());
            frame.releaseAsyncLease(sender);
        }
    }
}