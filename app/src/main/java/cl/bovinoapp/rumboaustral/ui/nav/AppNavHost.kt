package cl.bovinoapp.rumboaustral.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import cl.bovinoapp.rumboaustral.AppContainer
import cl.bovinoapp.rumboaustral.ui.downloads.DownloadsScreen
import cl.bovinoapp.rumboaustral.ui.map.MapScreen
import cl.bovinoapp.rumboaustral.ui.points.PointEditScreen
import cl.bovinoapp.rumboaustral.ui.points.PointsListScreen

private const val ROUTE_MAP = "mapa"
private const val ROUTE_POINTS = "puntos"
private const val ROUTE_DOWNLOADS = "descargas"
private const val ROUTE_POINT_EDIT = "punto/editar?pointId={pointId}&lat={lat}&lon={lon}"

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(ROUTE_MAP, "Mapa", Icons.Filled.Map),
    BottomTab(ROUTE_POINTS, "Puntos", Icons.Filled.Place),
    BottomTab(ROUTE_DOWNLOADS, "Mapas", Icons.Filled.CloudDownload)
)

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination
            NavigationBar {
                bottomTabs.forEach { tab ->
                    val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_MAP,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_MAP) {
                MapScreen(
                    container = container,
                    onAddPointAt = { lat, lon ->
                        navController.navigate("punto/editar?pointId=0&lat=$lat&lon=$lon")
                    },
                    onEditPoint = { id -> navController.navigate("punto/editar?pointId=$id&lat=0&lon=0") }
                )
            }
            composable(ROUTE_POINTS) {
                PointsListScreen(
                    container = container,
                    onEditPoint = { id -> navController.navigate("punto/editar?pointId=$id&lat=0&lon=0") },
                    onNavigateToPoint = { id ->
                        // La navegacion se muestra sobre el mapa: fija el destino y vuelve a esa pestana.
                        container.mapStateStore.navigationTargetId.value = id
                        navController.navigate(ROUTE_MAP) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAddPoint = { navController.navigate("punto/editar?pointId=0&lat=0&lon=0") }
                )
            }
            composable(ROUTE_DOWNLOADS) {
                DownloadsScreen(container = container)
            }
            composable(
                ROUTE_POINT_EDIT,
                arguments = listOf(
                    navArgument("pointId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("lat") { type = NavType.StringType; defaultValue = "0.0" },
                    navArgument("lon") { type = NavType.StringType; defaultValue = "0.0" }
                )
            ) { backStackEntry ->
                val args = backStackEntry.arguments
                PointEditScreen(
                    container = container,
                    pointId = args?.getLong("pointId") ?: 0L,
                    initialLatitude = args?.getString("lat")?.toDoubleOrNull() ?: 0.0,
                    initialLongitude = args?.getString("lon")?.toDoubleOrNull() ?: 0.0,
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}
