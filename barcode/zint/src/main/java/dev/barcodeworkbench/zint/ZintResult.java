package dev.barcodeworkbench.zint;

/**
 * Outcome of a single libzint encode.
 *
 * <p>Fields are populated from JNI. A {@code returnCode} below
 * {@link ZintConstants#ZINT_ERROR} means the symbol was produced; values at or
 * above it mean encoding failed and only {@code returnCode} and
 * {@code errorText} are meaningful.
 */
public final class ZintResult {

    /** zint return code: 0 = clean, 1-4 = warning (symbol still valid), >=5 = error. */
    public int returnCode;

    /** zint's own diagnostic text, passed through verbatim. Null when absent. */
    public String errorText;

    /** Echo of the requested symbology id. */
    public int symbology;

    /** Module rows. 1 for most linear symbologies. */
    public int rows;

    /** Module columns. */
    public int width;

    /**
     * One byte per module, row-major, {@code rows * width} entries, each 0 or 1.
     * Null when encoding failed.
     */
    public byte[] modules;

    /**
     * Per-row heights in X-dimensions, as reported by zint. Needed to render
     * stacked symbologies (PDF417, DataBar) with correct proportions.
     */
    public float[] rowHeights;

    /** Human Readable Text, when the symbology provides it. Null otherwise. */
    public String hrt;

    ZintResult() {
        // Instantiated from JNI via the no-arg constructor.
    }

    /** True when a symbol was produced, even if zint also raised a warning. */
    public boolean isSuccess() {
        return returnCode < ZintConstants.ZINT_ERROR && modules != null;
    }

    /** True when a symbol was produced but zint flagged a caveat. */
    public boolean isWarning() {
        return returnCode > 0 && returnCode < ZintConstants.ZINT_ERROR;
    }

    /** Module value at (x, y). Throws if the encode failed. */
    public int moduleAt(int x, int y) {
        if (modules == null) {
            throw new IllegalStateException("No symbol was produced: " + errorText);
        }
        if (x < 0 || x >= width || y < 0 || y >= rows) {
            throw new IndexOutOfBoundsException("(" + x + "," + y + ") outside " + width + "x" + rows);
        }
        return modules[y * width + x];
    }
}
