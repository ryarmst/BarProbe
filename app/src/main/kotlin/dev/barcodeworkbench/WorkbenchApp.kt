package dev.barcodeworkbench

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.barcodeworkbench.feature.catalogue.CatalogueScreen
import dev.barcodeworkbench.feature.configpacks.ConfigPacksScreen
import dev.barcodeworkbench.feature.generator.GeneratorScreen
import dev.barcodeworkbench.feature.learn.LearnScreen
import dev.barcodeworkbench.feature.scanner.ScannerScreen
import dev.barcodeworkbench.navigation.TopLevelDestination
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun WorkbenchApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.all.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Single instance per tab, and returning to a tab
                                // restores where the user left off.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Generator.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.Generator.route) { GeneratorScreen() }
            composable(TopLevelDestination.Scanner.route) { ScannerScreen() }
            composable(TopLevelDestination.Catalogue.route) { CatalogueScreen() }
            composable(TopLevelDestination.Learn.route) { LearnScreen() }
            composable(TopLevelDestination.ConfigPacks.route) {
                // The config screen renders symbols from stored data strings, so it
                // needs the encoder; it is provided here rather than injected into the
                // screen so the feature module stays free of a Hilt entry point.
                val encoderHolder: EncoderHolder = hiltViewModel()
                ConfigPacksScreen(encoder = encoderHolder.encoder)
            }
        }
    }
}
