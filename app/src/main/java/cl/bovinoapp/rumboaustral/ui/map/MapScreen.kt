package cl.bovinoapp.rumboaustral.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import cl.bovinoapp.rumboaustral.data.Point
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cl.bovinoapp.rumboaustral.ui.navigation.HeadingSource
import kotlinx.coroutines.awaitCancellation
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.ui.draw.rotate
import cl.bovinoapp.rumboaustral.ui.navigation.NavigationViewModel
import cl.bovinoapp.rumboaustral.ui.navigationViewModelFactory
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import cl.bovinoapp.rumboaustral.data.UNLABELED_COLOR
import com.mapbox.maps.QueriedRenderedFeature
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.ScreenBox
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.extension.style.layers.properties.generated.TextAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.TextJustify
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlinx.coroutines.suspendCancellableCoroutine
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.AnnotationSourceOptions
import com.mapbox.maps.plugin.annotation.ClusterOptions
import com.mapbox.maps.plugin.annotation.OnClusterClickListener
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cl.bovinoapp.rumboaustral.geo.distanceMeters
import cl.bovinoapp.rumboaustral.geo.formatDistance
import com.google.gson.JsonPrimitive
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.bovinoapp.rumboaustral.AppContainer
import cl.bovinoapp.rumboaustral.map.CameraSnapshot
import cl.bovinoapp.rumboaustral.map.LatLon
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import cl.bovinoapp.rumboaustral.ui.labels.PointFilterSheet
import cl.bovinoapp.rumboaustral.ui.mapViewModelFactory
import com.mapbox.geojson.Point as GeoPoint
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar

// Colores de los grupos de marcadores según cuántos puntos reúnen (grises azulados: no compiten con los de las etiquetas).
private val CLUSTER_COLOR_SMALL = 0xFF455A64.toInt()
private val CLUSTER_COLOR_MEDIUM = 0xFF37474F.toInt()
private val CLUSTER_COLOR_LARGE = 0xFF263238.toInt()

/** ID de la capa de símbolos de los marcadores de puntos (los grupos usan capas aparte, con otros IDs). */
private const val POINTS_LAYER_ID = "rumbo-points-layer"

/** Radio, en dp alrededor de la cruz central, dentro del cual un punto suelto se resalta. */
private const val CROSSHAIR_RADIUS_DP = 20f

/** Punto resaltado bajo la cruz. */
private data class CrosshairTarget(val pointId: Long)

/**
 * Punto suelto más cercano al centro de la vista (donde está la cruz) dentro de [radiusPx], o null. Consulta solo la
 * capa de marcadores individuales ([POINTS_LAYER_ID]): los grupos y los puntos ocultos por filtros no aparecen ahí.
 */
private suspend fun probeCrosshair(view: MapView, radiusPx: Float): CrosshairTarget? {
    if (view.width == 0 || view.height == 0) return null
    val map = view.mapboxMap
    val cx = view.width / 2.0
    val cy = view.height / 2.0
    val box = ScreenBox(ScreenCoordinate(cx - radiusPx, cy - radiusPx), ScreenCoordinate(cx + radiusPx, cy + radiusPx))
    val features = suspendCancellableCoroutine<List<QueriedRenderedFeature>> { continuation ->
        try {
            val handle = map.queryRenderedFeatures(
                RenderedQueryGeometry(box),
                RenderedQueryOptions(listOf(POINTS_LAYER_ID), null)
            ) { result -> if (continuation.isActive) continuation.resume(result.value.orEmpty()) }
            continuation.invokeOnCancellation { handle.cancel() }
        } catch (e: Exception) {
            // Capa aún no creada o estilo sin cargar: sin resaltado por ahora.
            if (continuation.isActive) continuation.resume(emptyList())
        }
    }
    val hits = features.mapNotNull { queried ->
        val feature = queried.queriedFeature.feature
        val geometry = feature.geometry() as? GeoPoint ?: return@mapNotNull null
        // "custom_data" es donde el plugin guarda el dato que se pasó con withData (aquí, el ID del punto).
        val id = feature.properties()?.get("custom_data")?.takeIf { it.isJsonPrimitive }?.asLong ?: return@mapNotNull null
        val pixel = map.pixelForCoordinate(geometry)
        id to hypot(pixel.x - cx, pixel.y - cy)
    }.filter { it.second <= radiusPx }.distinctBy { it.first }.sortedBy { it.second }
    return hits.firstOrNull()?.let { CrosshairTarget(it.first) }
}

private fun cameraAt(latitude: Double, longitude: Double) = CameraOptions.Builder()
    .center(GeoPoint.fromLngLat(longitude, latitude))
    .zoom(15.0)
    .build()

@Composable
fun MapScreen(
    container: AppContainer,
    onAddPointAt: (lat: Double, lon: Double) -> Unit,
    onEditPoint: (Long) -> Unit
) {
    val viewModel: MapViewModel = viewModel(factory = mapViewModelFactory(container))
    val currentLocation by viewModel.location.collectAsState()
    val satellites by viewModel.satellites.collectAsState()
    val points by viewModel.points.collectAsState()
    val markers by viewModel.markers.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val allLabels by viewModel.labels.collectAsState()
    val showFilters = remember { mutableStateOf(false) }

    // Navegacion hacia un waypoint: el destino vive en el store para poder fijarlo desde la lista de Puntos.
    val navViewModel: NavigationViewModel = viewModel(factory = navigationViewModelFactory(container))
    val navState by navViewModel.state.collectAsState()
    val targetId by container.mapStateStore.navigationTargetId.collectAsState()
    val temporaryDestination by container.mapStateStore.temporaryDestination.collectAsState()
    val targetPoint = remember(points, targetId) { points.firstOrNull { it.id == targetId } } ?: temporaryDestination
    val polylineManagerRef = remember { mutableStateOf<PolylineAnnotationManager?>(null) }

    // Waypoint tocado en el mapa; se resuelve contra la lista para reflejar ediciones y borrados.
    val selectedPointId = remember { mutableStateOf<Long?>(null) }
    val pointToDelete = remember { mutableStateOf<Point?>(null) }
    val selectedPoint = remember(points, selectedPointId.value) { points.firstOrNull { it.id == selectedPointId.value } }

    val annotationManagerRef = remember { mutableStateOf<PointAnnotationManager?>(null) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    // Un bitmap por color distinto (pocos: uno por etiqueta de la categoría elegida más el gris de "sin etiqueta").
    val markerBitmaps = remember { HashMap<Int, android.graphics.Bitmap>() }

    // Resaltado del punto suelto que pasa bajo la cruz central: se sondea al mover el mapa (cameraTick) y se dibuja
    // en un administrador propio, encima de los marcadores, para que los toques sigan llegando al punto de abajo.
    val cameraTick = remember { mutableIntStateOf(0) }
    val crosshairTarget = remember { mutableStateOf<CrosshairTarget?>(null) }
    val highlightManagerRef = remember { mutableStateOf<PointAnnotationManager?>(null) }
    val highlightBitmaps = remember { HashMap<Int, android.graphics.Bitmap>() }
    val density = LocalDensity.current.density

    val store = container.mapStateStore
    val map = mapViewRef.value?.mapboxMap

    // Sin camara guardada y sin GPS aun: usar la ultima ubicacion conocida del sistema
    // si nunca habiamos guardado una propia.
    LaunchedEffect(map) {
        if (map != null && store.camera == null && store.lastLocation == null) {
            val known = container.locationTracker.lastKnown()
            // Tras la espera pudo llegar un GPS en vivo (que ya guardo su posicion y centro el mapa):
            // en ese caso la ubicacion del sistema es mas antigua y se descarta. Ambos efectos corren
            // en el hilo principal, asi que esta comprobacion y la asignacion no se intercalan.
            if (known != null && store.lastLocation == null) {
                store.lastLocation = LatLon(known.latitude, known.longitude)
                if (store.autoCenterPending) {
                    map.setCamera(cameraAt(known.latitude, known.longitude))
                }
            }
        }
    }

    // Centrado automatico: una sola vez con el primer GPS, salvo que el usuario ya haya movido el mapa.
    LaunchedEffect(currentLocation, map) {
        val loc = currentLocation ?: return@LaunchedEffect
        store.lastLocation = LatLon(loc.latitude, loc.longitude)
        if (store.autoCenterPending && map != null) {
            map.setCamera(cameraAt(loc.latitude, loc.longitude))
            store.autoCenterPending = false
        }
    }

    LaunchedEffect(targetPoint) { navViewModel.setDestinationPoint(targetPoint) }

    // El GPS ya lo observa este mapa: se reutiliza en vez de abrir otra suscripcion.
    LaunchedEffect(currentLocation) { currentLocation?.let { navViewModel.updateLocation(it) } }

    // La brujula solo corre mientras hay un destino y la pantalla esta visible (STARTED).
    val lifecycleOwner = LocalLifecycleOwner.current
    val isNavigating = targetPoint != null
    LaunchedEffect(isNavigating, lifecycleOwner) {
        if (!isNavigating) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            navViewModel.startCompass()
            try {
                awaitCancellation()
            } finally {
                navViewModel.stopCompass()
            }
        }
    }

    // Linea recta entre mi ubicacion y el destino, redibujada con cada posicion GPS.
    LaunchedEffect(navState.location, targetPoint, polylineManagerRef.value) {
        val manager = polylineManagerRef.value ?: return@LaunchedEffect
        manager.deleteAll()
        val loc = navState.location
        if (loc != null && targetPoint != null) {
            manager.create(
                PolylineAnnotationOptions()
                    .withPoints(
                        listOf(
                            GeoPoint.fromLngLat(loc.longitude, loc.latitude),
                            GeoPoint.fromLngLat(targetPoint.longitude, targetPoint.latitude)
                        )
                    )
                    .withLineColor("#FFD600")
                    .withLineWidth(3.0)
            )
        }
    }

    // Al iniciar la navegacion, encuadra mi ubicacion y el destino (una vez por destino).
    LaunchedEffect(targetPoint, navState.location != null, map) {
        val loc = navState.location
        if (map != null && loc != null && targetPoint != null && store.fittedTargetId != targetPoint.id) {
            val camera = map.cameraForCoordinates(
                listOf(
                    GeoPoint.fromLngLat(loc.longitude, loc.latitude),
                    GeoPoint.fromLngLat(targetPoint.longitude, targetPoint.latitude)
                ),
                CameraOptions.Builder().build(),
                EdgeInsets(300.0, 150.0, 350.0, 150.0),
                17.0,
                null
            )
            map.flyTo(camera, mapAnimationOptions { duration(800) })
            store.fittedTargetId = targetPoint.id
            store.autoCenterPending = false
        }
    }

    // Mapbox fija las opciones de agrupamiento al crear el administrador de marcadores, así que al cambiar la
    // elección del usuario se quita el anterior y se crea uno nuevo; los marcadores se vuelven a dibujar solos
    // porque el efecto de más abajo depende del administrador.
    val clustering by store.clusteringEnabled.collectAsState()
    val mapView = mapViewRef.value
    LaunchedEffect(mapView, clustering) {
        val view = mapView ?: return@LaunchedEffect
        val plugin = view.annotations
        highlightManagerRef.value?.let { plugin.removeAnnotationManager(it) }
        highlightManagerRef.value = null
        annotationManagerRef.value?.let { plugin.removeAnnotationManager(it) }
        annotationManagerRef.value = null
        val manager = plugin.createPointAnnotationManager(
            AnnotationConfig(
                layerId = POINTS_LAYER_ID, // ID propio: el sondeo de la cruz consulta exactamente esta capa
                annotationSourceOptions = AnnotationSourceOptions(
                    clusterOptions = if (clustering) {
                        ClusterOptions(
                            cluster = true,
                            clusterRadius = 50L,
                            clusterMaxZoom = 16L, // a partir de ese zoom todos los puntos se ven sueltos
                            clusterMinPoints = 2L,
                            textColor = android.graphics.Color.WHITE,
                            textSize = 14.0,
                            colorLevels = listOf(0 to CLUSTER_COLOR_SMALL, 25 to CLUSTER_COLOR_MEDIUM, 100 to CLUSTER_COLOR_LARGE)
                        )
                    } else {
                        // Explícito: el valor por defecto de `cluster` en Mapbox es true, así que ClusterOptions()
                        // a secas agruparía igual.
                        ClusterOptions(cluster = false)
                    }
                )
            )
        )
        manager.addClickListener(OnPointAnnotationClickListener { annotation ->
            selectedPointId.value = annotation.getData()?.asLong
            true
        })
        // Tocar un grupo acerca el mapa a él para que se separe en grupos más chicos o puntos sueltos.
        manager.addClusterClickListener(OnClusterClickListener { cluster ->
            val center = cluster.originalFeature.geometry() as? GeoPoint
            val map = view.mapboxMap
            if (center != null) {
                map.flyTo(
                    CameraOptions.Builder().center(center).zoom(minOf(map.cameraState.zoom + 2.0, 19.0)).build(),
                    mapAnimationOptions { duration(500) }
                )
            }
            true
        })
        annotationManagerRef.value = manager

        // Capa de resaltado: se crea DESPUÉS de los marcadores para quedar encima, sin agrupar y sin colisiones
        // (el nombre debe verse aunque haya otros símbolos cerca). No tiene listeners: no intercepta toques.
        highlightManagerRef.value = plugin.createPointAnnotationManager().also { overlay ->
            overlay.iconAllowOverlap = true
            overlay.iconIgnorePlacement = true
            overlay.textAllowOverlap = true
            overlay.textIgnorePlacement = true
        }
    }

    // Sondeo de la cruz: pregunta a Mapbox qué marcadores SUELTOS hay dibujados bajo ella. Así coincide con lo que se
    // ve (filtros incluidos) y un punto dentro de un grupo no cuenta. snapshotFlow junta los cambios de cámara
    // intermedios: se procesa el último sin acumular consultas.
    LaunchedEffect(mapView, annotationManagerRef.value, markers) {
        val view = mapView ?: return@LaunchedEffect
        if (annotationManagerRef.value == null) return@LaunchedEffect
        snapshotFlow { cameraTick.intValue }.collect {
            crosshairTarget.value = probeCrosshair(view, CROSSHAIR_RADIUS_DP * density)
        }
    }

    // Texto del resaltado: "Nombre - 420 m" (solo el nombre si no hay GPS).
    val crosshairPoint = remember(crosshairTarget.value, points) {
        crosshairTarget.value?.let { target -> points.firstOrNull { it.id == target.pointId } }
    }
    val crosshairText: String? = selectedPoint?.let { point ->
        val distance = currentLocation?.let {
            formatDistance(distanceMeters(it.latitude, it.longitude, point.latitude, point.longitude))
        }
        if (distance != null) "${point.name} - $distance" else point.name
    }
    // Al llegar la cruz a un punto distinto, ese punto pasa a ser el seleccionado (como si se tocara). Un toque
    // posterior sobre otro waypoint lo reemplaza, y cerrar el panel funciona aunque la cruz siga encima.
    LaunchedEffect(crosshairPoint?.id) {
        crosshairPoint?.let { selectedPointId.value = it.id }
    }
    val highlightManager = highlightManagerRef.value
    // El resaltado sigue al punto seleccionado (bajo la cruz o tocado), igual que el panel.
    LaunchedEffect(selectedPoint?.id, crosshairText, highlightManager, markers) {
        val overlay = highlightManager ?: return@LaunchedEffect
        overlay.deleteAll()
        val point = selectedPoint ?: return@LaunchedEffect
        val color = markers.firstOrNull { it.id == point.id }?.color ?: UNLABELED_COLOR
        overlay.create(
            PointAnnotationOptions()
                .withPoint(GeoPoint.fromLngLat(point.longitude, point.latitude))
                .withIconImage(highlightBitmaps.getOrPut(color) { createHighlightBitmap(color) })
                .withTextField(crosshairText ?: point.name)
                .withTextAnchor(TextAnchor.LEFT)
                .withTextJustify(TextJustify.LEFT)
                .withTextOffset(listOf(2.3, 0.0))
                .withTextSize(14.0)
                .withTextColor("#FFFFFF")
                .withTextHaloColor("#000000")
                .withTextHaloWidth(2.0)
        )
    }

    // Los marcadores se recrean solo cuando cambian los puntos visibles o sus colores (no en cada fix de GPS ni
    // recomposicion), y en un unico lote: con cientos de waypoints hacerlo por punto es muy lento.
    val annotationManager = annotationManagerRef.value
    LaunchedEffect(markers, annotationManager) {
        val manager = annotationManager ?: return@LaunchedEffect
        manager.deleteAll()
        manager.create(
            markers.map { marker ->
                PointAnnotationOptions()
                    .withPoint(GeoPoint.fromLngLat(marker.longitude, marker.latitude))
                    .withIconImage(markerBitmaps.getOrPut(marker.color) { createPointMarkerBitmap(fillColor = marker.color) })
                    .withData(JsonPrimitive(marker.id))
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    mapViewRef.value = this
                    mapboxMap.loadStyle(Style.SATELLITE_STREETS)
                    val saved = store.camera
                    val last = store.lastLocation
                    mapboxMap.setCamera(
                        when {
                            saved != null -> CameraOptions.Builder()
                                .center(GeoPoint.fromLngLat(saved.longitude, saved.latitude))
                                .zoom(saved.zoom)
                                .build()
                            last != null -> cameraAt(last.latitude, last.longitude)
                            else -> CameraOptions.Builder()
                                .center(GeoPoint.fromLngLat(-72.65, -53.62))
                                .zoom(11.0)
                                .build()
                        }
                    )
                    mapboxMap.subscribeCameraChanged {
                        val c = mapboxMap.cameraState
                        store.camera = CameraSnapshot(c.center.latitude(), c.center.longitude(), c.zoom)
                        cameraTick.intValue++
                    }
                    // Al quedar quieto el mapa los marcadores ya están dibujados: sondeo final para asentar el resaltado.
                    mapboxMap.subscribeMapIdle { cameraTick.intValue++ }
                    // Punto de mi posición con una flecha que indica hacia dónde mira el teléfono (brújula).
                    location.updateSettings {
                        enabled = true
                        locationPuck = createDefault2DPuck(withBearing = true)
                        puckBearingEnabled = true
                        puckBearing = PuckBearing.HEADING
                    }
                    // Mapbox elige las unidades según la región del teléfono (aquí salía en ft y millas): siempre métrica.
                    scalebar.updateSettings { isMetricUnits = true }
                    // La linea se crea antes que los marcadores para que quede debajo de ellos.
                    polylineManagerRef.value = annotations.createPolylineAnnotationManager()
                    // El administrador de marcadores se crea aparte (según el agrupamiento elegido); al crearse
                    // después queda sobre la línea de navegación, igual que antes.
                    gestures.addOnMapClickListener(OnMapClickListener {
                        selectedPointId.value = null
                        false
                    })
                    gestures.addOnMoveListener(object : OnMoveListener {
                        override fun onMoveBegin(detector: MoveGestureDetector) {
                            store.autoCenterPending = false
                        }
                        override fun onMove(detector: MoveGestureDetector): Boolean = false
                        override fun onMoveEnd(detector: MoveGestureDetector) {}
                    })
                    gestures.addOnMapLongClickListener(OnMapLongClickListener { clicked ->
                        onAddPointAt(clicked.latitude(), clicked.longitude())
                        true
                    })
                }
            }
        )

        // Mira central muy delgada (blanca con borde oscuro para verse sobre cualquier imagen).
        // Marca el punto exacto donde se creara el waypoint; no intercepta toques.
        Canvas(modifier = Modifier.align(Alignment.Center).size(32.dp)) {
            val c = center
            val arm = size.width / 2f
            val thin = 1.dp.toPx()
            val outline = thin * 2.5f
            for ((stroke, color) in listOf(outline to Color.Black.copy(alpha = 0.6f), thin to Color.White)) {
                drawLine(color, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), strokeWidth = stroke)
                drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), strokeWidth = stroke)
            }
        }

        // Esquina superior izquierda, bajo la escala de Mapbox: estado del GPS y, debajo, el cuadro de navegación
        // (destino, distancia y flecha relativa a la orientación del teléfono). En una sola columna para que no se
        // pisen entre sí; con margen a la derecha para no tapar la brújula (arriba a la derecha), que permite volver
        // al norte al tocarla.
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(top = 34.dp, start = 12.dp, end = 72.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GpsStatusChip(location = currentLocation, satellites = satellites)
            targetPoint?.let { target ->
                Card {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp)
                    ) {
                        // Sin brujula ni curso GPS no hay orientacion: la flecha apuntaria a un rumbo falso.
                        if (navState.headingSource != HeadingSource.NONE) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = "Direccion hacia el destino",
                                modifier = Modifier.size(36.dp).rotate(navState.arrowRotationDegrees)
                            )
                        }
                        Column(modifier = Modifier.padding(start = 8.dp).widthIn(max = 170.dp)) {
                            Text(
                                target.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                navState.distanceMeters?.let { formatDistance(it) } ?: "-- m",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val warning = when {
                                navState.isLocationStale -> "GPS desactualizado"
                                navState.isLocationInaccurate -> "Precision GPS baja"
                                navState.headingSource == HeadingSource.NONE -> "Orientacion no disponible"
                                else -> null
                            }
                            warning?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = { store.stopNavigation() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Terminar navegacion")
                        }
                    }
                }
            }
        }

        if (showFilters.value) {
            PointFilterSheet(
                filter = filter,
                labels = allLabels,
                shown = markers.size,
                total = points.size,
                onToggle = viewModel::toggleFilter,
                onColorBy = viewModel::setColorBy,
                onClear = viewModel::clearFilter,
                onDismiss = { showFilters.value = false },
                clustering = clustering,
                onClusteringChange = store::setClustering
            )
        }

        pointToDelete.value?.let { point ->
            AlertDialog(
                onDismissRequest = { pointToDelete.value = null },
                title = { Text("Eliminar waypoint") },
                text = { Text("¿Eliminar \"${point.name}\"? Esta acción no se puede deshacer.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deletePoint(point)
                        selectedPointId.value = null
                        if (store.navigationTargetId.value == point.id) store.stopNavigation()
                        pointToDelete.value = null
                    }) { Text("Eliminar") }
                },
                dismissButton = {
                    TextButton(onClick = { pointToDelete.value = null }) { Text("Cancelar") }
                }
            )
        }

        selectedPoint?.let { point ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 24.dp, end = 104.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 4.dp)) {
                    Text(
                        point.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    currentLocation?.let {
                        Text(
                            formatDistance(distanceMeters(it.latitude, it.longitude, point.latitude, point.longitude)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (point.notes.isNotBlank()) {
                        // Vista previa acotada: las notas completas se ven en la edicion, y los
                        // botones siempre quedan visibles aunque el texto sea largo.
                        Text(
                            point.notes,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = {
                            store.navigateToPoint(point.id)
                            selectedPointId.value = null
                        }) {
                            Icon(Icons.Filled.Navigation, contentDescription = "Navegar")
                        }
                        IconButton(onClick = { onEditPoint(point.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = { pointToDelete.value = point }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                        }
                        IconButton(onClick = { selectedPointId.value = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        FloatingActionButton(onClick = { showFilters.value = true }) {
            BadgedBox(badge = { if (filter.isActive) Badge { Text("${filter.activeKinds}") } }) {
                Icon(Icons.Filled.FilterList, contentDescription = "Filtros y colores")
            }
        }

        FloatingActionButton(
            onClick = {
                mapViewRef.value?.mapboxMap?.cameraState?.center?.let {
                    onAddPointAt(it.latitude(), it.longitude())
                }
            }
        ) {
            Icon(Icons.Filled.AddLocation, contentDescription = "Crear waypoint en el centro del mapa")
        }

        FloatingActionButton(
            onClick = {
                val loc = currentLocation
                val map = mapViewRef.value?.mapboxMap
                if (loc != null && map != null) {
                    map.flyTo(
                        CameraOptions.Builder()
                            .center(GeoPoint.fromLngLat(loc.longitude, loc.latitude))
                            .zoom(maxOf(map.cameraState.zoom, 15.0))
                            .build(),
                        mapAnimationOptions { duration(800) }
                    )
                }
            }
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Centrar en mi ubicacion")
        }
        }
    }
}
