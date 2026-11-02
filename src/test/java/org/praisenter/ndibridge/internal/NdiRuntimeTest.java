package org.praisenter.ndibridge.internal;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.praisenter.ndibridge.internal.generated.NDIlib_v6_3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class NdiRuntimeTest {
    @TempDir Path directory;

    static final class Fixture implements AutoCloseable {
        final Arena arena = Arena.ofShared();
        final MemorySegment table = arena.allocate(NDIlib_v6_3.layout());
        final MemorySegment version = arena.allocateFrom("fixture-runtime-\u00e9");
        boolean initialized = true;
        int destroys;

        Fixture() throws Exception {
            NDIlib_v6_3.initialize(table, stub("initialize", boolean.class));
            NDIlib_v6_3.destroy(table, stub("destroy", void.class));
            NDIlib_v6_3.version(table, stub("version", MemorySegment.class));
            NDIlib_v6_3.is_supported_CPU(table, stub("cpu", boolean.class));
        }

        MemorySegment stub(String name, Class<?> result) throws Exception {
            var handle = MethodHandles.lookup().findVirtual(Fixture.class, name, MethodType.methodType(result)).bindTo(this);
            var descriptor = result == void.class ? FunctionDescriptor.ofVoid()
                    : FunctionDescriptor.of(result == boolean.class ? ValueLayout.JAVA_BOOLEAN : ValueLayout.ADDRESS);
            return Linker.nativeLinker().upcallStub(handle, descriptor, arena);
        }

        SymbolLookup lookup() {
            try {
                MemorySegment address = stub("load", MemorySegment.class);
                return name -> name.equals("NDIlib_v6_3_load") ? Optional.of(address) : Optional.empty();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        public MemorySegment load() { return table; }
        public boolean initialize() { return initialized; }
        public void destroy() { destroys++; }
        public MemorySegment version() { return version; }
        public boolean cpu() { return true; }
        public void close() { if (arena.scope().isAlive()) arena.close(); }
    }

    @Test
    void executesRealFfmDowncallsWithoutTheSdk() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path file = Files.createFile(directory.resolve("runtime.dll"));
            NdiRuntime runtime = new NdiRuntime();
            assertEquals(0, runtime.load(file, (path, arena) -> fixture.lookup()));
            assertEquals("fixture-runtime-\u00e9", runtime.version());
            assertTrue(runtime.isSupportedCpu());
            runtime.close();
            runtime.close();
            assertEquals(1, fixture.destroys);
            assertThrows(IllegalStateException.class, runtime::version);
        }
    }

    @Test
    void rejectsAbsentExport() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path file = Files.createFile(directory.resolve("missing-export.dll"));
            NdiRuntime runtime = new NdiRuntime();
            assertEquals(-2, runtime.load(file, (path, arena) -> name -> Optional.empty()));
            assertFalse(runtime.isLoaded());
            assertTrue(runtime.lastError().orElseThrow().contains("NDIlib_v6_3_load"));
            assertEquals(0, fixture.destroys);
        }
    }

    @Test
    void checksAllRequiredPointersBeforeInitializing() throws Exception {
        try (Fixture fixture = new Fixture()) {
            NDIlib_v6_3.version(fixture.table, MemorySegment.NULL);
            Path file = Files.createFile(directory.resolve("missing-table-entry.dll"));
            NdiRuntime runtime = new NdiRuntime();
            assertEquals(-2, runtime.load(file, (path, arena) -> fixture.lookup()));
            assertFalse(runtime.isLoaded());
            assertEquals(0, fixture.destroys);
        }
    }

    @Test
    void rejectsInitializationFailure() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.initialized = false;
            Path file = Files.createFile(directory.resolve("initialization-failure.dll"));
            NdiRuntime runtime = new NdiRuntime();
            assertEquals(-2, runtime.load(file, (path, arena) -> fixture.lookup()));
            assertFalse(runtime.isLoaded());
            assertEquals(0, fixture.destroys);
        }
    }

    @Test
    void validatesExplicitPathAndReportsMissingRuntime() {
        NdiRuntime runtime = new NdiRuntime();
        assertThrows(NullPointerException.class, () -> runtime.load(null));
        assertThrows(IllegalArgumentException.class, () -> runtime.load(Path.of("ndi.dll")));
        assertThrows(IllegalArgumentException.class, () -> runtime.load(directory));
        Path missing = directory.resolve("not-installed.dll");
        assertEquals(-1, runtime.load(missing));
        assertTrue(runtime.lastError().orElseThrow().contains(missing.toString()));
        assertFalse(runtime.isLoaded());
        assertThrows(IllegalStateException.class, runtime::version);
    }

    @Test
    void loadsCanonicalPathOnceAndRejectsSwitching() throws Exception {
        try (Fixture fixture = new Fixture()) {
            NdiRuntime runtime = new NdiRuntime();
            Path file = Files.createFile(directory.resolve("runtime with spaces \u00e9.dll"));
            AtomicInteger opens = new AtomicInteger();
            var lookupFactory = (java.util.function.BiFunction<Path, Arena, SymbolLookup>) (path, arena) -> {
                opens.incrementAndGet();
                return fixture.lookup();
            };

            assertEquals(0, runtime.load(file, lookupFactory));
            assertEquals(0, runtime.load(file.getParent().resolve(".").resolve(file.getFileName()), lookupFactory));
            assertEquals(1, opens.get());
            assertEquals("fixture-runtime-\u00e9", runtime.version());
            assertTrue(runtime.lastError().isEmpty());

            Path other = Files.createFile(directory.resolve("other.dll"));
            assertThrows(IllegalStateException.class, () -> runtime.load(other, lookupFactory));
            runtime.close();
        }
    }

    @Test
    void retriesAfterInitializationFailure() throws Exception {
        try (Fixture fixture = new Fixture()) {
            NdiRuntime runtime = new NdiRuntime();
            Path file = Files.createFile(directory.resolve("retry.dll"));
            fixture.initialized = false;
            assertEquals(-2, runtime.load(file, (path, arena) -> fixture.lookup()));
            assertTrue(runtime.lastError().orElseThrow().contains("initialization returned false"));

            fixture.initialized = true;
            assertEquals(0, runtime.load(file, (path, arena) -> fixture.lookup()));
            assertTrue(runtime.lastError().isEmpty());
            runtime.close();
        }
    }

    @Test
    void serializesConcurrentInitialization() throws Exception {
        try (Fixture fixture = new Fixture()) {
            NdiRuntime runtime = new NdiRuntime();
            Path file = Files.createFile(directory.resolve("concurrent.dll"));
            AtomicInteger opens = new AtomicInteger();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var tasks = new ArrayList<Callable<Integer>>();
                for (int index = 0; index < 32; index++) {
                    tasks.add(() -> runtime.load(file, (path, arena) -> {
                        opens.incrementAndGet();
                        return fixture.lookup();
                    }));
                }
                for (var result : executor.invokeAll(tasks)) assertEquals(0, result.get());
            }
            assertEquals(1, opens.get());
            runtime.close();
        }
    }

    @Test
    void generatedBindingsDoNotAssumeCOnlyLongWidth() throws Exception {
        Class<?> support = Class.forName("org.praisenter.ndibridge.internal.generated.NdiDynamicLoad$shared");
        assertThrows(NoSuchFieldException.class, () -> support.getDeclaredField("C_LONG"));
    }

}