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

    // Trazo (latitud, longitud) del recorrido en curso. Se muta en sitio (sin copiar la lista entera en cada fix)
    // y se avisa con un contador de versión; el mapa pide solo los puntos nuevos con pathFrom().
    private val pathPoints = ArrayList<Pair<Double, Double>>()
    private val _pathVersion = MutableStateFlow(0)
    val pathVersion: StateFlow<Int> = _pathVersion.asStateFlow()

    fun pathSize(): Int = synchronized(pathPoints) { pathPoints.size }

    /** Copia de los puntos del trazo desde [index] (inclusive) hasta el final. */
    fun pathFrom(index: Int): List<Pair<Double, Double>> = synchronized(pathPoints) {
        if (index >= pathPoints.size) emptyList() else ArrayList(pathPoints.subList(index, pathPoints.size))
    }

    internal fun update(value: Snapshot?) {
        _snapshot.value = value
        if (value == null) {
            synchronized(pathPoints) { pathPoints.clear() }
            _pathVersion.value++
        }
    }

    internal fun appendToPath(latitude: Double, longitude: Double) {
        synchronized(pathPoints) { pathPoints.add(latitude to longitude) }
        _pathVersion.value++
    }
}
