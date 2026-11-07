package cl.bovinoapp.rumboaustral.ui.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.bovinoapp.rumboaustral.data.Track
import cl.bovinoapp.rumboaustral.data.TrackPoint
import cl.bovinoapp.rumboaustral.data.TracksRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TracksViewModel(private val repository: TracksRepository) : ViewModel() {
    val tracks: StateFlow<List<Track>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(track: Track, name: String) {
        viewModelScope.launch { repository.rename(track, name) }
    }

    fun delete(track: Track) {
        viewModelScope.launch { repository.delete(track.id) }
    }

    suspend fun pointsFor(track: Track): List<TrackPoint> = repository.getTrackPoints(track.id)
}
