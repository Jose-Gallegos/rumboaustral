package cl.bovinoapp.rumboaustral.ui.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.bovinoapp.rumboaustral.data.Point
import cl.bovinoapp.rumboaustral.data.PointsRepository
import cl.bovinoapp.rumboaustral.geo.distanceMeters
import cl.bovinoapp.rumboaustral.gpx.GpxIo
import cl.bovinoapp.rumboaustral.location.LocationState
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PointListItem(val point: Point, val distanceMeters: Double?)

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(text: String) {
        query.value = text
    }

    fun updateLocation(location: LocationState) {
        lastLocation.value = location
    }

    fun savePoint(point: Point, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = if (point.id == 0L) repository.save(point) else {
                repository.update(point)
                point.id
            }
            onSaved(id)
        }
    }

    fun deletePoint(point: Point) {
        viewModelScope.launch { repository.delete(point) }
    }

    suspend fun pointById(id: Long): Point? = repository.getById(id)

    fun exportGpx(out: OutputStream) {
        viewModelScope.launch {
            GpxIo.export(items.value.map { it.point }, out)
        }
    }

    fun importGpx(input: InputStream) {
        viewModelScope.launch {
            repository.importAll(GpxIo.import(input))
        }
    }
}
