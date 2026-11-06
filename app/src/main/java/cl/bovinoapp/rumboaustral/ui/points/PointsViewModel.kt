package cl.bovinoapp.rumboaustral.ui.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.bovinoapp.rumboaustral.data.Point
import cl.bovinoapp.rumboaustral.data.PointsRepository
import cl.bovinoapp.rumboaustral.geo.distanceMeters
import cl.bovinoapp.rumboaustral.gpx.GpxIo
import cl.bovinoapp.rumboaustral.gpx.ImportFormatException
import cl.bovinoapp.rumboaustral.gpx.ImportPlan
import cl.bovinoapp.rumboaustral.gpx.ImportPlanner
import cl.bovinoapp.rumboaustral.gpx.ImportStatus
import cl.bovinoapp.rumboaustral.gpx.PointFileParser
import cl.bovinoapp.rumboaustral.location.LocationState
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PointListItem(val point: Point, val distanceMeters: Double?)

/** Vista previa de una importacion: [selected] son indices de [plan].candidates. */
data class ImportPreview(
    val fileName: String,
    val plan: ImportPlan,
    val selected: Set<Int>
)

class PointsViewModel(private val repository: PointsRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val lastLocation = MutableStateFlow<LocationState?>(null)

    val items: StateFlow<List<PointListItem>> = combine(
        repository.observeAll(),
        query,
        lastLocation
    ) { points, text, location ->
        points
            .filter { text.isBlank() || it.name.contains(text, ignoreCase = true) }
            .map { point ->
                val distance = location?.let {
                    distanceMeters(it.latitude, it.longitude, point.latitude, point.longitude)
                }
                PointListItem(point, distance)
            }
            .sortedWith(compareBy(nullsLast()) { it.distanceMeters })
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(text: String) {
        query.value = text
    }

    fun updateLocation(location: LocationState) {
        lastLocation.value = location
    }

    /**
     * Crea el punto (id == 0) o actualiza uno existente. Una actualización escribe nombre, coordenadas,
     * notas y etiquetas de [point], y conserva la fecha de creación. Quien llama debe pasar solo etiquetas
     * que existan (si no, la clave foránea rechaza el guardado y se informa por [onError]).
     */
    fun savePoint(point: Point, onError: (Throwable) -> Unit = {}, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = try {
                if (point.id == 0L) repository.save(point) else {
                    check(repository.updateDetails(point) > 0) { "El punto ya no existe" }
                    point.id
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
                return@launch
            }
            onSaved(id)
        }
    }

    fun deletePoint(point: Point) {
        viewModelScope.launch { repository.delete(point) }
    }

    suspend fun pointById(id: Long): Point? = repository.getById(id)

    /** Siguiente nombre libre con el formato "Waypoint N" (N = mayor sufijo existente + 1). */
    suspend fun nextDefaultName(): String {
        val regex = Regex("^Waypoint (\\d+)$", RegexOption.IGNORE_CASE)
        val max = repository.observeAll().first()
            .mapNotNull { regex.find(it.name.trim())?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "Waypoint ${max + 1}"
    }

    /** Respaldo GPX de TODOS los puntos (sin aplicar el filtro del buscador). */
    fun exportGpx(open: () -> OutputStream?) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val points = repository.observeAll().first()
                    val out = open() ?: throw java.io.IOException("No se pudo crear el archivo")
                    out.use { GpxIo.export(points, it) }
                    points.size
                }
                _message.value = when (count) {
                    0 -> "Respaldo creado (sin puntos)"
                    1 -> "1 punto exportado"
                    else -> "$count puntos exportados"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "No se pudo crear el respaldo"
            } finally {
                _importing.value = false
            }
        }
    }

    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    /** Mensaje de un solo uso para mostrar al usuario (la UI llama a [consumeMessage]). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    /** Lee el archivo y prepara la vista previa; no guarda nada todavia. */
    fun loadImportPreview(fileName: String, open: () -> InputStream?) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                val plan = withContext(Dispatchers.IO) {
                    val parsed = (open() ?: throw ImportFormatException("No se pudo abrir el archivo"))
                        .use { PointFileParser.parse(it) }
                    val existing = repository.observeAll().first()
                    ImportPlanner.plan(parsed, existing)
                }
                if (plan.candidates.isEmpty()) {
                    _message.value = if (plan.invalid.isEmpty()) "El archivo no contiene puntos"
                    else "No se encontraron puntos válidos (${plan.invalid.size} omitidos)"
                } else {
                    val preselected = plan.candidates.indices
                        .filter { plan.candidates[it].status == ImportStatus.NEW }.toSet()
                    _importPreview.value = ImportPreview(fileName, plan, preselected)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportFormatException) {
                _message.value = e.message
            } catch (e: Exception) {
                _message.value = "No se pudo leer el archivo"
            } finally {
                _importing.value = false
            }
        }
    }

    fun toggleImportCandidate(index: Int) {
        _importPreview.update { p ->
            p?.copy(selected = if (index in p.selected) p.selected - index else p.selected + index)
        }
    }

    fun selectImportCandidates(onlyNew: Boolean?) {
        _importPreview.update { p ->
            p?.copy(
                selected = when (onlyNew) {
                    null -> emptySet()
                    true -> p.plan.candidates.indices.filter { p.plan.candidates[it].status == ImportStatus.NEW }.toSet()
                    false -> p.plan.candidates.indices.toSet()
                }
            )
        }
    }

    fun dismissImportPreview() {
        _importPreview.value = null
    }

    fun confirmImport() {
        if (_saving.value) return
        val preview = _importPreview.value ?: return
        val points = preview.selected.sorted().map { preview.plan.candidates[it].toPoint() }
        if (points.isEmpty()) return
        _saving.value = true
        viewModelScope.launch {
            try {
                repository.importAll(points)
                _importPreview.value = null
                _message.value = if (points.size == 1) "1 punto importado" else "${points.size} puntos importados"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "No se pudieron guardar los puntos"
            } finally {
                _saving.value = false
            }
        }
    }
}
