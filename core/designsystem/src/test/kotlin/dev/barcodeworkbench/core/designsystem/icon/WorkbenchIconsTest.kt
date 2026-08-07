package dev.barcodeworkbench.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Proves the vendored icons are the real ones.
 *
 * `material-icons-extended` is a test-only dependency of this module and exists solely
 * for this comparison. Vendoring icons is normally a quiet way to introduce a subtly
 * wrong shape; here a mistake fails the build instead.
 *
 * If this module ever drops the test dependency, delete this file rather than weakening
 * it — a comparison against nothing is worse than none.
 */
class WorkbenchIconsTest {

    private val pairs: List<Triple<String, ImageVector, ImageVector>> = listOf(
        Triple("QrCode2", WorkbenchIcons.QrCode2, Icons.Outlined.QrCode2),
        Triple("CameraAlt", WorkbenchIcons.CameraAlt, Icons.Outlined.CameraAlt),
        Triple("Inventory2", WorkbenchIcons.Inventory2, Icons.Outlined.Inventory2),
        Triple("Build", WorkbenchIcons.Build, Icons.Outlined.Build),
        Triple("MenuBook", WorkbenchIcons.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
        Triple("Close", WorkbenchIcons.Close, Icons.Filled.Close),
        Triple("Refresh", WorkbenchIcons.Refresh, Icons.Filled.Refresh),
    )

    @Test
    fun `each vendored icon equals the library original`() {
        pairs.forEach { (label, vendored, original) ->
            assertWithMessage("$label differs from the Material original")
                .that(vendored)
                .isEqualTo(original)
        }
    }

    @Test
    fun `menu book is auto-mirrored for right-to-left layouts`() {
        // The one icon here whose direction carries meaning; losing the flag would be
        // invisible in a left-to-right locale.
        assertThat(WorkbenchIcons.MenuBook.autoMirror).isTrue()
    }

    @Test
    fun `icons keep the standard 24dp material geometry`() {
        pairs.forEach { (label, vendored, _) ->
            assertWithMessage("viewport width of $label").that(vendored.viewportWidth)
                .isEqualTo(24f)
            assertWithMessage("viewport height of $label").that(vendored.viewportHeight)
                .isEqualTo(24f)
        }
    }
}
