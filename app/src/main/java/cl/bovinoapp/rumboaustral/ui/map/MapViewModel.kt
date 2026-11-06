package cl.bovinoapp.rumboaustral.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.bovinoapp.rumboaustral.data.Point
import cl.bovinoapp.rumboaustral.data.PointsRepository
import cl.bovinoapp.rumboaustral.location.LocationState
import cl.bovinoapp.rumboaustral.location.LocationTracker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(
    private val pointsRepository: PointsRepository,
    locationTracker: LocationTracker
) : ViewModel() {
    val location: StateFlow<LocationState?> = locationTracker.observeLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val points: StateFlow<List<Point>> = pointsRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deletePoint(point: Point) {
        viewModelScope.launch { pointsRepository.delete(point) }
    }

    fun saveNewPoint(name: String, latitude: Double, longitude: Double, notes: String = "") {
        viewModelScope.launch {
            pointsRepository.save(Point(name = name, latitude = latitude, longitude = longitude, notes = notes))
        }
    }
}
