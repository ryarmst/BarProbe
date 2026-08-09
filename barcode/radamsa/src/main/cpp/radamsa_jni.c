/*
 * JNI bridge over libradamsa.
 *
 * Deliberately tiny: initialise once, then turn (bytes, seed) into mutated bytes.
 * Everything about what to do with those bytes -- validate, encode, display --
 * lives on the JVM side.
 *
 * Two constraints from the library, both enforced by the Kotlin caller rather
 * than here (see RadamsaNative / RadamsaMutator):
 *   - radamsa_init() must run exactly once per process. Each call reallocates the
 *     Owl heap and never frees the last, so repeated init leaks ~1.4 MB a time.
 *   - radamsa() is not re-entrant; calls must be serialised.
 */
#include <jni.h>
#include <stdlib.h>

#include "radamsa.h"

JNIEXPORT void JNICALL
Java_dev_barcodeworkbench_radamsa_RadamsaNative_nativeInit(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    radamsa_init();
}

JNIEXPORT jbyteArray JNICALL
Java_dev_barcodeworkbench_radamsa_RadamsaNative_nativeMutate(JNIEnv *env,
                                                             jclass clazz,
                                                             jbyteArray input,
                                                             jint seed,
                                                             jint max_length) {
    (void) clazz;

    if (max_length <= 0) {
        return (*env)->NewByteArray(env, 0);
    }

    jsize in_len = (*env)->GetArrayLength(env, input);

    /* A copy rather than a pinned pointer: radamsa reads the whole buffer and we
     * do not want it holding the JVM array pinned across the VM run. */
    uint8_t *in = NULL;
    if (in_len > 0) {
        in = (uint8_t *) malloc((size_t) in_len);
        if (in == NULL) {
            return NULL; /* OOM: let the JVM surface it */
        }
        (*env)->GetByteArrayRegion(env, input, 0, in_len, (jbyte *) in);
    }

    uint8_t *out = (uint8_t *) malloc((size_t) max_length);
    if (out == NULL) {
        free(in);
        return NULL;
    }

    size_t written = radamsa(in, (size_t) in_len, out, (size_t) max_length,
                             (unsigned int) seed);

    free(in);

    jbyteArray result = (*env)->NewByteArray(env, (jsize) written);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize) written, (jbyte *) out);
    }
    free(out);
    return result;
}
