package dev.barcodeworkbench.radamsa;

/**
 * Thin static binding to libradamsa. Prefer the higher-level Mutator, which owns
 * the init-once and serialisation rules; this class only loads the library and
 * forwards two native calls.
 *
 * <p>Java rather than Kotlin so the JNI symbol names are predictable
 * ({@code Java_dev_barcodeworkbench_radamsa_RadamsaNative_nativeMutate}), matching
 * the ZintNative pattern.
 */
public final class RadamsaNative {

    private static final String LIBRARY_NAME = "barcode_radamsa";

    private static volatile boolean loaded;
    private static volatile boolean initialised;
    private static volatile UnsatisfiedLinkError loadFailure;

    private RadamsaNative() {
    }

    /**
     * Loads the library and calls {@code radamsa_init()} exactly once for the
     * process. Safe to call repeatedly; only the first call does anything.
     *
     * <p>init-once is not an optimisation. Each {@code radamsa_init()} reallocates
     * the Owl heap without freeing the previous one, so calling it per mutation
     * leaks unboundedly (measured at ~1.4 MB per call). After a single init the
     * memory footprint is flat across tens of thousands of mutations.
     *
     * @throws UnsatisfiedLinkError if the library is unavailable for this ABI
     */
    public static synchronized void ensureInitialised() {
        if (initialised) {
            return;
        }
        if (loadFailure != null) {
            throw loadFailure;
        }
        try {
            if (!loaded) {
                System.loadLibrary(LIBRARY_NAME);
                loaded = true;
            }
            nativeInit();
            initialised = true;
        } catch (UnsatisfiedLinkError e) {
            loadFailure = e;
            throw e;
        }
    }

    public static boolean isAvailable() {
        try {
            ensureInitialised();
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /**
     * Mutates {@code input}. Not thread-safe: callers must serialise, which the
     * Mutator wrapper does. {@code ensureInitialised()} must have run first.
     *
     * @param input     base bytes
     * @param seed      entropy for this mutation
     * @param maxLength hard cap on the returned length
     * @return the mutated bytes
     */
    public static byte[] mutate(byte[] input, int seed, int maxLength) {
        return nativeMutate(input, seed, maxLength);
    }

    private static native void nativeInit();

    private static native byte[] nativeMutate(byte[] input, int seed, int maxLength);
}
