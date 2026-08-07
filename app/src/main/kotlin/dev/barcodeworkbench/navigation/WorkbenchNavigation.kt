package dev.barcodeworkbench.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import dev.barcodeworkbench.core.designsystem.icon.WorkbenchIcons
import dev.barcodeworkbench.R

/**
 * The top-level destinations: the four capabilities the app provides, plus the
 * reference section. Kept as a sealed hierarchy so the navigation graph and the
 * bottom bar are generated from one list and cannot drift apart.
 */
sealed class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    data object Generator : TopLevelDestination(
        route = "generator",
        labelRes = R.string.nav_generate,
        icon = WorkbenchIcons.QrCode2,
    )

    data object Scanner : TopLevelDestination(
        route = "scanner",
        labelRes = R.string.nav_scan,
        icon = WorkbenchIcons.CameraAlt,
    )

    data object Catalogue : TopLevelDestination(
        route = "catalogue",
        labelRes = R.string.nav_catalogue,
        icon = WorkbenchIcons.Inventory2,
    )

    data object ConfigPacks : TopLevelDestination(
        route = "configpacks",
        labelRes = R.string.nav_config,
        icon = WorkbenchIcons.Build,
    )

    data object Learn : TopLevelDestination(
        route = "learn",
        labelRes = R.string.nav_learn,
        icon = WorkbenchIcons.MenuBook,
    )

    companion object {
        val all: List<TopLevelDestination> =
            listOf(Generator, Scanner, Catalogue, ConfigPacks, Learn)
    }
}
