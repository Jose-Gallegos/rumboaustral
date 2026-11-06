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

/** ID de la etiqueta que el punto tiene en la categoría [kind] (null = sin etiqueta). */
fun Point.labelIdFor(kind: LabelKind): Long? = when (kind) {
    LabelKind.TYPE -> typeLabelId
    LabelKind.STATUS -> statusLabelId
    LabelKind.ZONE -> zoneLabelId
}

/** Copia del punto con la etiqueta de la categoría [kind] reemplazada por [labelId]. */
fun Point.withLabel(kind: LabelKind, labelId: Long?): Point = when (kind) {
    LabelKind.TYPE -> copy(typeLabelId = labelId)
    LabelKind.STATUS -> copy(statusLabelId = labelId)
    LabelKind.ZONE -> copy(zoneLabelId = labelId)
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
