package dev.barcodeworkbench.zint;

/**
 * Thin static binding to libzint. Callers should prefer a higher-level encoder
 * that resolves options from the symbology registry rather than calling this
 * directly.
 */
public final class ZintNative {

    private static final String LIBRARY_NAME = "barcode_zint";

    private static volatile boolean loaded;
    private static volatile UnsatisfiedLinkError loadFailure;

    private ZintNative() {
    }

    /**
     * Loads the native library once. Safe to call repeatedly.
     *
     * @throws UnsatisfiedLinkError if the library is unavailable for this ABI
     */
    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        if (loadFailure != null) {
            throw loadFailure;
        }
        try {
            System.loadLibrary(LIBRARY_NAME);
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            loadFailure = e;
            throw e;
        }
    }

    public static boolean isAvailable() {
        try {
            ensureLoaded();
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /** libzint version as an integer, e.g. 21600 for 2.16.0. */
    public static int version() {
        ensureLoaded();
        return nativeVersion();
    }

    /** Queries a {@code ZINT_CAP_*} flag set for a symbology. */
    public static int capabilities(int symbology, int capFlags) {
        ensureLoaded();
        return nativeCap(symbology, capFlags);
    }

    public static boolean hasCapability(int symbology, int capFlag) {
        return (capabilities(symbology, capFlag) & capFlag) != 0;
    }

    public static boolean isValidSymbology(int symbology) {
        ensureLoaded();
        return nativeValidId(symbology);
    }

    /** libzint's canonical name for a symbology, or null if unknown. */
    public static String barcodeName(int symbology) {
        ensureLoaded();
        return nativeBarcodeName(symbology);
    }

    /**
     * Encodes a payload.
     *
     * @param symbology     a {@code BARCODE_*} id
     * @param data          raw payload bytes; length is passed explicitly so
     *                      embedded NUL bytes survive
     * @param inputMode     a base mode OR'd with flags, see {@link ZintConstants}
     * @param eci           ECI code, or 0 for none/automatic
     * @param option1       symbology-specific (commonly error-correction level)
     * @param option2       symbology-specific (commonly symbol size/version)
     * @param option3       symbology-specific
     * @param outputOptions {@code BARCODE_*} output flags
     * @param height        overall height in X-dimensions, or 0 for the default
     * @param warnLevel     0 default, or WARN_FAIL_ALL to promote warnings to errors
     * @return a result that is never null; inspect {@link ZintResult#isSuccess()}
     */
    public static ZintResult encode(int symbology,
                                    byte[] data,
                                    int inputMode,
                                    int eci,
                                    int option1,
                                    int option2,
                                    int option3,
                                    int outputOptions,
                                    float height,
                                    int warnLevel) {
        ensureLoaded();
        return nativeEncode(symbology, data, inputMode, eci,
                option1, option2, option3, outputOptions, height, warnLevel);
    }

    /** Convenience overload using zint defaults. */
    public static ZintResult encode(int symbology, byte[] data, int inputMode) {
        return encode(symbology, data, inputMode, 0, -1, -1, -1, 0, 0f, 0);
    }

    private static native int nativeVersion();

    private static native int nativeCap(int symbology, int capFlags);

    private static native boolean nativeValidId(int symbology);

    private static native String nativeBarcodeName(int symbology);

    private static native ZintResult nativeEncode(int symbology,
                                                  byte[] data,
                                                  int inputMode,
                                                  int eci,
                                                  int option1,
                                                  int option2,
                                                  int option3,
                                                  int outputOptions,
                                                  float height,
                                                  int warnLevel);
}
