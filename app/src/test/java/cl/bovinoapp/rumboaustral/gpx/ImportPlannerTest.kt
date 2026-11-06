package cl.bovinoapp.rumboaustral.gpx

import cl.bovinoapp.rumboaustral.data.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlannerTest {
    private fun entry(name: String, lat: Double?, lon: Double?, notes: String = "", problem: String? = null) =
        RawEntry("Punto", name, lat, lon, notes, problem)

    private fun plan(vararg e: RawEntry, existing: List<Point> = emptyList()) =
        ImportPlanner.plan(ParsedFile(e.toList(), false), existing)

    @Test
    fun validaRangosYCoordenadasFaltantes() {
        val p = plan(
            entry("ok", -33.0, -70.0),
            entry("lat", 95.0, 10.0),
            entry("lon", 10.0, 200.0),
            entry("nan", Double.NaN, 1.0),
            entry("sin", null, null),
            entry("linea", null, null, problem = "No es un punto")
        )
        assertEquals(1, p.candidates.size)
        assertEquals(5, p.invalid.size)
    }

    @Test
    fun nombreVacioUsaPorDefectoYSeNormaliza() {
        val c = plan(entry("   ", 1.0, 1.0), entry("  Mi   punto ", 2.0, 2.0)).candidates
        assertEquals(ImportPlanner.DEFAULT_NAME, c[0].name)
        assertEquals("Mi punto", c[1].name)
    }

    @Test
    fun detectaDuplicadoConPuntoExistente() {
        val existing = listOf(Point(id = 1, name = "Portón", latitude = -33.0, longitude = -70.0))
        val c = plan(
            entry("portón", -33.00001, -70.0, ), // ~1 m, mismo nombre
            entry("Portón", -33.01, -70.0),       // ~1 km: otro lugar
            entry("Otro", -33.0, -70.0),          // mismo lugar, otro nombre
            existing = existing
        ).candidates
        assertEquals(ImportStatus.DUPLICATE_EXISTING, c[0].status)
        assertEquals(ImportStatus.NEW, c[1].status)
        assertEquals(ImportStatus.NEW, c[2].status)
    }

    @Test
    fun detectaDuplicadoDentroDelArchivo() {
        val c = plan(entry("A", 1.0, 1.0), entry("a", 1.0, 1.0), entry("A", 5.0, 5.0)).candidates
        assertEquals(
            listOf(ImportStatus.NEW, ImportStatus.DUPLICATE_IN_FILE, ImportStatus.NEW),
            c.map { it.status }
        )
    }

    @Test
    fun htmlDeDescripcionKmlSeConvierteATexto() {
        val t = htmlToText("<p>Hola &amp; <b>chao</b></p><br>Linea&nbsp;2")
        assertTrue(t.startsWith("Hola & chao"))
        assertTrue(t.contains("Linea 2"))
    }
}
