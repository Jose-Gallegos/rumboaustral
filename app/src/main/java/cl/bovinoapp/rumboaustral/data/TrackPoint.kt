package cl.bovinoapp.rumboaustral.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Un fix de GPS dentro de un recorrido grabado. [sequence] preserva el orden (el id autogenerado también lo haría, pero explícito evita depender de eso). */
@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(Track::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId")]
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val sequence: Int
)
