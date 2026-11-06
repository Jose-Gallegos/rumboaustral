package cl.bovinoapp.rumboaustral.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointFilterTest {
    private val aguada = Label(id = 1, kind = LabelKind.TYPE, name = "Aguada", color = 0)
    private val poste = Label(id = 2, kind = LabelKind.TYPE, name = "Poste", color = 0)
    private val pendiente = Label(id = 3, kind = LabelKind.STATUS, name = "Pendiente", color = 0)
    private val labels = listOf(aguada, poste, pendiente)

    private fun point(id: Long, type: Long? = null, status: Long? = null, zone: Long? = null) =
        Point(id = id, name = "p$id", latitude = 0.0, longitude = 0.0, typeLabelId = type, statusLabelId = status, zoneLabelId = zone)

    private val points = listOf(
        point(1, type = 1, status = 3),
        point(2, type = 2),
        point(3, type = 1),
        point(4)
    )

    private fun ids(filter: PointFilter) = points.filteredBy(filter, labels).map { it.id }

    @Test
    fun sinSeleccionNoFiltra() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(PointFilter()))
    }

    @Test
    fun variasEtiquetasDeUnaCategoriaSeCombinanConO() {
        val f = PointFilter().toggle(LabelKind.TYPE, 1).toggle(LabelKind.TYPE, 2)
        assertEquals(listOf(1L, 2L, 3L), ids(f))
    }

    @Test
    fun categoriasDistintasSeCombinanConY() {
        val f = PointFilter().toggle(LabelKind.TYPE, 1).toggle(LabelKind.STATUS, 3)
        assertEquals(listOf(1L), ids(f))
    }

    @Test
    fun sinEtiquetaSeleccionaLosPuntosSinAsignar() {
        assertEquals(listOf(4L), ids(PointFilter().toggle(LabelKind.TYPE, NO_LABEL_ID)))
        assertEquals(listOf(2L, 3L, 4L), ids(PointFilter().toggle(LabelKind.STATUS, NO_LABEL_ID)))
    }

    @Test
    fun toggleDosVecesQuitaLaSeleccion() {
        val f = PointFilter().toggle(LabelKind.TYPE, 1).toggle(LabelKind.TYPE, 1)
        assertFalse(f.isActive)
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(f))
    }

    @Test
    fun etiquetaBorradaSePodaYNoEscondePuntos() {
        // La etiqueta 99 ya no existe: el filtro no debe dejar la lista vacía.
        val f = PointFilter().toggle(LabelKind.TYPE, 99)
        assertFalse(f.pruned(labels).isActive)
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(f))
    }

    @Test
    fun podaConservaSinEtiquetaYLasVigentes() {
        val f = PointFilter().toggle(LabelKind.TYPE, 99).toggle(LabelKind.TYPE, NO_LABEL_ID).toggle(LabelKind.TYPE, 1)
        assertEquals(setOf(NO_LABEL_ID, 1L), f.pruned(labels).selected[LabelKind.TYPE])
    }

    @Test
    fun limpiarConservaColorearPor() {
        val f = PointFilter(colorBy = LabelKind.STATUS).toggle(LabelKind.TYPE, 1).cleared()
        assertFalse(f.isActive)
        assertTrue(f.colorBy == LabelKind.STATUS)
    }
}
