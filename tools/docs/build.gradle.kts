plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Build-time documentation tooling. Two entry points:
//   GenerateConceptsKt  — parses docs/learn/*.md into the app's typed Concepts.kt
//   GenerateReferenceKt — emits docs/reference/*.md from the symbology registry
// Not shipped in the app; invoked by :feature:learn's codegen task and the Pages
// workflow. Depends on :core:model so the reference reflects the real encoder tables.
dependencies {
    implementation(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Emits the reference Markdown (symbology + escape tables) for the Pages site. The
// output dir defaults under build/ and can be overridden with -PreferenceOut=<dir>.
tasks.register<JavaExec>("generateReference") {
    // Resolved against the repo root, not this module, so -PreferenceOut=docs/reference
    // lands where the site expects it.
    val outDir = (project.findProperty("referenceOut") as String?)
        ?.let { rootProject.file(it) }
        ?: layout.buildDirectory.dir("reference").get().asFile
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.barcodeworkbench.tools.docs.GenerateReferenceKt")
    args(outDir.path)
    outputs.dir(outDir)
    doFirst { outDir.mkdirs() }
}
