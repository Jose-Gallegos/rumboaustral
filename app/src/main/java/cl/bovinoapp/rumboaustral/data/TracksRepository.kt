package cl.bovinoapp.rumboaustral.data

import cl.bovinoapp.rumboaustral.geo.distanceMeters
import kotlinx.coroutines.flow.Flow

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
