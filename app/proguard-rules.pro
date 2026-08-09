# R8 configuration for release builds.
#
# Most dependencies ship their own consumer rules (Room, Hilt, kotlinx.serialization,
# the zxing-cpp AAR), so this file only covers what R8 cannot infer on its own.

# ---------------------------------------------------------------------------
# JNI boundary. This is the part R8 will silently break.
#
# The native layer resolves these by name at runtime:
#   - JNI binds native methods to C symbols like
#     Java_dev_barcodeworkbench_zint_ZintNative_nativeEncode, so the class and
#     method names must survive verbatim.
#   - zint_jni.c calls FindClass("dev/barcodeworkbench/zint/ZintResult"), then
#     GetMethodID for its no-arg constructor and GetFieldID for each field by
#     literal name.
#
# R8 has no way to see any of that: from bytecode alone ZintResult's fields look
# write-only and its constructor unused. Renaming or removing them compiles fine
# and produces an UnsatisfiedLinkError or a NoSuchFieldError the first time a
# barcode is generated.
# ---------------------------------------------------------------------------
-keepclasseswithmembernames,includedescriptorclasses class dev.barcodeworkbench.zint.ZintNative {
    native <methods>;
}
-keep class dev.barcodeworkbench.zint.ZintNative {
    native <methods>;
}

# Constructed and populated entirely from C: keep the name, the no-arg
# constructor and every field.
-keep class dev.barcodeworkbench.zint.ZintResult {
    <init>();
    <fields>;
}

# The libradamsa JNI keep rules were removed while the fuzz feature is shelved and
# :barcode:radamsa is not packaged into the app. Restore them alongside re-wiring the
# feature -- see TODO-fuzzing.md.

# ---------------------------------------------------------------------------
# Serialization
#
# Backup files and configuration packs are read back by later versions of the app,
# so the JSON field names are a compatibility surface. @SerialName covers the
# explicit ones, but a renamed class would still break the generated serialiser
# lookup.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class dev.barcodeworkbench.core.model.backup.** {
    *** Companion;
}
-keepclasseswithmembers class dev.barcodeworkbench.core.model.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.barcodeworkbench.core.model.backup.**$$serializer { *; }

-keepclassmembers class dev.barcodeworkbench.core.model.config.** {
    *** Companion;
}
-keepclasseswithmembers class dev.barcodeworkbench.core.model.config.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.barcodeworkbench.core.model.config.**$$serializer { *; }

# ---------------------------------------------------------------------------
# Enum names are persisted, not just displayed.
#
# SymbologyId, InputMode, CodeSource and VerificationStatus are all stored in the
# database and in backup files by name, then read back with valueOf(). Obfuscating
# them would make every existing row and every backup unreadable, and the failure
# would be quiet: valueOf throws, the mapper catches it, and entries simply vanish
# from listings.
# ---------------------------------------------------------------------------
-keepclassmembers enum dev.barcodeworkbench.core.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** entries();
}
-keepnames enum dev.barcodeworkbench.core.model.SymbologyId
-keepnames enum dev.barcodeworkbench.core.model.InputMode
-keepnames enum dev.barcodeworkbench.core.model.CodeSource
-keepnames enum dev.barcodeworkbench.core.model.config.VerificationStatus

# ---------------------------------------------------------------------------
# Reader engine. Formats are resolved from the registry's stored names with
# BarcodeReader.Format.valueOf(), so those enum constants must keep their names.
# ---------------------------------------------------------------------------
-keepnames class zxingcpp.BarcodeReader$Format
-keepclassmembers class zxingcpp.BarcodeReader$Format {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------------------
# Diagnostics
#
# Line numbers are kept and the source file attribute renamed, so a crash from a
# release build still maps to a real stack trace via the retained mapping file.
# ---------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
