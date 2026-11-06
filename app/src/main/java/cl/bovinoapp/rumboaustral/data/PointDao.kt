package cl.bovinoapp.rumboaustral.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PointDao {
    @Query("SELECT * FROM points ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Point>>

    @Query("SELECT * FROM points WHERE id = :id")
    suspend fun getById(id: Long): Point?

    @Insert
    suspend fun insert(point: Point): Long

    @Insert
    suspend fun insertAll(points: List<Point>)

    @Update
    suspend fun update(point: Point)

    // Escribe solo los campos editables; no toca etiquetas ni fecha, que pueden haber cambiado desde que se abrió el editor.
    @Query("UPDATE points SET name = :name, latitude = :latitude, longitude = :longitude, notes = :notes WHERE id = :id")
    suspend fun updateDetails(id: Long, name: String, latitude: Double, longitude: Double, notes: String): Int

    @Delete
    suspend fun delete(point: Point)

    @Query("DELETE FROM points WHERE id = :id")
    suspend fun deleteById(id: Long)
}
