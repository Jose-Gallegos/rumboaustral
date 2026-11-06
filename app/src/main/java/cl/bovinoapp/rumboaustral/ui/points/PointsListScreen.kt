package cl.bovinoapp.rumboaustral.ui.points

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.bovinoapp.rumboaustral.AppContainer
import cl.bovinoapp.rumboaustral.geo.formatDistance
import cl.bovinoapp.rumboaustral.ui.labels.LabelsManagerSheet
import cl.bovinoapp.rumboaustral.ui.labels.LabelsViewModel
import cl.bovinoapp.rumboaustral.ui.labelsViewModelFactory
import cl.bovinoapp.rumboaustral.ui.pointsViewModelFactory

@Composable
fun PointsListScreen(
    container: AppContainer,
    onEditPoint: (Long) -> Unit,
    onNavigateToPoint: (Long) -> Unit,
    onAddPoint: () -> Unit
) {
    val viewModel: PointsViewModel = viewModel(factory = pointsViewModelFactory(container))
    val items by viewModel.items.collectAsState()
    var query by remember { mutableStateOf("") }

    val locationFlow = remember(container.locationTracker) { container.locationTracker.observeLocation() }
    val location by locationFlow.collectAsState(initial = null)
    LaunchedEffect(location) {
        location?.let { viewModel.updateLocation(it) }
    }

    val labelsViewModel: LabelsViewModel = viewModel(factory = labelsViewModelFactory(container))
    var showLabels by remember { mutableStateOf(false) }
    if (showLabels) LabelsManagerSheet(labelsViewModel, onDismiss = { showLabels = false })

    val context = LocalContext.current
    val importPreview by viewModel.importPreview.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val resolver = context.contentResolver
            val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment ?: "archivo"
            viewModel.loadImportPreview(fileName) { resolver.openInputStream(uri) }
        }
    }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? ->
        if (uri != null) viewModel.exportGpx { context.contentResolver.openOutputStream(uri, "wt") }
    }

    importPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            saving = saving,
            onToggle = viewModel::toggleImportCandidate,
            onSelect = viewModel::selectImportCandidates,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::dismissImportPreview
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPoint) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar punto")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setQuery(it)
                },
                label = { Text("Buscar punto") },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    enabled = !importing && !saving
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                    Text(if (importing) "Procesando…" else "Importar archivo", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = {
                        val date = java.time.LocalDate.now()
                        exportPicker.launch("rumbo-austral-$date.gpx")
                    },
                    enabled = !importing && !saving
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("Exportar GPX", modifier = Modifier.padding(start = 8.dp))
                }
            }
            OutlinedButton(
                onClick = { showLabels = true },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
                Text("Etiquetas", modifier = Modifier.padding(start = 8.dp))
            }
            LazyColumn {
                items(items, key = { it.point.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.point.name, style = MaterialTheme.typography.titleMedium)
                            val distanceText = item.distanceMeters?.let { formatDistance(it) } ?: "Distancia desconocida"
                            Text(distanceText, style = MaterialTheme.typography.bodyMedium)
                            if (item.point.notes.isNotBlank()) {
                                Text(item.point.notes, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { onNavigateToPoint(item.point.id) }) {
                                    Icon(Icons.Filled.Navigation, contentDescription = "Navegar")
                                }
                                IconButton(onClick = { onEditPoint(item.point.id) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Editar")
                                }
                                IconButton(onClick = { viewModel.deletePoint(item.point) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
