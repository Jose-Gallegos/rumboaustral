package cl.bovinoapp.rumboaustral.gpx

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Entrada cruda leida de un archivo, antes de validar. Si [problem] no es nulo la entrada
 * no se pudo interpretar como punto (p. ej. una linea o poligono de KML).
 */
data class RawEntry(
    val label: String,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String,
    val problem: String? = null
)

data class ParsedFile(val entries: List<RawEntry>, val truncated: Boolean)

class ImportFormatException(message: String) : Exception(message)

/** Lee puntos (waypoints / placemarks) desde GPX, KML o KMZ. El formato se detecta por contenido. */
object PointFileParser {
    const val MAX_FILE_BYTES = 20 * 1024 * 1024
    const val MAX_ENTRIES = 5_000

    fun parse(input: InputStream): ParsedFile {
        val bytes = readLimited(input)
        val xml = if (isZip(bytes)) extractKml(bytes) else bytes
        return try {
            parseXml(xml)
        } catch (e: XmlPullParserException) {
            throw ImportFormatException("El archivo no es un XML válido")
        } catch (e: IOException) {
            throw ImportFormatException("No se pudo leer el archivo")
        }
    }

    private fun isZip(b: ByteArray) = b.size >= 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()

    private fun readLimited(input: InputStream, limit: Int = MAX_FILE_BYTES): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) throw ImportFormatException("El archivo supera el máximo de 20 MB")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun extractKml(zip: ByteArray): ByteArray {
        var fallback: ByteArray? = null
        try {
            ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory || !entry.name.endsWith(".kml", ignoreCase = true)) continue
                    val data = readLimited(zis)
                    if (entry.name.substringAfterLast('/').equals("doc.kml", ignoreCase = true)) return data
                    if (fallback == null) fallback = data
                }
            }
        } catch (e: ZipException) {
            throw ImportFormatException("El archivo KMZ está dañado")
        }
        return fallback ?: throw ImportFormatException("El KMZ no contiene ningún archivo KML")
    }

    private fun parseXml(bytes: ByteArray): ParsedFile {
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(bytes), null)
        while (p.eventType != XmlPullParser.START_TAG) {
            if (p.next() == XmlPullParser.END_DOCUMENT) throw ImportFormatException("El archivo está vacío")
        }
        return when (p.local()) {
            "gpx" -> parseGpx(p)
            "kml" -> parseKml(p)
            else -> throw ImportFormatException("Formato no soportado: se esperaba GPX, KML o KMZ")
        }
    }

    private fun XmlPullParser.local() = name.substringAfter(':')

    /** Texto del elemento actual (hasta su cierre); deja el parser en el END_TAG. */
    private fun readText(p: XmlPullParser): String {
        val depth = p.depth
        val sb = StringBuilder()
        while (true) {
            when (p.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(p.text)
                XmlPullParser.END_TAG -> if (p.depth == depth) return sb.toString().trim()
                XmlPullParser.END_DOCUMENT -> return sb.toString().trim()
            }
        }
    }

    private fun parseGpx(p: XmlPullParser): ParsedFile {
        val out = mutableListOf<RawEntry>()
        var index = 0
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG || p.local() != "wpt") continue
            if (out.size >= MAX_ENTRIES) return ParsedFile(out, truncated = true)
            index++
            val lat = p.getAttributeValue(null, "lat")?.trim()?.toDoubleOrNull()
            val lon = p.getAttributeValue(null, "lon")?.trim()?.toDoubleOrNull()
            val depth = p.depth
            var name = ""
            var desc = ""
            var cmt = ""
            while (true) {
                val ev = p.next()
                if (ev == XmlPullParser.END_DOCUMENT || (ev == XmlPullParser.END_TAG && p.depth == depth)) break
                if (ev == XmlPullParser.START_TAG && p.depth == depth + 1) {
                    when (p.local()) {
                        "name" -> name = readText(p)
                        "desc" -> desc = readText(p)
                        "cmt" -> cmt = readText(p)
                    }
                }
            }
            val problem = if (lat == null || lon == null) "Falta latitud o longitud" else null
            out += RawEntry("Punto #$index", name, lat, lon, desc.ifBlank { cmt }, problem)
        }
        return ParsedFile(out, truncated = false)
    }

    private fun parseKml(p: XmlPullParser): ParsedFile {
        val out = mutableListOf<RawEntry>()
        var index = 0
        var truncated = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG || p.local() != "Placemark") continue
            if (out.size >= MAX_ENTRIES) return ParsedFile(out, truncated = true)
            index++
            val depth = p.depth
            var name = ""
            var desc = ""
            val pointCoords = mutableListOf<String>()
            var otherGeometry = false
            var inPoint = false
            while (true) {
                val ev = p.next()
                if (ev == XmlPullParser.END_DOCUMENT || (ev == XmlPullParser.END_TAG && p.depth == depth)) break
                if (ev == XmlPullParser.END_TAG && p.local() == "Point") inPoint = false
                if (ev != XmlPullParser.START_TAG) continue
                when (p.local()) {
                    "name" -> if (p.depth == depth + 1) name = readText(p)
                    "description" -> if (p.depth == depth + 1) desc = readText(p)
                    "Point" -> inPoint = true
                    "coordinates" -> {
                        val text = readText(p)
                        if (!inPoint) otherGeometry = true
                        else if (pointCoords.size < MAX_ENTRIES) pointCoords += text
                        else truncated = true
                    }
                    "LineString", "LinearRing", "Polygon", "Track" -> otherGeometry = true
                }
            }
            if (pointCoords.isEmpty()) {
                val problem = if (otherGeometry) "No es un punto (es una línea o polígono)" else "Sin coordenadas"
                out += RawEntry("Punto #$index", name, null, null, htmlToText(desc), problem)
            } else {
                // MultiGeometry puede traer varios puntos: cada uno se importa por separado.
                pointCoords.forEachIndexed { i, text ->
                    if (out.size >= MAX_ENTRIES) { truncated = true; return@forEachIndexed }
                    // Formato KML: lon,lat[,alt]
                    val parts = text.trim().split(Regex("\\s+")).first().split(',')
                    val lon = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
                    val lat = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
                    val problem = if (lat == null || lon == null) "Coordenadas ilegibles" else null
                    val entryName = if (pointCoords.size > 1) "$name (${i + 1})".trim() else name
                    out += RawEntry("Punto #$index", entryName, lat, lon, htmlToText(desc), problem)
                }
            }
            if (truncated) return ParsedFile(out, truncated = true)
        }
        return ParsedFile(out, truncated = false)
    }
}

/** Convierte el HTML habitual de las descripciones KML en texto plano. */
internal fun htmlToText(html: String): String {
    if (html.isBlank()) return ""
    return html
        .replace(Regex("<\\s*(br|/p|/div|/tr|/li)\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
