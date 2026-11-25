package cl.bovinoapp.rumboaustral.qr

import cl.bovinoapp.rumboaustral.geo.isValidLatitude
import cl.bovinoapp.rumboaustral.geo.isValidLongitude
import java.net.URLDecoder

/** Resultado de interpretar el contenido de un QR de dron como coordenadas. */
sealed interface DroneQrResult {
    data class Success(val latitude: Double, val longitude: Double) : DroneQrResult
    /** No se encontro ningun par de coordenadas valido en el contenido. */
    data object NotFound : DroneQrResult
    /** Se encontro mas de un par de coordenadas distinto: no se elige uno al azar. */
    data object Ambiguous : DroneQrResult
}

/**
 * Interpreta el contenido de un QR de dron (formato probado en Drongpx) sin depender de
 * un GPX intermedio ni de otra app instalada. Puerto de CoordinateParser de Drongpx, con el
 * agregado de detectar contenido ambiguo (mas de un par de coordenadas distinto) en vez de
 * quedarse silenciosamente con el primero. El parseo de query string y esquema "geo:" es manual
 * (en vez de android.net.Uri) para que la logica se pueda probar con JUnit comun, sin Robolectric.
 */
object DroneQrParser {

    private val AT_PATTERN =
        Regex("""@(-?\d{1,3}(?:\.\d+)?)(?![\d.eE]),(-?\d{1,3}(?:\.\d+)?)(?![\d.eE])""")
    private val LOOSE_PAIR_PATTERN =
        Regex("""(?<![-+.\deE])(-?\d{1,3}(?:\.\d+)?)(?![\d.eE])\s*,\s*(?<![-+.\deE])(-?\d{1,3}(?:\.\d+)?)(?![\d.eE])""")

    fun parse(raw: String): DroneQrResult {
        val trimmed = raw.trim()

        // "query" y "q" tienen la misma prioridad: si ambos estan presentes y no coinciden, es
        // ambiguo en vez de quedarse silenciosamente con "query".
        val queryCandidates = listOfNotNull(
            queryParam(trimmed, "query")?.let { fromPairText(it) },
            queryParam(trimmed, "q")?.let { fromPairText(it) }
        ).distinct()
        when (queryCandidates.size) {
            0 -> {}
            1 -> return DroneQrResult.Success(queryCandidates[0].first, queryCandidates[0].second)
            else -> return DroneQrResult.Ambiguous
        }

        geoSchemePart(trimmed)?.let { part ->
            fromPairText(part)?.let { c -> return DroneQrResult.Success(c.first, c.second) }
        }

        // AT_PATTERN y LOOSE_PAIR_PATTERN se combinan antes de decidir: si cada uno encontrara
        // solo un par pero son pares distintos, el contenido sigue siendo ambiguo.
        val candidates = (AT_PATTERN.findAll(trimmed) + LOOSE_PAIR_PATTERN.findAll(trimmed))
            .mapNotNull { m -> fromParts(m.groupValues[1], m.groupValues[2]) }
            .distinct()
            .toList()
        return when (candidates.size) {
            0 -> DroneQrResult.NotFound
            1 -> DroneQrResult.Success(candidates[0].first, candidates[0].second)
            else -> DroneQrResult.Ambiguous
        }
    }

    /** Valor del parametro [key] en la query string de [text] (lo que sigue al primer "?"), o null si no esta. */
    private fun queryParam(text: String, key: String): String? {
        val queryStart = text.indexOf('?')
        if (queryStart < 0) return null
        val queryString = text.substring(queryStart + 1)
        for (pair in queryString.split('&')) {
            val eq = pair.indexOf('=')
            if (eq < 0) continue
            if (pair.substring(0, eq) != key) continue
            val value = pair.substring(eq + 1)
            return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        }
        return null
    }

    /** Parte especifica del esquema "geo:lat,lon" (sin la query string), o null si [text] no usa ese esquema. */
    private fun geoSchemePart(text: String): String? {
        if (!text.startsWith("geo:", ignoreCase = true)) return null
        return text.substring(4).substringBefore('?')
    }

    private fun fromPairText(text: String): Pair<Double, Double>? {
        val parts = text.split(",").map { it.trim() }
        if (parts.size < 2) return null
        return fromParts(parts[0], parts[1])
    }

    private fun fromParts(latText: String, lonText: String): Pair<Double, Double>? {
        val lat = latText.toDoubleOrNull() ?: return null
        val lon = lonText.toDoubleOrNull() ?: return null
        if (!isValidLatitude(lat) || !isValidLongitude(lon)) return null
        return lat to lon
    }
}
