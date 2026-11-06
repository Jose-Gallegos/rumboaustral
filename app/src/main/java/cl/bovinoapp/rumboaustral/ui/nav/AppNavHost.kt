package cl.bovinoapp.rumboaustral.ui.nav

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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

private const val EXIT_WINDOW_MILLIS = 2_000L

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(ROUTE_MAP, "Mapa", Icons.Filled.Map),
    BottomTab(ROUTE_POINTS, "Puntos", Icons.Filled.Place),
    BottomTab(ROUTE_DOWNLOADS, "Mapas", Icons.Filled.CloudDownload)
)

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    // Salir con doble Atras solo desde el mapa (raiz); en el resto Atras conserva su funcion habitual.
    val currentEntry by navController.currentBackStackEntryAsState()
    val context = LocalContext.current
    var lastBackMillis by remember { mutableLongStateOf(0L) }
    val exitToast = remember { mutableStateOf<Toast?>(null) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    BackHandler(enabled = currentEntry?.destination?.route == ROUTE_MAP) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackMillis <= EXIT_WINDOW_MILLIS) {
            handler.removeCallbacksAndMessages(null)
            exitToast.value?.cancel()
            context.findActivity()?.finish()
        } else {
            lastBackMillis = now
            exitToast.value?.cancel()
            val toast = Toast.makeText(context, "Presiona Atrás otra vez para salir", Toast.LENGTH_SHORT)
            exitToast.value = toast
            toast.show()
            // El toast dura mas que la ventana: se retira cuando esta termina, para que
            // lo visible coincida con el tiempo disponible para el segundo toque.
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ toast.cancel() }, EXIT_WINDOW_MILLIS)
        }
    }

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
                        container.mapStateStore.navigateToPoint(id)
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
                    onDone = { navController.popBackStack() },
                    onNavigate = { id, lat, lon ->
                        if (id != 0L) container.mapStateStore.navigateToPoint(id)
                        else container.mapStateStore.navigateToCoordinates(lat, lon)
                        // Retirar el formulario ANTES de guardar el estado de las pestanas;
                        // si no, al volver a Puntos se restauraria el formulario.
                        navController.popBackStack()
                        navController.navigate(ROUTE_MAP) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}
