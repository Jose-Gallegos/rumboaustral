package cl.bovinoapp.rumboaustral.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cada punto tiene a lo sumo una etiqueta por categoría (null = sin asignar).
 * Si se borra una etiqueta, la clave foránea deja el campo en null (SET_NULL).
 */
@Entity(
    tableName = "points",
    foreignKeys = [
        ForeignKey(Label::class, ["id"], ["typeLabelId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(Label::class, ["id"], ["statusLabelId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(Label::class, ["id"], ["zoneLabelId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("typeLabelId"), Index("statusLabelId"), Index("zoneLabelId")]
)
data class Point(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val typeLabelId: Long? = null,
    val statusLabelId: Long? = null,
    val zoneLabelId: Long? = null
)
