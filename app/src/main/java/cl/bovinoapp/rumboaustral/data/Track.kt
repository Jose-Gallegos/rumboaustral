package cl.bovinoapp.rumboaustral.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Un recorrido grabado: metadatos y estadísticas ya calculadas al terminar de grabar. */
@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAt: Long,
    /** null mientras se está grabando. */
    val endedAt: Long? = null,
    val distanceMeters: Double = 0.0,
    val avgSpeedKmh: Double = 0.0
)
