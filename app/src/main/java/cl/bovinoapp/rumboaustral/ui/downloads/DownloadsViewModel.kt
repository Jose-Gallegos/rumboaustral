package cl.bovinoapp.rumboaustral.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.bovinoapp.rumboaustral.map.DownloadedRegion
import cl.bovinoapp.rumboaustral.map.OfflineRegionsRepository
import com.mapbox.geojson.Point as GeoPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val regions: List<DownloadedRegion> = emptyList(),
    val activeDownloadId: String? = null,
    val activeDownloadFraction: Float = 0f,
    val errorMessage: String? = null
)

class DownloadsViewModel(private val repository: OfflineRegionsRepository) : ViewModel() {
    private val _state = MutableStateFlow(DownloadsUiState())
    val state: StateFlow<DownloadsUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.listRegions() }
                .onSuccess { regions -> _state.value = _state.value.copy(regions = regions) }
                .onFailure { e -> _state.value = _state.value.copy(errorMessage = e.message) }
        }
    }

    fun downloadRegion(id: String, centerLat: Double, centerLon: Double, sideMeters: Double) {
        downloadJob?.cancel()
        _state.value = _state.value.copy(activeDownloadId = id, activeDownloadFraction = 0f, errorMessage = null)
        downloadJob = viewModelScope.launch {
            runCatching {
                repository.download(id, GeoPoint.fromLngLat(centerLon, centerLat), sideMeters).collect { progress ->
                    _state.value = _state.value.copy(activeDownloadFraction = progress.fraction)
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(errorMessage = e.message)
            }
            _state.value = _state.value.copy(activeDownloadId = null)
            refresh()
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = _state.value.copy(activeDownloadId = null)
    }

    fun deleteRegion(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            refresh()
        }
    }
}
