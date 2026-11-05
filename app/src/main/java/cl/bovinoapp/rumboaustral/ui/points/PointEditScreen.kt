package cl.bovinoapp.rumboaustral.ui.points

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.MaterialTheme
import cl.bovinoapp.rumboaustral.AppContainer
import cl.bovinoapp.rumboaustral.data.Point
import cl.bovinoapp.rumboaustral.geo.isValidLatitude
import cl.bovinoapp.rumboaustral.geo.isValidLongitude
import cl.bovinoapp.rumboaustral.ui.pointsViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointEditScreen(
    container: AppContainer,
    pointId: Long,
    initialLatitude: Double,
    initialLongitude: Double,
    onDone: () -> Unit
) {
    val viewModel: PointsViewModel = viewModel(factory = pointsViewModelFactory(container))

    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var latitudeText by remember { mutableStateOf(initialLatitude.toString()) }
    var longitudeText by remember { mutableStateOf(initialLongitude.toString()) }
    var loadedExisting by remember { mutableStateOf(pointId == 0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pointId) {
        if (pointId != 0L) {
            viewModel.pointById(pointId)?.let { point ->
                name = point.name
                notes = point.notes
                latitudeText = point.latitude.toString()
                longitudeText = point.longitude.toString()
            }
            loadedExisting = true
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (pointId == 0L) "Nuevo punto" else "Editar punto") }) }
    ) { innerPadding ->
        if (!loadedExisting) return@Scaffold
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = latitudeText,
                onValueChange = { latitudeText = it },
                label = { Text("Latitud") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = longitudeText,
                onValueChange = { longitudeText = it },
                label = { Text("Longitud") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Button(
                onClick = {
                    val lat = latitudeText.toDoubleOrNull()
                    val lon = longitudeText.toDoubleOrNull()
                    errorMessage = when {
                        name.isBlank() -> "Ingresa un nombre"
                        lat == null || !isValidLatitude(lat) -> "La latitud debe ser un numero entre -90 y 90"
                        lon == null || !isValidLongitude(lon) -> "La longitud debe ser un numero entre -180 y 180"
                        else -> null
                    }
                    if (errorMessage == null && lat != null && lon != null) {
                        viewModel.savePoint(
                            Point(id = pointId, name = name, latitude = lat, longitude = lon, notes = notes)
                        ) { onDone() }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("Guardar")
            }
        }
    }
}
