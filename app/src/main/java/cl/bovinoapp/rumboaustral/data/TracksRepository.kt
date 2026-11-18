package cl.bovinoapp.rumboaustral.data

import cl.bovinoapp.rumboaustral.geo.distanceMeters
import kotlinx.coroutines.flow.Flow

/** Qué hacer con un recorrido cuya grabación se cortó. A futuro: CONTINUAR (retomar la grabación sobre el mismo). */
enum class InterruptedTrackAction { FINISH }

class TracksRepository(private val dao: TrackDao) {
    fun observeAll(): Flow<List<Track>> = dao.observeAll()

    suspend fun getById(id: Long): Track? = dao.getById(id)

    suspend fun startTrack(name: String, startedAt: Long): Long =
        dao.insertTrack(Track(name = name, startedAt = startedAt))

    suspend fun appendPoints(points: List<TrackPoint>) {
        if (points.isNotEmpty()) dao.insertTrackPoints(points)
    }

    suspend fun getTrackPoints(trackId: Long): List<TrackPoint> = dao.getTrackPoints(trackId)

    // Por ID y solo el nombre: el Track que tiene la pantalla pudo quedar desactualizado (p.ej. si el
    // recorrido se detuvo mientras el dialogo de renombrar estaba abierto), y copiar ese Track entero
    // pisaria endedAt/distancia/velocidad ya calculados con los valores viejos (anteriores a terminar).
    suspend fun rename(trackId: Long, name: String) = dao.renameTrack(trackId, name)

    suspend fun delete(id: Long) = dao.deleteTrack(id)

    /** Cierra el recorrido: calcula distancia total y velocidad promedio a partir de los puntos grabados. */
    suspend fun finishTrack(trackId: Long, endedAt: Long) {
        val track = dao.getById(trackId) ?: return
        close(track, endedAt)
    }

    /**
     * Recorridos sin cerrar. Solo es seguro tratarlos como cortados mientras ninguna grabación esté en marcha:
     * quien llame debe garantizarlo (ver AppContainer.interruptedRecovery).
     */
    suspend fun interruptedTracks(): List<Track> = dao.getUnfinished()

    /**
     * Cierra un recorrido interrumpido: termina en su último punto grabado (o en su inicio si no alcanzó a
     * grabar ninguno) y calcula las estadísticas con lo que hay.
     */
    suspend fun finishInterrupted(track: Track) {
        val lastTimestamp = dao.getTrackPoints(track.id).lastOrNull()?.timestampMillis
        close(track, lastTimestamp ?: track.startedAt)
    }

    /**
     * Resuelve todos los recorridos interrumpidos preguntando a [decide] qué hacer con cada uno. Es el único punto
     * donde se decide el destino: hoy la respuesta por defecto es terminarlo ahí; para preguntar al usuario basta
     * pasar un [decide] que lo consulte (y añadir una acción de continuar a [InterruptedTrackAction]).
     */
    suspend fun recoverInterrupted(
        decide: suspend (Track) -> InterruptedTrackAction = { InterruptedTrackAction.FINISH }
    ) {
        for (track in interruptedTracks()) {
            when (decide(track)) {
                InterruptedTrackAction.FINISH -> finishInterrupted(track)
            }
        }
    }

    private suspend fun close(track: Track, endedAt: Long) {
        val trackId = track.id
        val points = dao.getTrackPoints(trackId)
        var distance = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            distance += distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        val durationHours = (endedAt - track.startedAt).coerceAtLeast(1L) / 3_600_000.0
        val avgSpeedKmh = if (durationHours > 0) (distance / 1000.0) / durationHours else 0.0
        dao.updateTrack(track.copy(endedAt = endedAt, distanceMeters = distance, avgSpeedKmh = avgSpeedKmh))
    }
}
