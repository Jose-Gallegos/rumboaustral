package cl.bovinoapp.rumboaustral.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmlNamespacesTest {
    private val rumbo = GpxIo.RUMBO_NAMESPACE

    @Test
    fun leeDeclaracionesConPrefijoYPorDefecto() {
        val decls = XmlNamespaces.declarations(
            listOf("xmlns" to "http://gpx", "xmlns:rumbo" to rumbo, "lat" to "1.0", "version" to "1.1")
        )
        assertEquals(mapOf("" to "http://gpx", "rumbo" to rumbo), decls)
    }

    @Test
    fun resuelveElPrefijoDeclaradoEnElRaiz() {
        val root = mapOf("rumbo" to rumbo)
        assertEquals(rumbo, XmlNamespaces.resolve("rumbo:label", listOf(emptyMap(), root)))
    }

    @Test
    fun otroNamespaceConElMismoNombreLocalNoCoincide() {
        val scopes = listOf(mapOf("otra" to "https://otra.app/ns"))
        assertEquals("https://otra.app/ns", XmlNamespaces.resolve("otra:label", scopes))
        assertNotEquals(rumbo, XmlNamespaces.resolve("otra:label", scopes))
    }

    @Test
    fun elPrefijoNoDeclaradoNoResuelve() {
        assertNull(XmlNamespaces.resolve("rumbo:label", listOf(mapOf("otra" to "x"))))
    }

    @Test
    fun elPrefijoPuedeTenerOtroNombreSiApuntaANuestroNamespace() {
        assertEquals(rumbo, XmlNamespaces.resolve("rb:label", listOf(mapOf("rb" to rumbo))))
    }

    @Test
    fun sinPrefijoUsaElNamespacePorDefectoMasCercano() {
        // <label> dentro de un contexto GPX no es nuestro; con xmlns propio en el elemento sí lo es.
        val gpx = mapOf("" to "http://www.topografix.com/GPX/1/1")
        val own = mapOf("" to rumbo)
        assertNotEquals(rumbo, XmlNamespaces.resolve("label", listOf(emptyMap(), gpx)))
        assertEquals(rumbo, XmlNamespaces.resolve("label", listOf(own, gpx)))
    }

    @Test
    fun laDeclaracionMasInternaGana() {
        val scopes = listOf(mapOf("p" to rumbo), mapOf("p" to "https://otra.app/ns"))
        assertEquals(rumbo, XmlNamespaces.resolve("p:label", scopes))
    }
}
