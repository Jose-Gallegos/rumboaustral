package cl.bovinoapp.rumboaustral.data

import kotlinx.coroutines.flow.Flow

class PointsRepository(private val dao: PointDao) {
    fun observeAll(): Flow<List<Point>> = dao.observeAll()

    suspend fun getById(id: Long): Point? = dao.getById(id)

    suspend fun save(point: Point): Long = dao.insert(point)

    suspend fun update(point: Point) = dao.update(point)

    /** Actualiza nombre, coordenadas, notas y etiquetas (no la fecha). @return filas actualizadas (0 si el punto ya no existe). */
    suspend fun updateDetails(point: Point): Int = dao.updateDetails(
        point.id, point.name, point.latitude, point.longitude, point.notes,
        point.typeLabelId, point.statusLabelId, point.zoneLabelId
    )

    suspend fun delete(point: Point) = dao.delete(point)

    suspend fun importAll(points: List<Point>) {
        dao.insertAll(points)
    }
}
