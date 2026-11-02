package org.praisenter.ndibridge;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SenderColorManualTest {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int FRAMES_PER_SECOND = 30;

    @Test
    @EnabledIfSystemProperty(named = "ndibridgeManualSender", matches = "true")
    void sendsChangingColorUntilEnterIsPressed() throws Exception {
        String runtimePath = System.getProperty("ndibridge.runtime");
        if (runtimePath == null || runtimePath.isBlank()) {
            throw new IllegalStateException("Set -Dndibridge.runtime to the absolute NDI runtime file path");
        }

        int loadResult = NdiBridge.loadLibraries(Path.of(runtimePath));
        assertEquals(0, loadResult, () -> NdiBridge.getLastLoadError().orElse("NDI runtime loading failed"));

        AtomicBoolean stopRequested = new AtomicBoolean();
        AtomicReference<IOException> inputFailure = new AtomicReference<>();
        ByteBuffer[] pixelBuffers = {
            ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4),
            ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4)
        };
        IntBuffer[] pixelWords = {
            pixelBuffers[0].order(ByteOrder.nativeOrder()).asIntBuffer(),
            pixelBuffers[1].order(ByteOrder.nativeOrder()).asIntBuffer()
        };
        long frameInterval = 1_000_000_000L / FRAMES_PER_SECOND;

        try (NdiVideoFrame frame = new NdiVideoFrame(); NdiSender sender = new NdiSender("NDI Bridge Color Test")) {
            frame.setResolution(WIDTH, HEIGHT);
            frame.setFourCCType(NdiFrameFourCCType.BGRA);
            frame.setFrameRate(FRAMES_PER_SECOND, 1);
            frame.setLineStride(WIDTH * 4);

            System.out.println("Sending NDI Bridge Color Test at 1920x1080. Connect with NDI Tools; press Enter to stop.");
            Thread inputThread = new Thread(() -> {
                try {
                    System.in.read();
                } catch (IOException failure) {
                    inputFailure.set(failure);
                } finally {
                    stopRequested.set(true);
                }
            }, "ndi-bridge-sender-test-input");
            inputThread.setDaemon(true);
            inputThread.start();

            int frameNumber = 0;
            long nextFrameTime = System.nanoTime();
            while (!stopRequested.get()) {
                int bufferIndex = frameNumber & 1;
                fillColor(pixelWords[bufferIndex], frameNumber++);
                frame.setData(pixelBuffers[bufferIndex]);
                sender.sendVideoFrameAsync(frame);

                nextFrameTime += frameInterval;
                long remaining = nextFrameTime - System.nanoTime();
                if (remaining > 0) LockSupport.parkNanos(remaining);
                else nextFrameTime = System.nanoTime();
            }
        }

        if (inputFailure.get() != null) throw inputFailure.get();
    }

    private static void fillColor(IntBuffer pixels, int frameNumber) {
        int phase = frameNumber % 768;
        int red;
        int green;
        int blue;
        if (phase < 256) {
            red = 255 - phase;
            green = phase;
            blue = 0;
        } else if (phase < 512) {
            red = 0;
            green = 511 - phase;
            blue = phase - 256;
        } else {
            red = phase - 512;
            green = 0;
            blue = 767 - phase;
        }

        int bgra = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                ? 0xff000000 | red << 16 | green << 8 | blue
                : blue << 24 | green << 16 | red << 8 | 0xff;
        for (int index = 0; index < WIDTH * HEIGHT; index++) pixels.put(index, bgra);
    }
}