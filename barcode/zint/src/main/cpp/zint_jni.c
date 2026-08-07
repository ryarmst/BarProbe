/*
 * JNI bridge over libzint's encoder.
 *
 * Deliberately narrow: it encodes a byte payload into a module matrix and
 * passes zint's own diagnostics back verbatim. All rendering (PNG/SVG/PDF)
 * happens on the JVM side from the returned matrix, so one renderer serves
 * every output format with identical geometry.
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "zint.h"

/* Bounds of zint_symbol.encoded_data[200][144]. */
#define ZJ_MAX_ROWS 200
#define ZJ_MAX_WIDTH 1152

#define RESULT_CLASS "dev/barcodeworkbench/zint/ZintResult"

/* Module test, matching common.h's z_module_is_set: LSB-first within each byte. */
static inline int zj_module_is_set(const struct zint_symbol *symbol, int y, int x) {
    return (symbol->encoded_data[y][x >> 3] >> (x & 0x07)) & 1;
}

/*
 * Build a ZintResult. Ownership note: `modules` and `rowHeights` may be NULL
 * when encoding failed, in which case only returnCode/errorText are meaningful.
 */
static jobject zj_new_result(JNIEnv *env,
                             jint return_code,
                             const char *error_text,
                             jint symbology,
                             jint rows,
                             jint width,
                             const unsigned char *modules,
                             const float *row_heights,
                             const char *hrt) {
    jclass cls = (*env)->FindClass(env, RESULT_CLASS);
    if (cls == NULL) {
        return NULL; /* NoClassDefFoundError already pending */
    }
    jmethodID ctor = (*env)->GetMethodID(env, cls, "<init>", "()V");
    if (ctor == NULL) {
        return NULL;
    }
    jobject result = (*env)->NewObject(env, cls, ctor);
    if (result == NULL) {
        return NULL;
    }

    jfieldID f_return_code = (*env)->GetFieldID(env, cls, "returnCode", "I");
    jfieldID f_error_text = (*env)->GetFieldID(env, cls, "errorText", "Ljava/lang/String;");
    jfieldID f_symbology = (*env)->GetFieldID(env, cls, "symbology", "I");
    jfieldID f_rows = (*env)->GetFieldID(env, cls, "rows", "I");
    jfieldID f_width = (*env)->GetFieldID(env, cls, "width", "I");
    jfieldID f_modules = (*env)->GetFieldID(env, cls, "modules", "[B");
    jfieldID f_row_heights = (*env)->GetFieldID(env, cls, "rowHeights", "[F");
    jfieldID f_hrt = (*env)->GetFieldID(env, cls, "hrt", "Ljava/lang/String;");
    if ((*env)->ExceptionCheck(env)) {
        return NULL;
    }

    (*env)->SetIntField(env, result, f_return_code, return_code);
    (*env)->SetIntField(env, result, f_symbology, symbology);
    (*env)->SetIntField(env, result, f_rows, rows);
    (*env)->SetIntField(env, result, f_width, width);

    if (error_text != NULL) {
        jstring s = (*env)->NewStringUTF(env, error_text);
        if (s == NULL) {
            return NULL;
        }
        (*env)->SetObjectField(env, result, f_error_text, s);
    }
    if (hrt != NULL) {
        jstring s = (*env)->NewStringUTF(env, hrt);
        if (s == NULL) {
            return NULL;
        }
        (*env)->SetObjectField(env, result, f_hrt, s);
    }
    if (modules != NULL && rows > 0 && width > 0) {
        const jsize count = (jsize) rows * (jsize) width;
        jbyteArray arr = (*env)->NewByteArray(env, count);
        if (arr == NULL) {
            return NULL;
        }
        (*env)->SetByteArrayRegion(env, arr, 0, count, (const jbyte *) modules);
        (*env)->SetObjectField(env, result, f_modules, arr);
    }
    if (row_heights != NULL && rows > 0) {
        jfloatArray arr = (*env)->NewFloatArray(env, rows);
        if (arr == NULL) {
            return NULL;
        }
        (*env)->SetFloatArrayRegion(env, arr, 0, rows, row_heights);
        (*env)->SetObjectField(env, result, f_row_heights, arr);
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_dev_barcodeworkbench_zint_ZintNative_nativeVersion(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return ZBarcode_Version();
}

JNIEXPORT jint JNICALL
Java_dev_barcodeworkbench_zint_ZintNative_nativeCap(JNIEnv *env, jclass clazz,
                                                    jint symbology, jint cap_flag) {
    (void) env;
    (void) clazz;
    return (jint) ZBarcode_Cap(symbology, (unsigned int) cap_flag);
}

JNIEXPORT jboolean JNICALL
Java_dev_barcodeworkbench_zint_ZintNative_nativeValidId(JNIEnv *env, jclass clazz, jint symbology) {
    (void) env;
    (void) clazz;
    return ZBarcode_ValidID(symbology) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_dev_barcodeworkbench_zint_ZintNative_nativeBarcodeName(JNIEnv *env, jclass clazz,
                                                            jint symbology) {
    (void) clazz;
    char name[32];
    memset(name, 0, sizeof(name));
    if (ZBarcode_BarcodeName(symbology, name) != 0) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, name);
}

JNIEXPORT jobject JNICALL
Java_dev_barcodeworkbench_zint_ZintNative_nativeEncode(JNIEnv *env, jclass clazz,
                                                       jint symbology,
                                                       jbyteArray data,
                                                       jint input_mode,
                                                       jint eci,
                                                       jint option_1,
                                                       jint option_2,
                                                       jint option_3,
                                                       jint output_options,
                                                       jfloat height,
                                                       jint warn_level) {
    (void) clazz;

    if (data == NULL) {
        return zj_new_result(env, ZINT_ERROR_INVALID_DATA, "No input data", symbology,
                             0, 0, NULL, NULL, NULL);
    }

    struct zint_symbol *symbol = ZBarcode_Create();
    if (symbol == NULL) {
        return zj_new_result(env, ZINT_ERROR_MEMORY, "Could not create zint symbol", symbology,
                             0, 0, NULL, NULL, NULL);
    }

    const jsize length = (*env)->GetArrayLength(env, data);
    /*
     * Copy the payload out rather than pinning it: encoding is not a hot path and
     * this keeps us clear of the JNI critical-region restrictions. The explicit
     * length is what allows embedded NUL bytes to survive, which a NUL-terminated
     * string API would silently truncate.
     */
    unsigned char *source = (unsigned char *) malloc((size_t) length + 1);
    if (source == NULL) {
        ZBarcode_Delete(symbol);
        return zj_new_result(env, ZINT_ERROR_MEMORY, "Out of memory", symbology,
                             0, 0, NULL, NULL, NULL);
    }
    if (length > 0) {
        (*env)->GetByteArrayRegion(env, data, 0, length, (jbyte *) source);
        if ((*env)->ExceptionCheck(env)) {
            free(source);
            ZBarcode_Delete(symbol);
            return NULL;
        }
    }
    source[length] = '\0';

    symbol->symbology = symbology;
    symbol->input_mode = input_mode;
    symbol->eci = eci;
    /*
     * ZBarcode_Create() callocs the struct, so zint's own "unset/auto" default
     * for these is 0. A negative value from the caller therefore means "leave
     * zint's default alone" -- assigning -1 would be taken as a literal, invalid
     * row count or version by symbologies like PDF417, Aztec and rMQR.
     */
    if (option_1 >= 0) {
        symbol->option_1 = option_1;
    }
    if (option_2 >= 0) {
        symbol->option_2 = option_2;
    }
    if (option_3 >= 0) {
        symbol->option_3 = option_3;
    }
    symbol->output_options = output_options;
    symbol->warn_level = warn_level;
    if (height > 0.0f) {
        symbol->height = height;
    }

    const int return_code = ZBarcode_Encode(symbol, source, (int) length);
    free(source);

    /*
     * Return codes below ZINT_ERROR are warnings and still yield a usable
     * symbol, so the matrix is extracted for those too.
     */
    if (return_code >= ZINT_ERROR) {
        jobject failure = zj_new_result(env, return_code, symbol->errtxt, symbology,
                                        0, 0, NULL, NULL, NULL);
        ZBarcode_Delete(symbol);
        return failure;
    }

    const int rows = symbol->rows;
    const int width = symbol->width;
    if (rows <= 0 || width <= 0 || rows > ZJ_MAX_ROWS || width > ZJ_MAX_WIDTH) {
        /* Guards against reading outside encoded_data if zint ever reports
         * dimensions beyond its own fixed buffer. */
        jobject failure = zj_new_result(env, ZINT_ERROR_ENCODING_PROBLEM,
                                        "Symbol dimensions outside supported range", symbology,
                                        0, 0, NULL, NULL, NULL);
        ZBarcode_Delete(symbol);
        return failure;
    }

    /* One byte per module keeps the JVM side free of bit-order assumptions. */
    unsigned char *modules = (unsigned char *) malloc((size_t) rows * (size_t) width);
    if (modules == NULL) {
        ZBarcode_Delete(symbol);
        return zj_new_result(env, ZINT_ERROR_MEMORY, "Out of memory", symbology,
                             0, 0, NULL, NULL, NULL);
    }
    for (int y = 0; y < rows; y++) {
        unsigned char *row = modules + (size_t) y * (size_t) width;
        for (int x = 0; x < width; x++) {
            row[x] = (unsigned char) zj_module_is_set(symbol, y, x);
        }
    }

    char hrt[256 + 1];
    memset(hrt, 0, sizeof(hrt));
    if (symbol->text_length > 0) {
        int hrt_len = symbol->text_length;
        if (hrt_len > 256) {
            hrt_len = 256;
        }
        memcpy(hrt, symbol->text, (size_t) hrt_len);
        hrt[hrt_len] = '\0';
    }

    jobject result = zj_new_result(env, return_code,
                                   symbol->errtxt[0] != '\0' ? symbol->errtxt : NULL,
                                   symbology, rows, width, modules, symbol->row_height,
                                   hrt[0] != '\0' ? hrt : NULL);
    free(modules);
    ZBarcode_Delete(symbol);
    return result;
}
