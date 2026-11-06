package cl.bovinoapp.rumboaustral.data

import cl.bovinoapp.rumboaustral.gpx.GpxIo
import cl.bovinoapp.rumboaustral.gpx.ImportPlanner
import cl.bovinoapp.rumboaustral.gpx.ParsedFile
import cl.bovinoapp.rumboaustral.gpx.RawEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelImportTest {
    private val aguada = Label(id = 1, kind = LabelKind.TYPE, name = "Aguada", color = 0)

    @Test
    fun sanitizeRecortaDescartaVaciosYDejaUnaPorCategoria() {
        val refs = listOf(
            LabelRef(LabelKind.TYPE, "  Aguada "),
            LabelRef(LabelKind.STATUS, "   "),
            LabelRef(LabelKind.TYPE, "Poste")
        )
        // Gana la última de TYPE; STATUS no tiene nombre utilizable.
        assertEquals(listOf(LabelRef(LabelKind.TYPE, "Poste")), LabelImport.sanitize(refs))
        assertEquals("Aguada", LabelImport.sanitize(refs.take(1)).single().name)
    }

    @Test
    fun sanitizeNoAlteraPuntuacionNiEspaciosInternos() {
        // El administrador permite estos nombres; un respaldo debe devolverlos tal cual.
        assertEquals("A-1", LabelImport.sanitize(listOf(LabelRef(LabelKind.ZONE, "A-1"))).single().name)
        assertEquals("A  1", LabelImport.sanitize(listOf(LabelRef(LabelKind.ZONE, "A  1"))).single().name)
        assertEquals("!!!", LabelImport.sanitize(listOf(LabelRef(LabelKind.ZONE, "!!!"))).single().name)
    }

    @Test
    fun zonasConPuntuacionDistintaNoSeFusionan() {
        val a1 = Label(id = 5, kind = LabelKind.ZONE, name = "A1", color = 0)
        val refs = listOf(LabelRef(LabelKind.ZONE, "A-1", color = 1), LabelRef(LabelKind.ZONE, "A 1", color = 2))
        // "A1" ya existe; "A-1" y "A 1" son distintas entre sí y de "A1": las dos se crean.
        assertEquals(refs, LabelImport.missing(refs, listOf(a1)))
    }

    @Test
    fun laClaveCoincideConLaReglaDeDuplicadosDelAdministrador() {
        assertEquals(LabelImport.key("Aguada"), LabelImport.key("  AGUADA "))
        assertNotEquals(LabelImport.key("A-1"), LabelImport.key("A1"))
    }

    @Test
    fun sanitizeRecortaNombresLargos() {
        val long = "x".repeat(200)
        assertEquals(LabelImport.MAX_LABEL_NAME, LabelImport.sanitize(listOf(LabelRef(LabelKind.TYPE, long))).single().name.length)
    }

    @Test
    fun missingIgnoraLasQueYaExistenSinDistinguirMayusculasNiTildes() {
        val refs = listOf(LabelRef(LabelKind.TYPE, "AGUADA"), LabelRef(LabelKind.TYPE, "Pozo"))
        assertEquals(listOf(LabelRef(LabelKind.TYPE, "Pozo")), LabelImport.missing(refs, listOf(aguada)))
    }

    @Test
    fun missingEsPorCategoria() {
        // "Aguada" existe en Tipo, pero no en Zona: en Zona hay que crearla.
        val refs = listOf(LabelRef(LabelKind.ZONE, "Aguada"))
        assertEquals(refs, LabelImport.missing(refs, listOf(aguada)))
    }

    @Test
    fun missingNoRepiteYConservaElPrimerColor() {
        val refs = listOf(
            LabelRef(LabelKind.TYPE, "Pozo", color = 111),
            LabelRef(LabelKind.TYPE, "pozo", color = 222),
            LabelRef(LabelKind.TYPE, "Pozo", color = 333)
        )
        assertEquals(listOf(LabelRef(LabelKind.TYPE, "Pozo", color = 111)), LabelImport.missing(refs, emptyList()))
    }

    @Test
    fun colorDeIdaYVuelta() {
        val color = 0xFF1E88E5.toInt()
        assertEquals("#1E88E5", GpxIo.formatColor(color))
        assertEquals(color, GpxIo.parseColor("#1E88E5"))
        assertEquals(color, GpxIo.parseColor("1e88e5"))
    }

    @Test
    fun colorInvalidoDevuelveNull() {
        assertNull(GpxIo.parseColor(null))
        assertNull(GpxIo.parseColor("#12345"))
        assertNull(GpxIo.parseColor("#GGGGGG"))
        assertNull(GpxIo.parseColor("-12345"))
        assertNull(GpxIo.parseColor("+12345"))
    }

    @Test
    fun elPlanificadorConservaLasEtiquetasLimpias() {
        val entry = RawEntry(
            "Punto #1", "Aguada 1", -33.0, -70.0, "", null,
            listOf(LabelRef(LabelKind.TYPE, " Aguada "), LabelRef(LabelKind.STATUS, " "))
        )
        val plan = ImportPlanner.plan(ParsedFile(listOf(entry), false), emptyList())
        assertEquals(listOf(LabelRef(LabelKind.TYPE, "Aguada")), plan.candidates.single().labels)
    }
}
