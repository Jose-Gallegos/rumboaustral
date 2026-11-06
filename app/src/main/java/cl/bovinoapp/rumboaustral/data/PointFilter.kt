package cl.bovinoapp.rumboaustral.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** ID reservado dentro de un filtro para "puntos sin etiqueta" (los IDs reales de etiquetas parten en 1). */
const val NO_LABEL_ID = 0L

/** Color de los marcadores de puntos sin etiqueta en la categoría elegida. */
val UNLABELED_COLOR: Int = 0xFF9E9E9E.toInt()

/**
 * Filtro de puntos: por cada categoría, las etiquetas aceptadas (varias = "o"); categorías distintas se
 * combinan con "y". Una categoría sin selección no filtra. [colorBy] decide qué categoría pinta los marcadores.
 */
data class PointFilter(
    val selected: Map<LabelKind, Set<Long>> = emptyMap(),
    val colorBy: LabelKind = LabelKind.TYPE
) {
    val activeKinds: Int get() = selected.count { it.value.isNotEmpty() }
    val isActive: Boolean get() = activeKinds > 0

    fun toggle(kind: LabelKind, labelId: Long): PointFilter {
        val current = selected[kind].orEmpty()
        val next = if (labelId in current) current - labelId else current + labelId
        return copy(selected = selected + (kind to next))
    }

    fun cleared(): PointFilter = copy(selected = emptyMap())

    /** Quita de la selección las etiquetas que ya no existen, para no esconder puntos por un ID borrado. */
    fun pruned(labels: List<Label>): PointFilter {
        val live = labels.mapTo(HashSet()) { it.id }
        return copy(
            selected = selected
                .mapValues { (_, ids) -> ids.filterTo(HashSet()) { it == NO_LABEL_ID || it in live } }
                .filterValues { it.isNotEmpty() }
        )
    }

    fun matches(point: Point): Boolean =
        selected.all { (kind, ids) -> ids.isEmpty() || (point.labelIdFor(kind) ?: NO_LABEL_ID) in ids }
}

/** Puntos que cumplen [filter] (podado contra las etiquetas vigentes [labels]). */
fun List<Point>.filteredBy(filter: PointFilter, labels: List<Label>): List<Point> {
    val effective = filter.pruned(labels)
    return if (effective.isActive) filter { effective.matches(it) } else this
}

/** Filtro compartido entre el mapa y la lista de puntos (solo en memoria). */
class PointFilterStore {
    val filter = MutableStateFlow(PointFilter())

    fun toggle(kind: LabelKind, labelId: Long) = filter.update { it.toggle(kind, labelId) }

    fun setColorBy(kind: LabelKind) = filter.update { it.copy(colorBy = kind) }

    fun clear() = filter.update { it.cleared() }
}
