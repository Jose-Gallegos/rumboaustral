package cl.bovinoapp.rumboaustral.ui.points

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
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.bovinoapp.rumboaustral.AppContainer
import cl.bovinoapp.rumboaustral.geo.formatDistance
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

    Scaffold(
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
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
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
