package cl.bovinoapp.rumboaustral.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DroneQrParserTest {

    @Test
    fun `par simple separado por coma se reconoce`() {
        val result = DroneQrParser.parse("-45.123456,-72.654321")
        assertTrue(result is DroneQrResult.Success)
        result as DroneQrResult.Success
        assertEquals(-45.123456, result.latitude, 1e-9)
        assertEquals(-72.654321, result.longitude, 1e-9)
    }

    @Test
    fun `formato con arroba se reconoce`() {
        val result = DroneQrParser.parse("DRONE01 @-45.5,-72.5 batt=80")
        assertTrue(result is DroneQrResult.Success)
        result as DroneQrResult.Success
        assertEquals(-45.5, result.latitude, 1e-9)
        assertEquals(-72.5, result.longitude, 1e-9)
    }

    @Test
    fun `contenido sin coordenadas no encuentra nada`() {
        assertEquals(DroneQrResult.NotFound, DroneQrParser.parse("DRONE01 sin coordenadas"))
    }

    @Test
    fun `latitud fuera de rango no cuenta como par valido`() {
        assertEquals(DroneQrResult.NotFound, DroneQrParser.parse("185.0,-72.5"))
    }

    @Test
    fun `dos pares distintos son ambiguos`() {
        val result = DroneQrParser.parse("-45.1,-72.1 waypoint2=-46.2,-73.2")
        assertEquals(DroneQrResult.Ambiguous, result)
    }

    @Test
    fun `el mismo par repetido no es ambiguo`() {
        val result = DroneQrParser.parse("-45.1,-72.1 (-45.1,-72.1)")
        assertTrue(result is DroneQrResult.Success)
        result as DroneQrResult.Success
        assertEquals(-45.1, result.latitude, 1e-9)
        assertEquals(-72.1, result.longitude, 1e-9)
    }

    @Test
    fun `par con arroba y otro par de texto distinto son ambiguos`() {
        val result = DroneQrParser.parse("Dron @-45.1,-72.1 destino=-46.2,-73.2")
        assertEquals(DroneQrResult.Ambiguous, result)
    }

    @Test
    fun `query y q con coordenadas distintas son ambiguos`() {
        val result = DroneQrParser.parse("geo:0,0?q=-45.1,-72.1&query=-46.2,-73.2")
        assertEquals(DroneQrResult.Ambiguous, result)
    }

    @Test
    fun `query y q con la misma coordenada no son ambiguos`() {
        val result = DroneQrParser.parse("geo:0,0?q=-45.1,-72.1&query=-45.1,-72.1")
        assertTrue(result is DroneQrResult.Success)
        result as DroneQrResult.Success
        assertEquals(-45.1, result.latitude, 1e-9)
        assertEquals(-72.1, result.longitude, 1e-9)
    }

    @Test
    fun `esquema geo sin query usa las coordenadas del esquema`() {
        val result = DroneQrParser.parse("geo:-45.1,-72.1")
        assertTrue(result is DroneQrResult.Success)
        result as DroneQrResult.Success
        assertEquals(-45.1, result.latitude, 1e-9)
        assertEquals(-72.1, result.longitude, 1e-9)
    }
}
