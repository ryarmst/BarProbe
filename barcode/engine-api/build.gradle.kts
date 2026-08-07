plugins {
    alias(libs.plugins.android.library)
}

// An Android library rather than a plain JVM one because the decoder contract
// unavoidably deals in platform image types. The encoder half stays pure.
android {
    namespace = "dev.barcodeworkbench.barcode.engine"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:model"))
    // CameraX appears here deliberately. The abstraction that matters is which
    // decode engine is in use, not which camera library; and letting the engine
    // read an ImageProxy directly avoids a YUV-to-bitmap conversion on every
    // preview frame, which is the difference between a smooth and a laggy scanner.
    api(libs.androidx.camera.core)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
