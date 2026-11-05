package cl.bovinoapp.rumboaustral.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import cl.bovinoapp.rumboaustral.ui.mapViewModelFactory
import com.mapbox.geojson.Point as GeoPoint
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location

@Composable
fun MapScreen(
    container: AppContainer,
    onAddPointAt: (lat: Double, lon: Double) -> Unit
) {
    val viewModel: MapViewModel = viewModel(factory = mapViewModelFactory(container))
    val currentLocation by viewModel.location.collectAsState()
    val points by viewModel.points.collectAsState()

    val annotationManagerRef = remember { mutableStateOf<PointAnnotationManager?>(null) }
    val markerBitmap = remember { createPointMarkerBitmap() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    mapboxMap.loadStyle(Style.SATELLITE_STREETS)
                    mapboxMap.setCamera(
                        CameraOptions.Builder()
                            .center(GeoPoint.fromLngLat(-72.65, -53.62))
                            .zoom(11.0)
                            .build()
                    )
                    location.updateSettings { enabled = true }
                    annotationManagerRef.value = annotations.createPointAnnotationManager()
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

        FloatingActionButton(
            onClick = {
                currentLocation?.let { onAddPointAt(it.latitude, it.longitude) }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Guardar ubicacion actual")
        }
    }
}
