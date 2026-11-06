package cl.bovinoapp.rumboaustral.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Las tres categorías fijas de etiquetas. Lo editable son las etiquetas de cada una. */
enum class LabelKind(val displayName: String) {
    TYPE("Tipo"),
    STATUS("Estado"),
    ZONE("Zona")
}

/** Una etiqueta de una categoría (por ejemplo "Aguada" dentro de Tipo). [color] es ARGB. */
@Entity(tableName = "labels", indices = [Index("kind")])
data class Label(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: LabelKind,
    val name: String,
    val color: Int,
    val sortOrder: Int = 0
)
