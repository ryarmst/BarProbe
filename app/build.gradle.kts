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

android {
    namespace = "dev.barcodeworkbench"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.barcodeworkbench"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed with the debug key deliberately, so the release build is
            // installable for validating R8, resource shrinking and native packaging.
            // This is NOT distributable: replace with a real signing config before
            // publishing anything.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Each ABI ships its own native encoder, so split rather than making every
    // user download all three.
    splits {
        abi {
            isEnable = true
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
