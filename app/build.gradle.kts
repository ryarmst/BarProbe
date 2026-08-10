import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Release version, taken from the git tag when one is building a release.
 *
 * `RELEASE_VERSION` is set by the release workflow from the tag (`v1.2.3` arrives as
 * `1.2.3`). Local builds get the -dev fallback.
 *
 * versionCode has to increase for Android to treat a build as an upgrade; left at a
 * constant, every published APK would refuse to install over the previous one with a
 * bare "App not installed". Encoding the tag as major*10000 + minor*100 + patch keeps
 * it monotonic for versions with minor and patch below 100.
 */
val releaseVersionName: String = System.getenv("RELEASE_VERSION")?.takeIf { it.isNotBlank() }
    ?: "0.1.0-dev"

val releaseVersionCode: Int = Regex("""^(\d+)\.(\d+)\.(\d+)""")
    .find(releaseVersionName)
    ?.destructured
    ?.let { (major, minor, patch) ->
        major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
    }
    ?: 1

/**
 * Release signing.
 *
 * Key material never lives in the repository. It is read from, in order of precedence:
 *   1. environment variables (CI sets these from GitHub Actions secrets), or
 *   2. a gitignored keystore.properties at the repo root (for local release builds).
 * When neither is present the build falls back to the debug key: local and CI *debug*
 * builds keep working and forks without secrets still compile, but a debug-signed build
 * is only good for sideloading and is never what gets uploaded to Play.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingParam(env: String, prop: String): String? =
    System.getenv(env) ?: keystoreProperties.getProperty(prop)

val releaseStoreFile: String? = signingParam("SIGNING_KEYSTORE_FILE", "storeFile")

android {
    namespace = "dev.barcodeworkbench"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // The Play-registered package. Permanent once published. Kept distinct from
        // `namespace` (the internal Kotlin package root), which AGP allows to differ
        // and which there is no user-facing reason to rename across ~500 files.
        applicationId = "ca.ryarmst.barprobe"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Created only when a keystore is available; otherwise the release build falls
        // back to the debug key below. The passwords/alias come from the same
        // env-or-properties source as the store path, so no secret is ever written
        // into the build files.
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingParam("SIGNING_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingParam("SIGNING_KEY_ALIAS", "keyAlias")
                keyPassword = signingParam("SIGNING_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The real upload key when a keystore is configured (CI from secrets, or a
            // local keystore.properties); the debug key otherwise, which is fine for
            // sideloading but must never be what is uploaded to Play.
            signingConfig = signingConfigs.getByName(
                if (releaseStoreFile != null) "release" else "debug",
            )
        }
    }

    // Each ABI ships its own native encoder, so the sideload APKs split by ABI rather
    // than making every user download all three. This is for `assembleRelease` only:
    // an App Bundle does its own per-ABI splitting and AGP rejects the combination of
    // ABI splits and bundling (it produces multiple shrunk-resource files). So the
    // split is switched off whenever a bundle task is in the build, which means
    // `assembleRelease` and `bundleRelease` must be run as separate invocations.
    splits {
        abi {
            val buildingBundle = gradle.startParameter.taskNames.any {
                it.contains("bundle", ignoreCase = true)
            }
            isEnable = !buildingBundle
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":barcode:engine-api"))
    implementation(project(":barcode:zint"))
    implementation(project(":barcode:reader"))
    implementation(project(":barcode:render"))
    implementation(project(":feature:generator"))
    implementation(project(":feature:scanner"))
    implementation(project(":feature:catalogue"))
    implementation(project(":feature:configpacks"))
    implementation(project(":feature:learn"))
    // :barcode:radamsa and :feature:fuzz are built but not wired in: the fuzz engine
    // crashes under ART. They stay in the build (compiled, host-tested) but are not
    // packaged into the app. See TODO-fuzzing.md.

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
