package app.cloven.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.cloven.AppContainer
import app.cloven.MainActivity
import app.cloven.R
import app.cloven.ui.activity.ActivityScreen
import app.cloven.ui.activity.ActivityViewModel
import app.cloven.ui.library.LibraryScreen
import app.cloven.ui.library.LibraryViewModel
import app.cloven.ui.reader.ReaderScreen
import app.cloven.ui.reader.ReaderViewModel
import app.cloven.ui.settings.SettingsScreen
import app.cloven.ui.settings.SettingsViewModel

object Routes {
    const val LIBRARY = "library"
    const val ACTIVITY = "activity"
    const val SETTINGS = "settings"
    const val READER = "reader/{bookId}"
    fun reader(bookId: String) = "reader/$bookId"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector?)

private val topLevel = listOf(
    TopLevel(Routes.LIBRARY, "Library", Icons.Outlined.LocalLibrary),
    TopLevel(Routes.ACTIVITY, "Activity", null), // drawn with the clover mark
    TopLevel(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun ClovenRoot(container: AppContainer, requestedDestination: String?, onDestinationConsumed: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route == null || topLevel.any { it.route == route }

    LaunchedEffect(requestedDestination) {
        if (requestedDestination == MainActivity.DESTINATION_ACTIVITY) {
            nav.navigateTopLevel(Routes.ACTIVITY)
            onDestinationConsumed()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = { nav.navigateTopLevel(item.route) },
                            icon = {
                                if (item.icon != null) Icon(item.icon, contentDescription = null)
                                else Icon(painterResource(R.drawable.cloven_logo), contentDescription = null)
                            },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.LIBRARY, modifier = Modifier.padding(padding)) {
            composable(Routes.LIBRARY) {
                val vm = viewModel { LibraryViewModel(container.library, container.importManager) }
                LibraryScreen(vm, onOpenBook = { nav.navigate(Routes.reader(it)) })
            }
            composable(Routes.ACTIVITY) {
                val vm = viewModel { ActivityViewModel(container.activity) }
                ActivityScreen(vm)
            }
            composable(Routes.SETTINGS) {
                val vm = viewModel { SettingsViewModel(container.preferences, container.library) }
                SettingsScreen(vm)
            }
            composable(Routes.READER, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { backStack ->
                val bookId = backStack.arguments?.getString("bookId").orEmpty()
                val vm = viewModel { ReaderViewModel(bookId, container) }
                ReaderScreen(vm, onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
