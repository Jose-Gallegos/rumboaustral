package cl.bovinoapp.rumboaustral.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.bovinoapp.rumboaustral.AppContainer
import cl.bovinoapp.rumboaustral.geo.formatBearing
import cl.bovinoapp.rumboaustral.geo.formatDistance
import cl.bovinoapp.rumboaustral.ui.navigationViewModelFactory

@Composable
fun NavigationScreen(container: AppContainer, pointId: Long) {
    val viewModel: NavigationViewModel = viewModel(factory = navigationViewModelFactory(container))
    val state by viewModel.state.collectAsState()

    LaunchedEffect(pointId) { viewModel.setDestination(pointId) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                state.destination?.name ?: "Selecciona un punto",
                style = MaterialTheme.typography.headlineSmall
            )

            Icon(
                Icons.Filled.ArrowUpward,
                contentDescription = "Direccion hacia el punto",
                modifier = Modifier
                    .padding(vertical = 24.dp)
                    .rotate(state.arrowRotationDegrees)
                    .size(120.dp)
            )

            Text(
                state.distanceMeters?.let { formatDistance(it) } ?: "-- m",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                state.bearingToTargetDegrees?.let { "Rumbo " + formatBearing(it) } ?: "Rumbo --",
                style = MaterialTheme.typography.headlineMedium
            )

            val sourceLabel = when (state.headingSource) {
                HeadingSource.DEVICE_COMPASS -> "Orientacion: brujula del telefono"
                HeadingSource.GPS_COURSE -> "Orientacion: curso GPS (en movimiento)"
                HeadingSource.NONE -> "Orientacion no disponible"
            }
            Text(sourceLabel, style = MaterialTheme.typography.bodyMedium)

            if (state.isLocationStale) {
                Card(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        "La ubicacion GPS esta desactualizada",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (state.isLocationInaccurate) {
                Card(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        "Precision GPS baja (" + "%.0f".format(state.location?.accuracyMeters ?: 0f) + " m)",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
