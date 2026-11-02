package org.praisenter.ndibridge.internal;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.function.BiFunction;
import org.praisenter.ndibridge.internal.generated.NDIlib_v6_3;

public final class NdiRuntime {
    private Arena arena;
    private MemorySegment table;
    private Path loadedPath;
    private String lastError;
    private boolean closed;

    public synchronized int load(Path requestedPath) {
        return load(requestedPath, SymbolLookup::libraryLookup);
    }

    synchronized int load(Path requestedPath, BiFunction<Path, Arena, SymbolLookup> lookupFactory) {
        if (closed) throw new IllegalStateException("NDI runtime manager is closed");
        if (requestedPath == null) throw new NullPointerException("An explicit NDI runtime file path is required");
        if (!requestedPath.isAbsolute()) {
            throw new IllegalArgumentException("The NDI runtime path must be absolute: " + requestedPath);
        }

        Path realPath;
        try {
            realPath = requestedPath.toRealPath();
            BasicFileAttributes attributes = Files.readAttributes(realPath, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                throw new IllegalArgumentException("The NDI runtime path must name a regular file: " + requestedPath);
            }
        } catch (NoSuchFileException failure) {
            return fail(-1, requestedPath, failure);
        } catch (IOException | SecurityException failure) {
            return fail(-2, requestedPath, failure);
        }

        if (table != null) {
            if (!loadedPath.equals(realPath)) {
                throw new IllegalStateException("NDI is already initialized from " + loadedPath
                        + "; cannot switch to " + realPath);
            }
            lastError = null;
            return 0;
        }

        if (!NdiRuntime.class.getModule().isNativeAccessEnabled()) {
            String module = NdiRuntime.class.getModule().isNamed()
                    ? NdiRuntime.class.getModule().getName() : "ALL-UNNAMED";
            return fail(-2, realPath, new IllegalCallerException("Enable FFM access with --enable-native-access=" + module));
        }

        Arena newArena = Arena.ofShared();
        try {
            SymbolLookup symbols = lookupFactory.apply(realPath, newArena);
            table = bind(newArena, symbols);
            arena = newArena;
            loadedPath = realPath;
            lastError = null;
            return 0;
        } catch (RuntimeException | LinkageError failure) {
            newArena.close();
            return fail(-2, realPath, failure);
        }
    }

    private int fail(int result, Path path, Throwable failure) {
        lastError = "Cannot initialize NDI from " + path + " ("
                + System.getProperty("os.name") + "/" + System.getProperty("os.arch") + "): "
                + failure.getClass().getSimpleName() + ": " + failure.getMessage();
        return result;
    }

    public synchronized boolean isLoaded() {
        return table != null && !closed;
    }

    public synchronized Optional<String> lastError() {
        return Optional.ofNullable(lastError);
    }

    private static MemorySegment bind(Arena arena, SymbolLookup symbols) {
        Linker linker = Linker.nativeLinker();
        // The generated top-level wrapper uses global lookup, not the caller-selected library.
        MemorySegment loadAddress = symbols.find("NDIlib_v6_3_load")
            .orElseThrow(() -> new UnsatisfiedLinkError("Required export NDIlib_v6_3_load is absent"));
        MethodHandle load = linker.downcallHandle(loadAddress, FunctionDescriptor.of(ValueLayout.ADDRESS));
        try {
            MemorySegment tableAddress = (MemorySegment) load.invokeExact();
            if (tableAddress.equals(MemorySegment.NULL)) {
                throw new UnsatisfiedLinkError("NDIlib_v6_3_load returned a null function table");
            }
            MemorySegment table = tableAddress.reinterpret(NDIlib_v6_3.layout().byteSize(), arena, null);
            MemorySegment initialize = requireFunction(NDIlib_v6_3.initialize(table), "initialize");
            requireFunction(NDIlib_v6_3.destroy(table), "destroy");
            requireFunction(NDIlib_v6_3.version(table), "version");
            requireFunction(NDIlib_v6_3.is_supported_CPU(table), "is_supported_CPU");
            if (!NDIlib_v6_3.initialize.invoke(initialize)) {
                throw new IllegalStateException("NDI initialization returned false; check CPU and runtime requirements");
            }
            return table;
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot invoke NDI initialization", failure);
        }
    }

    public synchronized MemorySegment table() {
        requireOpen();
        return table;
    }

    public static MemorySegment requireFunction(MemorySegment address, String name) {
        if (address.equals(MemorySegment.NULL)) throw new UnsatisfiedLinkError("Required NDI table entry is null: " + name);
        return address;
    }

    public synchronized String version() {
        requireOpen();
        MemorySegment text = NDIlib_v6_3.version.invoke(NDIlib_v6_3.version(table));
        if (text.equals(MemorySegment.NULL)) throw new IllegalStateException("NDI returned a null version string");
        return text.reinterpret(4096).getString(0);
    }

    public synchronized boolean isSupportedCpu() {
        requireOpen();
        return NDIlib_v6_3.is_supported_CPU.invoke(NDIlib_v6_3.is_supported_CPU(table));
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("NDI runtime is closed");
        if (table == null) {
            throw new IllegalStateException("Call NdiBridge.loadLibraries(absoluteRuntimeFilePath) successfully first");
        }
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (table == null) return;
        try { NDIlib_v6_3.destroy.invoke(NDIlib_v6_3.destroy(table)); }
        finally { arena.close(); }
    }
}