package cl.bovinoapp.rumboaustral.data

import java.text.Normalizer

/** Asignaciones propuestas para una categoría: etiqueta -> IDs de los puntos que se le asignarían. */
data class KindPlan(
    val kind: LabelKind,
    val assignments: Map<Label, List<Long>>,
    /** Puntos con varias coincidencias igual de específicas: se omiten porque no hay forma de elegir. */
    val ambiguous: Int
) {
    /** Asignaciones de esta categoría; como cada punto recibe una sola etiqueta por categoría, son puntos distintos. */
    val total: Int get() = assignments.values.sumOf { it.size }
}

data class AutoClassifyPlan(val perKind: List<KindPlan>) {
    /** Etiquetas que se asignarían en total (un punto puede recibir una por categoría). */
    val assignmentCount: Int get() = perKind.sumOf { it.total }

    /** Puntos distintos que recibirían al menos una etiqueta. */
    val pointCount: Int
        get() = perKind.flatMapTo(HashSet()) { kindPlan -> kindPlan.assignments.values.flatten() }.size

    val ambiguous: Int get() = perKind.sumOf { it.ambiguous }
}

/**
 * Propone etiquetas para puntos sin etiquetar buscando el nombre de cada etiqueta como palabra (o frase)
 * completa dentro del nombre del punto, sin distinguir mayúsculas ni tildes. "Aguada 12" recibe "Aguada",
 * pero "Basural" no recibe "Sur". Se acepta plural simple ("aguadas") y número pegado ("aguada12").
 * Nunca pisa etiquetas ya asignadas.
 */
object AutoClassifier {
    fun plan(points: List<Point>, labels: List<Label>): AutoClassifyPlan =
        AutoClassifyPlan(LabelKind.entries.map { planKind(it, points, labels) })

    private class Keyword(val label: Label, val words: String) {
        // Palabras completas: delimitadas por espacio o borde; el plural simple (s/es) solo en la última palabra.
        val regex = Regex("(^| )" + Regex.escape(words) + "(s|es)?( |$)")
        val length = words.length
    }

    private fun planKind(kind: LabelKind, points: List<Point>, allLabels: List<Label>): KindPlan {
        val keywords = allLabels.filter { it.kind == kind }
            .map { Keyword(it, words(it.name)) }
            .filter { it.words.isNotEmpty() }
        val assignments = linkedMapOf<Label, MutableList<Long>>()
        var ambiguous = 0
        if (keywords.isNotEmpty()) {
            for (point in points) {
                if (point.labelIdFor(kind) != null) continue
                val name = words(point.name)
                val matches = keywords.filter { it.regex.containsMatchIn(name) }
                if (matches.isEmpty()) continue
                // Gana la coincidencia más específica ("Poste esquinero" antes que "Poste"); si empatan, es ambiguo.
                val longest = matches.maxOf { it.length }
                val best = matches.filter { it.length == longest }
                if (best.size == 1) assignments.getOrPut(best.single().label) { mutableListOf() }.add(point.id) else ambiguous++
            }
        }
        return KindPlan(kind, assignments, ambiguous)
    }

    /**
     * Texto en minúsculas, sin tildes y reducido a palabras separadas por un espacio. Letras y números pegados
     * se separan ("aguada12" -> "aguada 12") y la puntuación cuenta como separador.
     */
    internal fun words(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
