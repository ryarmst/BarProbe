package dev.barcodeworkbench.zint;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 1 spike verifier.
 *
 * <p>Encodes a fixture corpus through the JNI layer and prints each symbol in
 * exactly the format libzint's own {@code --dump} produces, so the output can be
 * diffed byte-for-byte against the reference CLI. This is what makes the encode
 * path verifiable without a device.
 *
 * <p>Becomes a JUnit test in the real module; kept as a main() here so the spike
 * runs with nothing but javac and java.
 */
public final class ZintSpikeVerifier {

    /** A fixture: name, symbology, payload, input mode, ECI. */
    private record Case(String name, int symbology, String data, int inputMode, int eci) {
        Case(String name, int symbology, String data, int inputMode) {
            this(name, symbology, data, inputMode, 0);
        }
    }

    private static final int ESC = ZintConstants.UNICODE_MODE | ZintConstants.ESCAPE_MODE;
    private static final int ESC_EXTRA = ESC | ZintConstants.EXTRA_ESCAPE_MODE;
    private static final int BIN_ESC = ZintConstants.DATA_MODE | ZintConstants.ESCAPE_MODE;

    private static final List<Case> CASES = List.of(
            new Case("code128-baseline", ZintConstants.BARCODE_CODE128, "ABC123",
                    ZintConstants.UNICODE_MODE),
            new Case("code128-embedded-gs", ZintConstants.BARCODE_CODE128, "AB\\x1DCD", ESC),
            new Case("code128-codeset-switch", ZintConstants.BARCODE_CODE128, "\\^A001\\^BABC",
                    ESC_EXTRA),
            new Case("code128-fnc1", ZintConstants.BARCODE_CODE128, "\\^1010123456789", ESC_EXTRA),
            new Case("gs1-128-ai", ZintConstants.BARCODE_GS1_128, "[01]09501101530003",
                    ZintConstants.UNICODE_MODE),
            new Case("qr-eci26-unicode", ZintConstants.BARCODE_QRCODE, "café", ESC, 26),
            new Case("datamatrix-binary-nul", ZintConstants.BARCODE_DATAMATRIX,
                    "\\x00\\x01\\xFF\\xFE", BIN_ESC),
            new Case("ean13", ZintConstants.BARCODE_EAN13, "012345678901",
                    ZintConstants.UNICODE_MODE),
            new Case("pdf417", ZintConstants.BARCODE_PDF417, "PDF417 payload",
                    ZintConstants.UNICODE_MODE),
            new Case("aztec", ZintConstants.BARCODE_AZTEC, "Aztec payload",
                    ZintConstants.UNICODE_MODE),
            new Case("datamatrix-text", ZintConstants.BARCODE_DATAMATRIX, "DM payload",
                    ZintConstants.UNICODE_MODE),
            new Case("maxicode", ZintConstants.BARCODE_MAXICODE, "MaxiCode payload",
                    ZintConstants.UNICODE_MODE),
            new Case("dotcode", ZintConstants.BARCODE_DOTCODE, "DotCode",
                    ZintConstants.UNICODE_MODE),
            new Case("microqr", ZintConstants.BARCODE_MICROQR, "MQR", ZintConstants.UNICODE_MODE),
            new Case("rmqr", ZintConstants.BARCODE_RMQR, "rMQR payload",
                    ZintConstants.UNICODE_MODE),
            new Case("dbar-omn", ZintConstants.BARCODE_DBAR_OMN, "0950110153000",
                    ZintConstants.UNICODE_MODE)
    );

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        System.out.println("### libzint version: " + ZintNative.version());

        for (Case c : CASES) {
            System.out.println("=== CASE " + c.name());
            ZintResult r = ZintNative.encode(
                    c.symbology(),
                    c.data().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    c.inputMode(),
                    c.eci(),
                    -1, -1, -1,
                    0, 0f, 0);

            if (!r.isSuccess()) {
                System.out.println("ENCODE-FAILED rc=" + r.returnCode + " msg=" + r.errorText);
                failures.add(c.name() + ": rc=" + r.returnCode + " " + r.errorText);
                continue;
            }
            if (r.isWarning()) {
                System.out.println("# warning rc=" + r.returnCode + ": " + r.errorText);
            }
            System.out.print(toDump(r));
        }

        // Capability cross-check: the registry's claims must match the library.
        System.out.println("=== CAPABILITIES");
        checkCap("QRCODE", ZintConstants.BARCODE_QRCODE, ZintConstants.ZINT_CAP_ECI, true, failures);
        checkCap("QRCODE", ZintConstants.BARCODE_QRCODE, ZintConstants.ZINT_CAP_MASK, true, failures);
        checkCap("GS1_128", ZintConstants.BARCODE_GS1_128, ZintConstants.ZINT_CAP_GS1, true, failures);
        checkCap("CODE128", ZintConstants.BARCODE_CODE128, ZintConstants.ZINT_CAP_HRT, true, failures);
        checkCap("EAN13", ZintConstants.BARCODE_EAN13, ZintConstants.ZINT_CAP_EANUPC, true, failures);
        checkCap("PDF417", ZintConstants.BARCODE_PDF417, ZintConstants.ZINT_CAP_STRUCTAPP, true,
                failures);
        checkCap("MAXICODE", ZintConstants.BARCODE_MAXICODE, ZintConstants.ZINT_CAP_FIXED_RATIO,
                true, failures);
        checkCap("CODE39", ZintConstants.BARCODE_CODE39, ZintConstants.ZINT_CAP_ECI, false,
                failures);

        /*
         * Every symbology constant is asserted against the linked library's own
         * name for that id. This is the safeguard that caught BARCODE_EAN13 being
         * transcribed as 11 (which is actually EAN_2ADDON) and is the reason the
         * registry cannot silently drift from libzint.
         */
        System.out.println("=== SYMBOLOGY ID VERIFICATION");
        verifyId("BARCODE_CODE11", ZintConstants.BARCODE_CODE11, failures);
        verifyId("BARCODE_C25INTER", ZintConstants.BARCODE_C25INTER, failures);
        verifyId("BARCODE_CODE39", ZintConstants.BARCODE_CODE39, failures);
        verifyId("BARCODE_EAN8", ZintConstants.BARCODE_EAN8, failures);
        verifyId("BARCODE_EAN13", ZintConstants.BARCODE_EAN13, failures);
        verifyId("BARCODE_GS1_128", ZintConstants.BARCODE_GS1_128, failures);
        verifyId("BARCODE_CODABAR", ZintConstants.BARCODE_CODABAR, failures);
        verifyId("BARCODE_CODE128", ZintConstants.BARCODE_CODE128, failures);
        verifyId("BARCODE_CODE93", ZintConstants.BARCODE_CODE93, failures);
        verifyId("BARCODE_DBAR_OMN", ZintConstants.BARCODE_DBAR_OMN, failures);
        verifyId("BARCODE_DBAR_LTD", ZintConstants.BARCODE_DBAR_LTD, failures);
        verifyId("BARCODE_DBAR_EXP", ZintConstants.BARCODE_DBAR_EXP, failures);
        verifyId("BARCODE_TELEPEN", ZintConstants.BARCODE_TELEPEN, failures);
        verifyId("BARCODE_UPCA", ZintConstants.BARCODE_UPCA, failures);
        verifyId("BARCODE_UPCE", ZintConstants.BARCODE_UPCE, failures);
        verifyId("BARCODE_MSI_PLESSEY", ZintConstants.BARCODE_MSI_PLESSEY, failures);
        verifyId("BARCODE_PDF417", ZintConstants.BARCODE_PDF417, failures);
        verifyId("BARCODE_MAXICODE", ZintConstants.BARCODE_MAXICODE, failures);
        verifyId("BARCODE_QRCODE", ZintConstants.BARCODE_QRCODE, failures);
        verifyId("BARCODE_DATAMATRIX", ZintConstants.BARCODE_DATAMATRIX, failures);
        verifyId("BARCODE_MICROPDF417", ZintConstants.BARCODE_MICROPDF417, failures);
        verifyId("BARCODE_ITF14", ZintConstants.BARCODE_ITF14, failures);
        verifyId("BARCODE_AZTEC", ZintConstants.BARCODE_AZTEC, failures);
        verifyId("BARCODE_MICROQR", ZintConstants.BARCODE_MICROQR, failures);
        verifyId("BARCODE_DOTCODE", ZintConstants.BARCODE_DOTCODE, failures);
        verifyId("BARCODE_RMQR", ZintConstants.BARCODE_RMQR, failures);

        // Binary fidelity: a NUL byte and high bytes must survive as distinct payloads.
        System.out.println("=== BINARY FIDELITY");
        byte[] withNul = new byte[]{0x41, 0x00, 0x42};
        byte[] truncated = new byte[]{0x41};
        ZintResult rNul = ZintNative.encode(ZintConstants.BARCODE_DATAMATRIX, withNul,
                ZintConstants.DATA_MODE);
        ZintResult rTrunc = ZintNative.encode(ZintConstants.BARCODE_DATAMATRIX, truncated,
                ZintConstants.DATA_MODE);
        if (!rNul.isSuccess() || !rTrunc.isSuccess()) {
            failures.add("binary fidelity: encode failed");
        } else {
            String a = toDump(rNul);
            String b = toDump(rTrunc);
            boolean distinct = !a.equals(b);
            System.out.println("A\\x00B distinct from A: " + distinct);
            if (!distinct) {
                failures.add("binary fidelity: NUL byte was truncated");
            }
        }

        // Error surfacing: bad check digit must come back as a structured error.
        System.out.println("=== ERROR SURFACING");
        ZintResult bad = ZintNative.encode(ZintConstants.BARCODE_EAN13,
                "abcdefghijklm".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                ZintConstants.UNICODE_MODE);
        System.out.println("invalid EAN13 rc=" + bad.returnCode + " msg=" + bad.errorText);
        if (bad.isSuccess()) {
            failures.add("error surfacing: invalid EAN-13 unexpectedly succeeded");
        }
        if (bad.errorText == null || bad.errorText.isEmpty()) {
            failures.add("error surfacing: no diagnostic text returned");
        }

        System.out.println("=== SUMMARY");
        if (failures.isEmpty()) {
            System.out.println("SELF-CHECKS PASSED");
        } else {
            System.out.println("SELF-CHECK FAILURES (" + failures.size() + "):");
            failures.forEach(f -> System.out.println("  - " + f));
        }
    }

    /**
     * Asserts that a constant's name in this codebase matches the name libzint
     * itself reports for that id.
     */
    private static void verifyId(String constantName, int id, List<String> failures) {
        String libraryName = ZintNative.barcodeName(id);
        boolean ok = constantName.equals(libraryName);
        System.out.println((ok ? "ok   " : "MISMATCH ") + id + " " + constantName
                + " -> library says " + libraryName);
        if (!ok) {
            failures.add("symbology id " + id + " declared as " + constantName
                    + " but library reports " + libraryName);
        }
    }

    private static void checkCap(String label, int symbology, int flag, boolean expected,
                                 List<String> failures) {
        boolean actual = ZintNative.hasCapability(symbology, flag);
        System.out.println(label + " cap 0x" + Integer.toHexString(flag) + " = " + actual);
        if (actual != expected) {
            failures.add(label + " cap 0x" + Integer.toHexString(flag)
                    + " expected " + expected + " got " + actual);
        }
    }

    /**
     * Reproduces libzint's TXT/--dump format from the module matrix: one hex
     * nibble per 4 modules packed MSB-first, a space after every 2 nibbles, and a
     * trailing partial nibble left-shifted to fill.
     */
    static String toDump(ZintResult r) {
        StringBuilder out = new StringBuilder();
        for (int y = 0; y < r.rows; y++) {
            int space = 0;
            int byt = 0;
            for (int x = 0; x < r.width; x++) {
                byt <<= 1;
                if (r.moduleAt(x, y) != 0) {
                    byt++;
                }
                if (((x + 1) & 0x3) == 0) {
                    out.append(HEX[byt]);
                    space++;
                    byt = 0;
                }
                if (space == 2 && x + 1 < r.width) {
                    out.append(' ');
                    space = 0;
                }
            }
            if ((r.width & 0x03) != 0) {
                byt <<= 4 - (r.width & 0x03);
                out.append(HEX[byt]);
            }
            out.append('\n');
        }
        return out.toString();
    }
}
