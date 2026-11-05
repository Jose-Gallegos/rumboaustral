package cl.bovinoapp.rumboaustral.ui.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.bovinoapp.rumboaustral.AppContainer
import cl.bovinoapp.rumboaustral.ui.downloadsViewModelFactory

/** Tamano del lado del area descargada para la primera version: una grilla de ~10x10 km. */
private const val DEFAULT_REGION_SIDE_METERS = 10_000.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(container: AppContainer) {
    val viewModel: DownloadsViewModel = viewModel(factory = downloadsViewModelFactory(container))
    val state by viewModel.state.collectAsState()
    val locationFlow = remember(container.locationTracker) { container.locationTracker.observeLocation() }
    val location by locationFlow.collectAsState(initial = null)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mapas descargados") }) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }

            if (state.activeDownloadId != null) {
                Text("Descargando ${state.activeDownloadId}...")
                LinearProgressIndicator(
                    progress = { state.activeDownloadFraction },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                Button(onClick = { viewModel.cancelDownload() }) {
                    Text("Cancelar descarga")
                }
            } else {
                Button(
                    onClick = {
                        val loc = location
                        if (loc != null) {
                            val id = "zona-${System.currentTimeMillis()}"
                            viewModel.downloadRegion(id, loc.latitude, loc.longitude, DEFAULT_REGION_SIDE_METERS)
                        }
                    },
                    enabled = location != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Descargar 10x10 km alrededor de mi ubicacion")
                }
            }

            LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                items(state.regions, key = { it.id }) { region ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(region.id, style = MaterialTheme.typography.titleMedium)
                            val sizeMb = region.completedResourceSize / (1024.0 * 1024.0)
                            Text("%.1f MB".format(sizeMb))
                            if (!region.isComplete) {
                                Text(
                                    "Descarga incompleta",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = { viewModel.deleteRegion(region.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                            }
                        }
                    }
                }
            }
        }
    }
}
