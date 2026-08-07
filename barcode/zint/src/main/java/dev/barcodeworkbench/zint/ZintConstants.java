package dev.barcodeworkbench.zint;

/**
 * Mirrors the subset of {@code zint.h} the app depends on.
 *
 * <p>Values are asserted against the linked library by test, so this cannot
 * silently drift from the native side.
 */
public final class ZintConstants {

    private ZintConstants() {
    }

    // Base input modes (zint.h). Exactly one is OR'd with the flags below.
    public static final int DATA_MODE = 0;      // raw 8-bit binary
    public static final int UNICODE_MODE = 1;   // UTF-8
    public static final int GS1_MODE = 2;       // GS1

    // Input mode flags.
    /** Enables escape processing: hex byte escapes and Unicode codepoint escapes. */
    public static final int ESCAPE_MODE = 0x0008;
    /** Parentheses instead of square brackets as GS1 AI delimiters. */
    public static final int GS1PARENS_MODE = 0x0010;
    /** Skip GS1 validity checking. */
    public static final int GS1NOCHECK_MODE = 0x0020;
    /** Treat height as per-row rather than overall. */
    public static final int HEIGHTPERROW_MODE = 0x0040;
    /** Faster, potentially less optimal encodation. */
    public static final int FAST_MODE = 0x0080;
    /** Enables symbology-specific escapes, notably Code 128 {@code \^A/\^B/\^C} and {@code \^1} FNC1. */
    public static final int EXTRA_ESCAPE_MODE = 0x0100;

    // Output options.
    public static final int BARCODE_BIND = 0x0002;
    public static final int BARCODE_BOX = 0x0004;
    public static final int BARCODE_DOTTY_MODE = 0x0100;
    public static final int BARCODE_QUIET_ZONES = 0x0800;
    public static final int BARCODE_NO_QUIET_ZONES = 0x1000;

    // Return codes.
    public static final int ZINT_WARN_HRT_TRUNCATED = 1;
    public static final int ZINT_WARN_INVALID_OPTION = 2;
    public static final int ZINT_WARN_USES_ECI = 3;
    public static final int ZINT_WARN_NONCOMPLIANT = 4;
    /** Marker: codes at or above this are errors, below are warnings. */
    public static final int ZINT_ERROR = 5;
    public static final int ZINT_ERROR_TOO_LONG = 5;
    public static final int ZINT_ERROR_INVALID_DATA = 6;
    public static final int ZINT_ERROR_INVALID_CHECK = 7;
    public static final int ZINT_ERROR_INVALID_OPTION = 8;
    public static final int ZINT_ERROR_ENCODING_PROBLEM = 9;
    public static final int ZINT_ERROR_MEMORY = 11;

    // Capability flags for ZBarcode_Cap.
    public static final int ZINT_CAP_HRT = 0x0001;
    public static final int ZINT_CAP_STACKABLE = 0x0002;
    public static final int ZINT_CAP_EANUPC = 0x0004;
    public static final int ZINT_CAP_COMPOSITE = 0x0008;
    public static final int ZINT_CAP_ECI = 0x0010;
    public static final int ZINT_CAP_GS1 = 0x0020;
    public static final int ZINT_CAP_DOTTY = 0x0040;
    public static final int ZINT_CAP_QUIET_ZONES = 0x0080;
    public static final int ZINT_CAP_FIXED_RATIO = 0x0100;
    public static final int ZINT_CAP_READER_INIT = 0x0200;
    public static final int ZINT_CAP_FULL_MULTIBYTE = 0x0400;
    public static final int ZINT_CAP_MASK = 0x0800;
    public static final int ZINT_CAP_STRUCTAPP = 0x1000;
    public static final int ZINT_CAP_COMPLIANT_HEIGHT = 0x2000;
    public static final int ZINT_CAP_BINDABLE = 0x4000;

    // Symbology ids used by the launch set. Verified against ZBarcode_BarcodeName
    // by test -- do not edit from memory.
    public static final int BARCODE_CODE11 = 1;
    public static final int BARCODE_CODE39 = 8;
    public static final int BARCODE_EXCODE39 = 9;
    /** Legacy combined EAN-13/8/5/2. Prefer the explicit ids below. */
    public static final int BARCODE_EANX = 13;
    public static final int BARCODE_EAN8 = 10;
    public static final int BARCODE_EAN13 = 15;
    public static final int BARCODE_UPCA = 34;
    public static final int BARCODE_UPCE = 37;
    public static final int BARCODE_CODE128 = 20;
    public static final int BARCODE_GS1_128 = 16;
    public static final int BARCODE_CODABAR = 18;
    public static final int BARCODE_CODE93 = 25;
    public static final int BARCODE_ITF14 = 89;
    public static final int BARCODE_C25INTER = 3;
    public static final int BARCODE_MSI_PLESSEY = 47;
    public static final int BARCODE_TELEPEN = 32;
    public static final int BARCODE_QRCODE = 58;
    public static final int BARCODE_MICROQR = 97;
    public static final int BARCODE_RMQR = 145;
    public static final int BARCODE_DATAMATRIX = 71;
    public static final int BARCODE_AZTEC = 92;
    public static final int BARCODE_PDF417 = 55;
    public static final int BARCODE_MICROPDF417 = 84;
    public static final int BARCODE_DOTCODE = 115;
    public static final int BARCODE_MAXICODE = 57;
    public static final int BARCODE_DBAR_OMN = 29;
    public static final int BARCODE_DBAR_LTD = 30;
    public static final int BARCODE_DBAR_EXP = 31;
}
