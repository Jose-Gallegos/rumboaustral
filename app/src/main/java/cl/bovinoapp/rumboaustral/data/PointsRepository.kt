package cl.bovinoapp.rumboaustral.data

import kotlinx.coroutines.flow.Flow

class PointsRepository(private val dao: PointDao) {
    fun observeAll(): Flow<List<Point>> = dao.observeAll()

    suspend fun getById(id: Long): Point? = dao.getById(id)

    suspend fun save(point: Point): Long = dao.insert(point)

    suspend fun update(point: Point) = dao.update(point)

    suspend fun delete(point: Point) = dao.delete(point)

    suspend fun importAll(points: List<Point>) {
        points.forEach { dao.insert(it) }
    }
}
