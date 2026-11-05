package cl.bovinoapp.rumboaustral.ui.map

import androidx.compose.foundation.layout.Arrangement
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
import com.mapbox.maps.plugin.locationcomponent.location

private fun cameraAt(latitude: Double, longitude: Double) = CameraOptions.Builder()
    .center(GeoPoint.fromLngLat(longitude, latitude))
    .zoom(15.0)
    .build()

@Composable
fun MapScreen(
    container: AppContainer,
    onAddPointAt: (lat: Double, lon: Double) -> Unit
) {
    val viewModel: MapViewModel = viewModel(factory = mapViewModelFactory(container))
    val currentLocation by viewModel.location.collectAsState()
    val points by viewModel.points.collectAsState()

    val annotationManagerRef = remember { mutableStateOf<PointAnnotationManager?>(null) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val markerBitmap = remember { createPointMarkerBitmap() }

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
                    }
                    location.updateSettings { enabled = true }
                    annotationManagerRef.value = annotations.createPointAnnotationManager()
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
            },
            update = { mapView ->
                val manager = annotationManagerRef.value ?: mapView.annotations.createPointAnnotationManager().also {
                    annotationManagerRef.value = it
                }
                manager.deleteAll()
                points.forEach { point ->
                    manager.create(
                        PointAnnotationOptions()
                            .withPoint(GeoPoint.fromLngLat(point.longitude, point.latitude))
                            .withIconImage(markerBitmap)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        FloatingActionButton(
            onClick = {
                currentLocation?.let { onAddPointAt(it.latitude, it.longitude) }
            }
        ) {
            Icon(Icons.Filled.AddLocation, contentDescription = "Crear waypoint en mi ubicacion")
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
