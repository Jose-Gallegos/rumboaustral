package cl.bovinoapp.rumboaustral.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Progreso en vivo del recorrido que se está grabando (o null si no hay ninguno). Un singleton de proceso:
 * más simple que un bind al servicio para una app de un solo proceso, y es solo UI reactiva (no persiste nada).
 */
object TrackRecordingState {
    data class Snapshot(val trackId: Long, val name: String, val distanceMeters: Double, val startedAt: Long)

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

    internal fun update(value: Snapshot?) {
        _snapshot.value = value
    }
}
