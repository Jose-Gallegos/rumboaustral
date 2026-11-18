package cl.bovinoapp.rumboaustral.ui.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.bovinoapp.rumboaustral.data.AutoClassifier
import cl.bovinoapp.rumboaustral.data.AutoClassifyPlan
import cl.bovinoapp.rumboaustral.data.Label
import cl.bovinoapp.rumboaustral.data.LabelImport
import cl.bovinoapp.rumboaustral.data.LabelKind
import cl.bovinoapp.rumboaustral.data.LabelsRepository
import cl.bovinoapp.rumboaustral.data.Point
import cl.bovinoapp.rumboaustral.data.PointFilter
import cl.bovinoapp.rumboaustral.data.PointFilterStore
import cl.bovinoapp.rumboaustral.data.PointsRepository
import cl.bovinoapp.rumboaustral.data.filteredBy
import cl.bovinoapp.rumboaustral.data.labelIdFor
import kotlinx.coroutines.flow.map
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

/** [labels] son las etiquetas del punto (Tipo, Estado, Zona) en ese orden, sin las categorías sin asignar. */
data class PointListItem(val point: Point, val distanceMeters: Double?, val labels: List<Label> = emptyList())

/** Vista previa de una importacion: [selected] son indices de [plan].candidates. */
data class ImportPreview(
    val fileName: String,
    val plan: ImportPlan,
    val selected: Set<Int>,
    /** Etiquetas que ya existían al leer el archivo; sirven para saber cuáles se crearían. */
    val existingLabels: List<Label> = emptyList()
)

class PointsViewModel(
    private val repository: PointsRepository,
    private val labelsRepository: LabelsRepository,
    private val filterStore: PointFilterStore
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val lastLocation = MutableStateFlow<LocationState?>(null)

    val items: StateFlow<List<PointListItem>> = combine(
        repository.observeAll(),
        query,
        lastLocation,
        labelsRepository.observeAll(),
        filterStore.filter
    ) { points, text, location, labels, filter ->
        val labelsById = labels.associateBy { it.id }
        points
            .filteredBy(filter, labels)
            .filter { text.isBlank() || it.name.contains(text, ignoreCase = true) }
            .map { point ->
                val distance = location?.let {
                    distanceMeters(it.latitude, it.longitude, point.latitude, point.longitude)
                }
                val pointLabels = LabelKind.entries.mapNotNull { kind -> point.labelIdFor(kind)?.let { labelsById[it] } }
                PointListItem(point, distance, pointLabels)
            }
            .sortedWith(compareBy(nullsLast()) { it.distanceMeters })
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cantidad total de puntos, sin filtros ni búsqueda, para mostrar "X de Y". */
    val totalCount: StateFlow<Int> = repository.observeAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val labels: StateFlow<List<Label>> = labelsRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Filtro vigente, sin etiquetas que ya no existen. */
    val filter: StateFlow<PointFilter> = combine(filterStore.filter, labelsRepository.observeAll()) { filter, labels ->
        filter.pruned(labels)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PointFilter())

    fun toggleFilter(kind: LabelKind, labelId: Long) = filterStore.toggle(kind, labelId)

    fun setColorBy(kind: LabelKind) = filterStore.setColorBy(kind)

    fun clearFilter() = filterStore.clear()

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

    suspend fun nextDefaultName(): String = repository.nextDefaultName()

    /** Respaldo GPX de TODOS los puntos (sin aplicar el filtro del buscador). */
    fun exportGpx(open: () -> OutputStream?) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val points = repository.observeAll().first()
                    val labels = labelsRepository.observeAll().first()
                    val out = open() ?: throw java.io.IOException("No se pudo crear el archivo")
                    out.use { GpxIo.export(points, it, labels) }
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
                val (plan, existingLabels) = withContext(Dispatchers.IO) {
                    val parsed = (open() ?: throw ImportFormatException("No se pudo abrir el archivo"))
                        .use { PointFileParser.parse(it) }
                    val existing = repository.observeAll().first()
                    ImportPlanner.plan(parsed, existing) to labelsRepository.observeAll().first()
                }
                if (plan.candidates.isEmpty()) {
                    _message.value = if (plan.invalid.isEmpty()) "El archivo no contiene puntos"
                    else "No se encontraron puntos válidos (${plan.invalid.size} omitidos)"
                } else {
                    val preselected = plan.candidates.indices
                        .filter { plan.candidates[it].status == ImportStatus.NEW }.toSet()
                    _importPreview.value = ImportPreview(fileName, plan, preselected, existingLabels)
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

    // --- Selección y asignación masiva de etiquetas ---

    private val rawSelection = MutableStateFlow<Set<Long>>(emptySet())

    /** Puntos seleccionados; descarta los que ya no existen (por ejemplo, borrados mientras se seleccionaba). */
    val selected: StateFlow<Set<Long>> = combine(rawSelection, repository.observeAll()) { selection, points ->
        if (selection.isEmpty()) selection else selection intersect points.mapTo(HashSet()) { it.id }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleSelected(id: Long) {
        rawSelection.update { if (id in it) it - id else it + id }
    }

    /** Selecciona todos los puntos que muestra la lista ahora (con filtros y búsqueda aplicados). */
    fun selectAllVisible() {
        rawSelection.value = items.value.mapTo(HashSet()) { it.point.id }
    }

    fun clearSelection() {
        rawSelection.value = emptySet()
    }

    private val _bulkBusy = MutableStateFlow(false)
    /** true mientras corre una asignación masiva o una autoclasificación. */
    val bulkBusy: StateFlow<Boolean> = _bulkBusy

    /**
     * Asigna [labelId] (null = quitar etiqueta) en la categoría [kind] a los puntos seleccionados.
     * [onSuccess] solo se llama si se aplicó; si falla se informa por [message] y no se cierra nada.
     */
    fun assignLabelToSelected(kind: LabelKind, labelId: Long?, onSuccess: () -> Unit) {
        val ids = selected.value
        if (ids.isEmpty() || _bulkBusy.value) return
        _bulkBusy.value = true
        viewModelScope.launch {
            try {
                val changed = repository.assignLabel(kind, ids, labelId)
                if (changed == null) {
                    _message.value = "No se pudo asignar: la etiqueta ya no existe"
                } else {
                    _message.value = if (changed == 1) "Etiqueta asignada a 1 punto" else "Etiqueta asignada a $changed puntos"
                    onSuccess()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "No se pudo asignar la etiqueta"
            } finally {
                _bulkBusy.value = false
            }
        }
    }

    // --- Autoclasificación por nombre ---

    private val _autoPlan = MutableStateFlow<AutoClassifyPlan?>(null)
    /** Plan propuesto, pendiente de confirmar (null = sin plan). */
    val autoPlan: StateFlow<AutoClassifyPlan?> = _autoPlan

    /** Calcula qué etiquetas se asignarían según el nombre de cada punto; no guarda nada todavía. */
    fun prepareAutoClassify() {
        if (_bulkBusy.value) return
        _bulkBusy.value = true
        viewModelScope.launch {
            try {
                val plan = withContext(Dispatchers.Default) {
                    AutoClassifier.plan(repository.observeAll().first(), labelsRepository.observeAll().first())
                }
                if (plan.assignmentCount == 0 && plan.ambiguous == 0) {
                    _message.value = "Ningún punto sin etiqueta tiene un nombre que coincida con una etiqueta"
                } else {
                    _autoPlan.value = plan
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "No se pudo preparar la autoclasificación"
            } finally {
                _bulkBusy.value = false
            }
        }
    }

    fun dismissAutoClassify() {
        if (!_bulkBusy.value) _autoPlan.value = null
    }

    fun confirmAutoClassify() {
        val plan = _autoPlan.value ?: return
        if (_bulkBusy.value) return
        _bulkBusy.value = true
        viewModelScope.launch {
            try {
                val byKind = plan.perKind
                    .filter { it.assignments.isNotEmpty() }
                    .associate { kindPlan -> kindPlan.kind to kindPlan.assignments.mapKeys { it.key.id } }
                val changed = repository.applyAutoClassification(byKind)
                _autoPlan.value = null
                // [changed] cuenta etiquetas asignadas (un punto puede recibir una por categoría), no puntos.
                _message.value = if (changed == 1) "1 etiqueta asignada" else "$changed etiquetas asignadas"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Es todo o nada: no quedó nada aplicado. El plan sigue abierto para reintentar o cancelar.
                _message.value = "No se pudo clasificar; no se cambió ningún punto"
            } finally {
                _bulkBusy.value = false
            }
        }
    }

    fun confirmImport() {
        if (_saving.value) return
        val preview = _importPreview.value ?: return
        val entries = preview.selected.sorted().map { preview.plan.candidates[it].let { c -> c.toPoint() to c.labels } }
        if (entries.isEmpty()) return
        // Las etiquetas nuevas se calculan con las que existían al abrir la vista previa; es solo para el aviso.
        val newLabelCount = LabelImport.missing(entries.flatMap { it.second }, preview.existingLabels).size
        _saving.value = true
        viewModelScope.launch {
            try {
                repository.importWithLabels(entries)
                _importPreview.value = null
                val pointsText = if (entries.size == 1) "1 punto importado" else "${entries.size} puntos importados"
                _message.value = when (newLabelCount) {
                    0 -> pointsText
                    1 -> "$pointsText y 1 etiqueta nueva"
                    else -> "$pointsText y $newLabelCount etiquetas nuevas"
                }
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
