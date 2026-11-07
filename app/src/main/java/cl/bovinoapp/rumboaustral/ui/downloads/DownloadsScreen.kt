package cl.bovinoapp.rumboaustral.ui.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.bovinoapp.rumboaustral.AppContainer
import cl.bovinoapp.rumboaustral.ui.downloadsViewModelFactory

/** Tamanos de lado disponibles para el area a descargar (km). */
private val REGION_SIDES_KM = listOf(5, 10, 20, 40)

private data class ZoomPreset(val label: String, val minZoom: Byte, val maxZoom: Byte)

private val ZOOM_PRESETS = listOf(
    ZoomPreset("Resumen (10-13)", 10, 13),
    ZoomPreset("Detalle (10-15)", 10, 15),
    ZoomPreset("Maximo (10-16)", 10, 16)
)

/**
 * El ID de la region es también donde guardamos el rango de zoom usado (Mapbox no lo expone al listar
 * regiones descargadas): "<nombre>__z<min>-<max>__<timestamp>". "__" en el nombre se reemplaza al generar el ID.
 */
private fun buildRegionId(name: String, minZoom: Byte, maxZoom: Byte): String =
    "${name.replace("__", "_")}__z${minZoom}-${maxZoom}__${System.currentTimeMillis()}"

private data class ParsedRegionId(val name: String, val zoomRange: String?)

private fun parseRegionId(id: String): ParsedRegionId {
    val parts = id.split("__")
    return if (parts.size >= 2 && parts[1].startsWith("z")) {
        ParsedRegionId(name = parts[0], zoomRange = parts[1].removePrefix("z"))
    } else {
        ParsedRegionId(name = id, zoomRange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(container: AppContainer) {
    val viewModel: DownloadsViewModel = viewModel(factory = downloadsViewModelFactory(container))
    val state by viewModel.state.collectAsState()
    val locationFlow = remember(container.locationTracker) { container.locationTracker.observeLocation() }
    val location by locationFlow.collectAsState(initial = null)

    var regionName by remember { mutableStateOf("") }
    var selectedSideKm by remember { mutableStateOf(10) }
    var selectedZoom by remember { mutableStateOf(ZOOM_PRESETS[1]) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mapas descargados") }) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }

            if (state.activeDownloadId != null) {
                Text("Descargando ${parseRegionId(state.activeDownloadId!!).name}...")
                LinearProgressIndicator(
                    progress = { state.activeDownloadFraction },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                Button(onClick = { viewModel.cancelDownload() }) {
                    Text("Cancelar descarga")
                }
            } else {
                OutlinedTextField(
                    value = regionName,
                    onValueChange = { regionName = it },
                    label = { Text("Nombre de la zona") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Tamano", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    REGION_SIDES_KM.forEach { km ->
                        FilterChip(
                            selected = selectedSideKm == km,
                            onClick = { selectedSideKm = km },
                            label = { Text("${km}x${km} km") }
                        )
                    }
                }
                Text("Nivel de detalle", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    ZOOM_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = selectedZoom == preset,
                            onClick = { selectedZoom = preset },
                            label = { Text(preset.label) }
                        )
                    }
                }
                Button(
                    onClick = {
                        val loc = location
                        if (loc != null) {
                            val name = regionName.ifBlank { "zona" }
                            val id = buildRegionId(name, selectedZoom.minZoom, selectedZoom.maxZoom)
                            viewModel.downloadRegion(
                                id, loc.latitude, loc.longitude,
                                sideMeters = selectedSideKm * 1_000.0,
                                minZoom = selectedZoom.minZoom,
                                maxZoom = selectedZoom.maxZoom
                            )
                        }
                    },
                    enabled = location != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text("Descargar alrededor de mi ubicacion")
                }
            }

            LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                items(state.regions, key = { it.id }) { region ->
                    val parsed = parseRegionId(region.id)
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(parsed.name, style = MaterialTheme.typography.titleMedium)
                            val sizeMb = region.completedResourceSize / (1024.0 * 1024.0)
                            val zoomText = parsed.zoomRange?.let { " · zoom $it" } ?: ""
                            Text("%.1f MB%s".format(sizeMb, zoomText))
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
