import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/** JavaExec that also declares the directory it generates into, so the Android variant
 *  API can wire it as a generated source directory (task ordering included). */
abstract class GenerateLearnConceptsTask : JavaExec() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

android {
    namespace = "dev.barcodeworkbench.feature.learn"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
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

// The Learn articles are authored as Markdown under docs/learn and compiled into the
// typed Concepts object here, before Kotlin/KSP run. Keeping the app model typed means
// the article content is still checked by test (ConceptsTest), while the source of
// truth is plain Markdown that also feeds the GitHub Pages site.
val docsTool: Configuration by configurations.creating
dependencies { docsTool(project(":tools:docs")) }

val generateLearnConcepts = tasks.register<GenerateLearnConceptsTask>("generateLearnConcepts") {
    val docsDir = rootProject.layout.projectDirectory.dir("docs/learn")
    inputs.dir(docsDir).withPropertyName("articles")
    outputDir.set(layout.buildDirectory.dir("generated/learndocs/kotlin"))
    classpath = docsTool
    mainClass.set("dev.barcodeworkbench.tools.docs.GenerateConceptsKt")
    argumentProviders.add(
        CommandLineArgumentProvider {
            val file = outputDir.get()
                .file("dev/barcodeworkbench/feature/learn/content/Concepts.kt").asFile
            listOf(docsDir.asFile.path, file.path)
        },
    )
}

// The AGP 9 variant API adds the generated directory as a source and wires the task
// dependency for both Kotlin compilation and KSP, avoiding the sourceSets DSL.
androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(
            generateLearnConcepts,
            GenerateLearnConceptsTask::outputDir,
        )
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
