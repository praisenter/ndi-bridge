package org.praisenter.ndibridge;

import java.nio.file.Path;
import java.util.Optional;
import org.praisenter.ndibridge.internal.NdiRuntime;

/**
 * Explicit initialization of a caller-supplied NDI runtime. Loading this class
 * does not load a native library, search for files, or extract resources.
 */
public final class NdiBridge {
    private static final NdiRuntime RUNTIME = new NdiRuntime();

    private NdiBridge() {
    }

    /**
     * Loads exactly the specified runtime file. No other locations are searched.
     * Successful initialization is permanent for this class loader's lifetime.
     *
     * @param runtimeLibrary absolute path to the runtime file, not a directory
     * @return zero on success, -1 if the file is absent, or -2 if loading fails
     * @throws NullPointerException if the path is null
     * @throws IllegalArgumentException if the path is relative or names a directory
     * @throws IllegalStateException if a different runtime is already initialized
     */
    public static int loadLibraries(Path runtimeLibrary) {
        return RUNTIME.load(runtimeLibrary);
    }

    /**
     * Returns whether the caller-supplied runtime was initialized successfully.
     *
     * @return whether the runtime is loaded
     */
    public static boolean isLoaded() {
        return RUNTIME.isLoaded();
    }

    /**
     * Returns the most recent loading failure, or an empty optional after success.
     *
     * @return the most recent load error, if any
     */
    public static Optional<String> getLastLoadError() {
        return RUNTIME.lastError();
    }

    /**
     * Returns the initialized runtime's version string.
     *
     * @return the runtime version
     * @throws IllegalStateException if initialization has not succeeded
     */
    public static String getNDIVersion() {
        return RUNTIME.version();
    }

    /**
        * Returns whether the initialized runtime supports this CPU.
        *
        * @return whether the current CPU is supported
     * @throws IllegalStateException if initialization has not succeeded
     */
    public static boolean isSupportedCpu() {
        return RUNTIME.isSupportedCpu();
    }

    static NdiRuntime runtime() {
        return RUNTIME;
    }
}