package cl.bovinoapp.rumboaustral.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoClassifierTest {
    private val aguada = Label(id = 1, kind = LabelKind.TYPE, name = "Aguada", color = 0)
    private val poste = Label(id = 2, kind = LabelKind.TYPE, name = "Poste", color = 0)
    private val posteEsq = Label(id = 3, kind = LabelKind.TYPE, name = "Poste esquinero", color = 0)
    private val norte = Label(id = 4, kind = LabelKind.ZONE, name = "Norte", color = 0)
    private val labels = listOf(aguada, poste, posteEsq, norte)

    private fun point(id: Long, name: String, type: Long? = null) =
        Point(id = id, name = name, latitude = 0.0, longitude = 0.0, typeLabelId = type)

    private fun typePlan(points: List<Point>) = AutoClassifier.plan(points, labels).perKind.first { it.kind == LabelKind.TYPE }

    @Test
    fun coincideSinDistinguirMayusculasNiTildes() {
        val plan = typePlan(listOf(point(1, "AGUADA 12"), point(2, "aguáda norte"), point(3, "Otra cosa")))
        assertEquals(listOf(1L, 2L), plan.assignments[aguada])
        assertEquals(0, plan.ambiguous)
    }

    @Test
    fun noPisaEtiquetasYaAsignadas() {
        val plan = typePlan(listOf(point(1, "Aguada 1", type = 2), point(2, "Aguada 2")))
        assertEquals(listOf(2L), plan.assignments[aguada])
    }

    @Test
    fun gananLasCoincidenciasMasEspecificas() {
        val plan = typePlan(listOf(point(1, "Poste esquinero 3"), point(2, "Poste roto")))
        assertEquals(listOf(1L), plan.assignments[posteEsq])
        assertEquals(listOf(2L), plan.assignments[poste])
    }

    @Test
    fun empateEntreEtiquetasDelMismoLargoEsAmbiguoYSeOmite() {
        val a = Label(id = 10, kind = LabelKind.TYPE, name = "Pozo", color = 0)
        val b = Label(id = 11, kind = LabelKind.TYPE, name = "Agua", color = 0)
        val plan = AutoClassifier.plan(listOf(point(1, "Pozo Agua")), listOf(a, b)).perKind.first { it.kind == LabelKind.TYPE }
        assertTrue(plan.assignments.isEmpty())
        assertEquals(1, plan.ambiguous)
    }

    @Test
    fun cadaCategoriaSePlanificaPorSeparado() {
        val plan = AutoClassifier.plan(listOf(point(1, "Aguada norte")), labels)
        assertEquals(1, plan.perKind.first { it.kind == LabelKind.TYPE }.total)
        assertEquals(1, plan.perKind.first { it.kind == LabelKind.ZONE }.total)
        assertEquals(2, plan.assignmentCount)
        // Un solo punto recibe dos etiquetas: no son dos puntos.
        assertEquals(1, plan.pointCount)
    }

    @Test
    fun noCoincideDentroDeOtraPalabra() {
        val sur = Label(id = 30, kind = LabelKind.ZONE, name = "Sur", color = 0)
        val plan = AutoClassifier.plan(listOf(point(1, "Basural"), point(2, "Potrero Sur"), point(3, "Surco")), listOf(sur))
            .perKind.first { it.kind == LabelKind.ZONE }
        assertEquals(listOf(2L), plan.assignments[sur])
    }

    @Test
    fun aceptaPluralSimpleYNumeroPegado() {
        val plan = typePlan(listOf(point(1, "Aguadas del norte"), point(2, "Aguada12"), point(3, "Aguadón")))
        assertEquals(listOf(1L, 2L), plan.assignments[aguada])
    }

    @Test
    fun etiquetasDeVariasPalabrasCoincidenComoFrase() {
        val animal = Label(id = 40, kind = LabelKind.TYPE, name = "Animal muerto", color = 0)
        val plan = AutoClassifier.plan(
            listOf(point(1, "Animal muerto cerca del cerco"), point(2, "Animal vivo"), point(3, "muerto animal")),
            listOf(animal)
        ).perKind.first { it.kind == LabelKind.TYPE }
        assertEquals(listOf(1L), plan.assignments[animal])
    }

    @Test
    fun puntuacionCuentaComoSeparador() {
        val plan = typePlan(listOf(point(1, "Cerca-Aguada, sector 2")))
        assertEquals(listOf(1L), plan.assignments[aguada])
    }

    @Test
    fun etiquetasSinNombreSeIgnoran() {
        val vacia = Label(id = 20, kind = LabelKind.TYPE, name = "  ", color = 0)
        val plan = AutoClassifier.plan(listOf(point(1, "Cualquiera")), listOf(vacia))
        assertEquals(0, plan.assignmentCount)
    }
}
