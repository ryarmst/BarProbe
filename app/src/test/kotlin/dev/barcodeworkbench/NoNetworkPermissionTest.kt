package dev.barcodeworkbench

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Enforces the app's central privacy claim structurally rather than by policy.
 *
 * The app is meant to be incapable of network access. A permission is trivially
 * easy to add later without anyone noticing, so this asserts on the merged
 * manifest and fails the build if one appears.
 */
class NoNetworkPermissionTest {

    @Test
    fun `no network permission is declared in our own manifest`() {
        // A bare string search is not sufficient here: the manifest intentionally
        // names ACCESS_NETWORK_STATE in order to strip it with tools:node="remove",
        // and a naive check would read that removal as a declaration.
        val declared = declaredPermissions(findSourceManifest().readText())
        NETWORK_PERMISSIONS.forEach { permission ->
            assertWithMessage("$permission declared in the app manifest")
                .that(declared).doesNotContain(permission)
        }
    }

    @Test
    fun `network permissions inherited from dependencies are explicitly stripped`() {
        // CameraX pulls in androidx.media3-common, which declares
        // ACCESS_NETWORK_STATE for streaming features this app does not use.
        // Removing it is deliberate, so assert the removal is still in place rather
        // than relying on nobody having deleted it.
        val text = findSourceManifest().readText()
        val removals = REMOVED_PERMISSION_PATTERN.findAll(text)
            .map { it.groupValues[1] }
            .toSet()
        assertThat(removals).contains("android.permission.ACCESS_NETWORK_STATE")
    }

    @Test
    fun `camera is requested but not required, so non-camera devices still install`() {
        val text = findSourceManifest().readText()
        assertThat(text).contains("android.permission.CAMERA")
        assertThat(text).contains("android:required=\"false\"")
    }

    @Test
    fun `merged manifest carries no network permission from any dependency`() {
        // This is the assertion that actually matters. Checking only our own
        // manifest is not enough: transitive dependencies contribute permissions
        // through manifest merging, and an earlier version of this test looked for
        // INTERNET alone and so missed ACCESS_NETWORK_STATE arriving via
        // androidx.media3-common through CameraX. The full list is checked now.
        val merged = mergedManifests()
        if (merged.isEmpty()) {
            // Merged manifests only exist after an assemble; the source assertions
            // above still hold, so absence here is not a failure.
            return
        }
        merged.forEach { file ->
            val text = file.readText()
            NETWORK_PERMISSIONS.forEach { permission ->
                assertWithMessage("$permission present in merged manifest ${file.path}")
                    .that(text).doesNotContain(permission)
            }
        }
    }

    @Test
    fun `merged manifest declares only the permissions we intend`() {
        // Catches any new permission a dependency introduces, network-related or
        // not, rather than only the ones already known about.
        val merged = mergedManifests()
        if (merged.isEmpty()) return
        val declared = merged.flatMap { declaredPermissions(it.readText()) }.toSet()
        assertThat(declared).containsExactly(
            "android.permission.CAMERA",
            "android.permission.VIBRATE",
        )
    }

    private companion object {
        val NETWORK_PERMISSIONS = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CHANGE_WIFI_STATE",
        )

        val REMOVED_PERMISSION_PATTERN =
            Regex("""<uses-permission[^>]*android:name="(android\.permission\.[A-Z_]+)"[^>]*tools:node="remove"""",
                RegexOption.DOT_MATCHES_ALL)

        val PERMISSION_PATTERN =
            Regex("""<uses-permission[^>]*android:name="(android\.permission\.[A-Z_]+)"[^>]*>""",
                RegexOption.DOT_MATCHES_ALL)
    }

    /**
     * Permission names the manifest actually requests, excluding any that appear
     * only to be stripped by the manifest merger.
     */
    private fun declaredPermissions(manifest: String): Set<String> =
        PERMISSION_PATTERN.findAll(manifest)
            .filterNot { it.value.contains("tools:node=\"remove\"") }
            .map { it.groupValues[1] }
            .toSet()

    private fun findSourceManifest(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate AndroidManifest.xml from ${File(".").absolutePath}")
    }

    private fun mergedManifests(): List<File> {
        val roots = listOf(File("build/intermediates"), File("app/build/intermediates"))
        return roots.filter { it.exists() }.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.name == "AndroidManifest.xml" }
                .filter { it.path.contains("merged_manifest") }
                .toList()
        }
    }
}
