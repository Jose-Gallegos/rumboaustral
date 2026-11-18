package cl.bovinoapp.rumboaustral.ui.tracks

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.bovinoapp.rumboaustral.AppContainer
import cl.bovinoapp.rumboaustral.data.Track
import cl.bovinoapp.rumboaustral.location.TrackRecordingState
import cl.bovinoapp.rumboaustral.share.ShareFiles
import cl.bovinoapp.rumboaustral.ui.tracksViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksListScreen(container: AppContainer, onShowTrackOnMap: () -> Unit) {
    val viewModel: TracksViewModel = viewModel(factory = tracksViewModelFactory(container))
    val tracks by viewModel.tracks.collectAsState()
    val visibleTrackIds by container.mapStateStore.visibleTrackIds.collectAsState()
    val recordingSnapshot by TrackRecordingState.snapshot.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var trackToRename by remember { mutableStateOf<Track?>(null) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }

    trackToRename?.let { track ->
        var text by remember(track.id) { mutableStateOf(track.name) }
        AlertDialog(
            onDismissRequest = { trackToRename = null },
            title = { Text("Renombrar recorrido") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) viewModel.rename(track.id, text)
                    trackToRename = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { trackToRename = null }) { Text("Cancelar") } }
        )
    }

    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("Eliminar recorrido") },
            text = { Text("Se eliminará \"${track.name}\" y todos sus puntos. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(track)
                    trackToDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { trackToDelete = null }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Recorridos") }) }
    ) { innerPadding ->
        if (tracks.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                Text("Todavía no grabaste ningún recorrido. Usa el botón de grabar en el mapa para empezar.")
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            items(tracks, key = { it.id }) { track ->
                // Solo es "el" recorrido activo si el servicio esta grabando JUSTAMENTE este id. Un
                // endedAt nulo sin eso es un recorrido interrumpido (p.ej. el proceso murio mientras
                // grababa): no hay servicio escribiendole, asi que no hace falta bloquear su borrado.
                val isActivelyRecording = track.endedAt == null && recordingSnapshot?.trackId == track.id
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(track.name, style = MaterialTheme.typography.titleMedium)
                        Text(DATE_FORMAT.format(Date(track.startedAt)), style = MaterialTheme.typography.bodySmall)
                        when {
                            track.endedAt != null -> {
                                val durationMin = (track.endedAt - track.startedAt) / 60_000.0
                                Text(
                                    "%.1f km · %.0f min · %.1f km/h promedio".format(
                                        track.distanceMeters / 1000.0, durationMin, track.avgSpeedKmh
                                    )
                                )
                            }
                            isActivelyRecording -> Text("Grabando...", color = MaterialTheme.colorScheme.primary)
                            else -> Text(
                                "Interrumpido (sin estadísticas)",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            val isShown = track.id in visibleTrackIds
                            IconButton(
                                // El recorrido que se esta grabando ya se dibuja en vivo en el mapa.
                                enabled = !isActivelyRecording,
                                onClick = {
                                    // Operaciones explicitas (no alternar): un doble toque mientras se consultan los
                                    // puntos repite "mostrar" en vez de mostrar y volver a ocultar.
                                    if (isShown) {
                                        container.mapStateStore.hideTrack(track.id)
                                    } else scope.launch {
                                        // Sin ningun punto (se detuvo antes del primer fix) no hay nada que dibujar:
                                        // se avisa en vez de marcarlo visible y cambiar de pantalla.
                                        if (viewModel.pointsFor(track).isEmpty()) {
                                            Toast.makeText(
                                                context, "Este recorrido no tiene puntos para mostrar", Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            container.mapStateStore.showTrack(track.id)
                                            // Al mostrarlo se pasa al mapa, donde se encuadra.
                                            onShowTrackOnMap()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    if (isShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (isShown) "Ocultar del mapa" else "Ver en el mapa"
                                )
                            }
                            IconButton(onClick = { trackToRename = track }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Renombrar")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    val points = viewModel.pointsFor(track)
                                    ShareFiles.shareTrackGpx(context, track, points)
                                }
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Compartir")
                            }
                            IconButton(
                                // Mientras graba, el servicio sigue insertando TrackPoint con este trackId:
                                // borrarlo ahora dejaria esas inserciones apuntando a un recorrido inexistente.
                                enabled = !isActivelyRecording,
                                onClick = { trackToDelete = track }
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                            }
                        }
                    }
                }
            }
        }
    }
}
