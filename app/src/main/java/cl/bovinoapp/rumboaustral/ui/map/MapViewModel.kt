package cl.bovinoapp.rumboaustral.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.bovinoapp.rumboaustral.data.Label
import cl.bovinoapp.rumboaustral.data.LabelKind
import cl.bovinoapp.rumboaustral.data.LabelsRepository
import cl.bovinoapp.rumboaustral.data.Point
import cl.bovinoapp.rumboaustral.data.PointFilter
import cl.bovinoapp.rumboaustral.data.PointFilterStore
import cl.bovinoapp.rumboaustral.data.PointsRepository
import cl.bovinoapp.rumboaustral.data.UNLABELED_COLOR
import cl.bovinoapp.rumboaustral.data.filteredBy
import cl.bovinoapp.rumboaustral.data.labelIdFor
import cl.bovinoapp.rumboaustral.location.LocationState
import cl.bovinoapp.rumboaustral.location.SatelliteStatus
import cl.bovinoapp.rumboaustral.location.LocationTracker
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Marcador listo para dibujar: posición y color (ARGB) según la categoría elegida en "Colorear por". */
data class MapMarker(val id: Long, val latitude: Double, val longitude: Double, val color: Int)

class MapViewModel(
    private val pointsRepository: PointsRepository,
    labelsRepository: LabelsRepository,
    private val filterStore: PointFilterStore,
    locationTracker: LocationTracker
) : ViewModel() {
    val location: StateFlow<LocationState?> = locationTracker.observeLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Satélites visibles y usados; null mientras no hay datos (o si el equipo no los expone). */
    val satellites: StateFlow<SatelliteStatus?> = locationTracker.observeSatellites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Todos los puntos, sin filtrar: sirven para resolver el destino y el punto seleccionado. */
    val points: StateFlow<List<Point>> = pointsRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val labels: StateFlow<List<Label>> = labelsRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Filtro vigente, sin etiquetas que ya no existen. */
    val filter: StateFlow<PointFilter> = combine(filterStore.filter, labelsRepository.observeAll()) { filter, labels ->
        filter.pruned(labels)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PointFilter())

    /** Marcadores de los puntos que pasan el filtro. Se recalcula fuera del hilo principal. */
    val markers: StateFlow<List<MapMarker>> = combine(
        pointsRepository.observeAll(),
        labelsRepository.observeAll(),
        filterStore.filter
    ) { points, labels, filter ->
        val colorBy = filter.colorBy
        val colorById = labels.associate { it.id to it.color }
        points.filteredBy(filter, labels).map { point ->
            MapMarker(
                id = point.id,
                latitude = point.latitude,
                longitude = point.longitude,
                color = point.labelIdFor(colorBy)?.let { colorById[it] } ?: UNLABELED_COLOR
            )
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFilter(kind: LabelKind, labelId: Long) = filterStore.toggle(kind, labelId)

    fun setColorBy(kind: LabelKind) = filterStore.setColorBy(kind)

    fun clearFilter() = filterStore.clear()

    fun deletePoint(point: Point) {
        viewModelScope.launch { pointsRepository.delete(point) }
    }

    fun saveNewPoint(
        name: String,
        latitude: Double,
        longitude: Double,
        notes: String = "",
        onError: (Throwable) -> Unit = {},
        onSaved: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = try {
                pointsRepository.save(Point(name = name, latitude = latitude, longitude = longitude, notes = notes))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
                return@launch
            }
            onSaved(id)
        }
    }

    suspend fun nextDefaultName(): String = pointsRepository.nextDefaultName()
}
