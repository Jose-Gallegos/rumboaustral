package cl.bovinoapp.rumboaustral.ui.points

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.material3.MaterialTheme
import cl.bovinoapp.rumboaustral.AppContainer
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import cl.bovinoapp.rumboaustral.data.LabelKind
import cl.bovinoapp.rumboaustral.data.Point
import cl.bovinoapp.rumboaustral.data.labelIdFor
import cl.bovinoapp.rumboaustral.data.withLabel
import cl.bovinoapp.rumboaustral.ui.labels.LabelSelector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import cl.bovinoapp.rumboaustral.geo.distanceMeters
import cl.bovinoapp.rumboaustral.geo.formatDistance
import cl.bovinoapp.rumboaustral.geo.isValidLatitude
import cl.bovinoapp.rumboaustral.geo.isValidLongitude
import cl.bovinoapp.rumboaustral.ui.labels.LabelsManagerSheet
import cl.bovinoapp.rumboaustral.ui.labels.LabelsViewModel
import cl.bovinoapp.rumboaustral.ui.labelsViewModelFactory
import cl.bovinoapp.rumboaustral.ui.pointsViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointEditScreen(
    container: AppContainer,
    pointId: Long,
    initialLatitude: Double,
    initialLongitude: Double,
    onDone: () -> Unit,
    /** pointId != 0: navegar al waypoint guardado; 0: navegar solo a las coordenadas. */
    onNavigate: (pointId: Long, lat: Double, lon: Double) -> Unit
) {
    val viewModel: PointsViewModel = viewModel(factory = pointsViewModelFactory(container))
    val labelsViewModel: LabelsViewModel = viewModel(factory = labelsViewModelFactory(container))
    val allLabels by labelsViewModel.labels.collectAsState()
    val labelsLoaded by labelsViewModel.loaded.collectAsState()
    // true entre cerrar el administrador y terminar de releer el punto de la base.
    var refreshingLabels by remember { mutableStateOf(false) }
    // true si la última relectura falló: la selección puede estar desactualizada y no se debe guardar.
    var labelsRefreshFailed by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var latitudeText by remember { mutableStateOf(initialLatitude.toString()) }
    var longitudeText by remember { mutableStateOf(initialLongitude.toString()) }
    var loadedExisting by remember { mutableStateOf(pointId == 0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val locationFlow = remember(container.locationTracker) { container.locationTracker.observeLocation() }
    val location by locationFlow.collectAsState(initial = null)
    var original by remember { mutableStateOf<Point?>(null) }
    var pendingNavigation by remember { mutableStateOf<Point?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Guardar antes de tener las etiquetas (o con la selección sin refrescar) las sobrescribiría con null.
    val canSave = labelsLoaded && !refreshingLabels && !labelsRefreshFailed && !saving

    // Etiqueta elegida por categoría y categorías que el usuario cambió a mano.
    val selection = remember { mutableStateMapOf<LabelKind, Long?>() }
    var touched by remember { mutableStateOf(emptySet<LabelKind>()) }
    // Una etiqueta borrada desde el administrador deja de contar: se guarda como "sin etiqueta" en vez de
    // fallar por la clave foránea.
    fun effectiveLabelId(kind: LabelKind): Long? = selection[kind]?.takeIf { id -> allLabels.any { it.id == id } }
    fun withSelectedLabels(point: Point): Point =
        LabelKind.entries.fold(point) { p, kind -> p.withLabel(kind, effectiveLabelId(kind)) }

    // El administrador puede haber reemplazado etiquetas en este punto: lo que el usuario no tocó se vuelve
    // a leer de la base para no pisar el reemplazo con un valor viejo. Si la lectura falla, el guardado
    // sigue bloqueado (labelsRefreshFailed) hasta que un reintento funcione.
    fun refreshLabelsFromDb() {
        refreshingLabels = true
        scope.launch {
            try {
                viewModel.pointById(pointId)?.let { fresh ->
                    original = fresh
                    LabelKind.entries.filter { it !in touched }.forEach { selection[it] = fresh.labelIdFor(it) }
                }
                labelsRefreshFailed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                labelsRefreshFailed = true
            } finally {
                refreshingLabels = false
            }
        }
    }

    // Panel inferior: no navega, así que no se pierde lo escrito en el formulario.
    if (showLabels) {
        LabelsManagerSheet(labelsViewModel, onDismiss = {
            showLabels = false
            if (pointId != 0L) refreshLabelsFromDb()
        })
    }

    LaunchedEffect(pointId) {
        if (pointId == 0L) {
            val defaultName = viewModel.nextDefaultName()
            // Si el usuario ya escribio algo mientras se consultaba la base, no lo pisamos.
            if (!nameEdited) name = defaultName
        } else {
            viewModel.pointById(pointId)?.let { point ->
                name = point.name
                notes = point.notes
                latitudeText = point.latitude.toString()
                longitudeText = point.longitude.toString()
                LabelKind.entries.forEach { selection[it] = point.labelIdFor(it) }
                original = point
            }
            loadedExisting = true
        }
    }

    pendingNavigation?.let { edited ->
        AlertDialog(
            onDismissRequest = { if (!saving) pendingNavigation = null },
            title = { Text("Guardar cambios") },
            text = { Text("Hay cambios sin guardar. ¿Guardarlos y navegar al waypoint?") },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    viewModel.savePoint(
                        edited,
                        onError = {
                            saving = false
                            pendingNavigation = null
                            errorMessage = "No se pudo guardar; no se inició la navegación"
                        }
                    ) { id ->
                        saving = false
                        pendingNavigation = null
                        onNavigate(id, edited.latitude, edited.longitude)
                    }
                }) { Text("Guardar y navegar") }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { pendingNavigation = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (pointId == 0L) "Nuevo punto" else "Editar punto") }) }
    ) { innerPadding ->
        if (!loadedExisting) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameEdited = true },
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
            LabelKind.entries.forEach { kind ->
                LabelSelector(
                    kind = kind,
                    labels = allLabels.filter { it.kind == kind },
                    selectedId = effectiveLabelId(kind),
                    onSelect = { id ->
                        selection[kind] = id
                        touched = touched + kind
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            TextButton(onClick = { showLabels = true }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Administrar etiquetas")
            }
            if (labelsRefreshFailed) {
                Text(
                    "No se pudieron actualizar las etiquetas del punto; no se puede guardar hasta reintentar.",
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(enabled = !refreshingLabels, onClick = { refreshLabelsFromDb() }) { Text("Reintentar") }
            }
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
                            withSelectedLabels(
                                Point(id = pointId, name = name, latitude = lat, longitude = lon, notes = notes)
                            ),
                            onError = { errorMessage = "No se pudo guardar el punto" }
                        ) { onDone() }
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("Guardar")
            }
            OutlinedButton(
                onClick = {
                    val lat = latitudeText.toDoubleOrNull()
                    val lon = longitudeText.toDoubleOrNull()
                    errorMessage = when {
                        lat == null || !isValidLatitude(lat) -> "La latitud debe ser un numero entre -90 y 90"
                        lon == null || !isValidLongitude(lon) -> "La longitud debe ser un numero entre -180 y 180"
                        else -> null
                    }
                    if (errorMessage != null || lat == null || lon == null) return@OutlinedButton
                    val saved = original
                    when {
                        pointId == 0L || saved == null -> onNavigate(0L, lat, lon)
                        saved.name == name && saved.notes == notes &&
                            saved.latitude == lat && saved.longitude == lon &&
                            LabelKind.entries.all { saved.labelIdFor(it) == effectiveLabelId(it) } ->
                            onNavigate(pointId, lat, lon)
                        name.isBlank() -> errorMessage = "Ingresa un nombre para guardar los cambios"
                        else -> pendingNavigation = withSelectedLabels(
                            saved.copy(name = name, latitude = lat, longitude = lon, notes = notes)
                        )
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                val lat = latitudeText.toDoubleOrNull()
                val lon = longitudeText.toDoubleOrNull()
                val loc = location
                val distance = when {
                    lat == null || lon == null || !isValidLatitude(lat) || !isValidLongitude(lon) -> null
                    loc == null -> "--"
                    else -> formatDistance(distanceMeters(loc.latitude, loc.longitude, lat, lon))
                }
                Text(if (distance != null) "Navegar · $distance" else "Navegar")
            }
        }
    }
}
