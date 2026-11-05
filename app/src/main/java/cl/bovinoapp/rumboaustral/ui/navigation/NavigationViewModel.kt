package cl.bovinoapp.rumboaustral.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.bovinoapp.rumboaustral.data.Point
import cl.bovinoapp.rumboaustral.data.PointsRepository
import cl.bovinoapp.rumboaustral.geo.angularDifferenceDegrees
import cl.bovinoapp.rumboaustral.geo.distanceMeters
import cl.bovinoapp.rumboaustral.geo.initialBearingDegrees
import cl.bovinoapp.rumboaustral.location.CompassTracker
import cl.bovinoapp.rumboaustral.location.LOCATION_LOW_ACCURACY_METERS
import cl.bovinoapp.rumboaustral.location.LOCATION_STALE_AFTER_MILLIS
import cl.bovinoapp.rumboaustral.location.LocationState
import cl.bovinoapp.rumboaustral.location.LocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class NavigationUiState(
    val destination: Point? = null,
    val location: LocationState? = null,
    val deviceAzimuthDegrees: Float? = null,
    val distanceMeters: Double? = null,
    val bearingToTargetDegrees: Double? = null,
    /** Angulo que debe girar la flecha en pantalla respecto a "arriba". */
    val arrowRotationDegrees: Float = 0f,
    val isLocationStale: Boolean = false,
    val isLocationInaccurate: Boolean = false,
    val headingSource: HeadingSource = HeadingSource.NONE
)

enum class HeadingSource { DEVICE_COMPASS, GPS_COURSE, NONE }

class NavigationViewModel(
    private val pointsRepository: PointsRepository,
    private val locationTracker: LocationTracker,
    private val compassTracker: CompassTracker
) : ViewModel() {
    private val _state = MutableStateFlow(NavigationUiState())
    val state: StateFlow<NavigationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            locationTracker.observeLocation().collect { location ->
                _state.value = recompute(_state.value.copy(location = location))
            }
        }
        viewModelScope.launch {
            compassTracker.observeAzimuthDegrees()
                .catch { /* Sin sensor de orientacion: se usa el curso GPS como respaldo. */ }
                .collect { azimuth ->
                    _state.value = recompute(_state.value.copy(deviceAzimuthDegrees = azimuth))
                }
        }
    }

    fun setDestination(pointId: Long) {
        viewModelScope.launch {
            val point = pointsRepository.getById(pointId)
            _state.value = recompute(_state.value.copy(destination = point))
        }
    }

    private fun recompute(state: NavigationUiState): NavigationUiState {
        val destination = state.destination
        val location = state.location
        if (destination == null || location == null) {
            return state.copy(
                distanceMeters = null,
                bearingToTargetDegrees = null,
                arrowRotationDegrees = 0f
            )
        }
        val distance = distanceMeters(
            location.latitude, location.longitude,
            destination.latitude, destination.longitude
        )
        val bearingToTarget = initialBearingDegrees(
            location.latitude, location.longitude,
            destination.latitude, destination.longitude
        )
        val headingSource = when {
            state.deviceAzimuthDegrees != null -> HeadingSource.DEVICE_COMPASS
            location.gpsCourseDegrees != null -> HeadingSource.GPS_COURSE
            else -> HeadingSource.NONE
        }
        val heading = state.deviceAzimuthDegrees?.toDouble()
            ?: location.gpsCourseDegrees?.toDouble()
            ?: bearingToTarget
        val arrowRotation = angularDifferenceDegrees(heading, bearingToTarget).toFloat()
        val age = System.currentTimeMillis() - location.timestampMillis
        return state.copy(
            distanceMeters = distance,
            bearingToTargetDegrees = bearingToTarget,
            arrowRotationDegrees = arrowRotation,
            isLocationStale = age > LOCATION_STALE_AFTER_MILLIS,
            isLocationInaccurate = location.accuracyMeters > LOCATION_LOW_ACCURACY_METERS,
            headingSource = headingSource
        )
    }
}
