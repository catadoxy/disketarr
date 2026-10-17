package io.github.catadoxy.disketarr.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.catadoxy.disketarr.data.SettingsStore
import io.github.catadoxy.disketarr.ui.screens.DetailsScreen
import io.github.catadoxy.disketarr.ui.screens.MoviesScreen
import io.github.catadoxy.disketarr.ui.screens.SearchScreen
import io.github.catadoxy.disketarr.ui.screens.SettingsScreen
import io.github.catadoxy.disketarr.ui.screens.SetupScreen
import io.github.catadoxy.disketarr.ui.screens.ShowsScreen
import io.github.catadoxy.disketarr.ui.screens.StatisticsScreen


private object Routes {
    const val SETUP = "setup"
    const val SHOWS = "shows"
    const val MOVIES = "movies"
    const val SEARCH = "search"
    const val STATISTICS = "statistics"
    const val SETTINGS = "settings"
    const val DETAILS = "details/{source}/{mediaType}/{mediaId}"

    fun details(source: String, mediaType: String, mediaId: String) =
        "details/$source/${detailRouteMediaType(mediaType)}/$mediaId"
}

/**
 * Disketarr's library can represent a show's season as a `season` row, but the
 * top-level detail endpoint only accepts supported parent media types. The row's
 * media_id is the parent show ID, so open the show's detail endpoint instead of
 * requesting the unsupported `/media/season/{source}/{id}/` route.
 */
internal fun detailRouteMediaType(mediaType: String): String = when (mediaType.lowercase()) {
    "season", "episode" -> "tv"
    else -> mediaType
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun DisketarrNavHost(
    settingsStore: SettingsStore,
    deepLink: Uri? = null,
) {
    val navController: NavHostController = rememberNavController()

    LaunchedEffect(deepLink) {
        val (source, mediaType, mediaId) = parseDeepLink(deepLink) ?: return@LaunchedEffect
        navController.navigate(Routes.details(source, mediaType, mediaId)) {
            launchSingleTop = true
        }
    }
    val selectedCategories = settingsStore.selectedCategories()
    val startDestination = if (!settingsStore.isConfigured) {
        Routes.SETUP
    } else if (selectedCategories.contains("tv")) {
        Routes.SHOWS
    } else if (selectedCategories.contains("movie")) {
        Routes.MOVIES
    } else {
        Routes.STATISTICS
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = buildList {
        if (selectedCategories.contains("tv")) add(BottomNavItem(Routes.SHOWS, "Shows", Icons.Default.LiveTv))
        if (selectedCategories.contains("movie")) add(BottomNavItem(Routes.MOVIES, "Movies", Icons.Default.Movie))
        add(BottomNavItem(Routes.STATISTICS, "Stats", Icons.Default.BarChart))
        add(BottomNavItem(Routes.SETTINGS, "Settings", Icons.Default.Settings))
    }

    val showBottomBar = currentRoute !in listOf(Routes.SETUP, Routes.DETAILS)
    val showAddFab = currentRoute == Routes.SHOWS || currentRoute == Routes.MOVIES

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = item.route != Routes.SHOWS
                                        }
                                        launchSingleTop = true
                                        restoreState = item.route != Routes.SHOWS
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showAddFab) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add to library")
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.SETUP) {
                SetupScreen(
                    onSetupComplete = {
                        navController.navigate(Routes.SHOWS) {
                            popUpTo(Routes.SETUP) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.SHOWS) {
                ShowsScreen(
                    onOpenDetail = { result ->
                        navController.navigate(
                            Routes.details(result.source, result.mediaType, result.mediaId),
                        )
                    },
                    onOpenSearch = {
                        navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                    },
                )
            }

            composable(Routes.MOVIES) {
                MoviesScreen(
                    onOpenDetail = { result ->
                        navController.navigate(
                            Routes.details(result.source, result.mediaType, result.mediaId),
                        )
                    },
                    onOpenSearch = {
                        navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                    },
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onResultClick = { result ->
                        navController.navigate(
                            Routes.details(result.source, result.mediaType, result.mediaId),
                        )
                    },
                )
            }

            composable(Routes.STATISTICS) {
                StatisticsScreen()
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onResetToSetup = {
                        navController.navigate(Routes.SETUP) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.DETAILS) { backStackEntry ->
                val source = backStackEntry.arguments?.getString("source").orEmpty()
                val mediaType = backStackEntry.arguments?.getString("mediaType").orEmpty()
                val mediaId = backStackEntry.arguments?.getString("mediaId").orEmpty()
                DetailsScreen(
                    source = source,
                    mediaType = mediaType,
                    mediaId = mediaId,
                )
            }
        }
    }
}

/** Parses `Disketarr://details/{source}/{mediaType}/{mediaId}` deep links. */
private fun parseDeepLink(uri: Uri?): Triple<String, String, String>? {
    if (uri == null || uri.scheme != "disketarr") return null
    // Supported forms:
    //   Disketarr://details/{source}/{mediaType}/{mediaId}  (host = "details")
    //   Disketarr:///details/{source}/{mediaType}/{mediaId} (legacy path form)
    val detailSegments = if (uri.host == "details") uri.pathSegments else {
        if (uri.pathSegments.size >= 4 && uri.pathSegments[0] == "details") {
            uri.pathSegments.subList(1, uri.pathSegments.size)
        } else {
            uri.pathSegments
        }
    }
    if (detailSegments.size < 3) return null
    val source = Uri.decode(detailSegments[0])
    val mediaType = detailRouteMediaType(Uri.decode(detailSegments[1]))
    val mediaId = Uri.decode(detailSegments[2])
    if (source.isBlank() || mediaType.isBlank() || mediaId.isBlank()) return null
    return Triple(source, mediaType, mediaId)
}