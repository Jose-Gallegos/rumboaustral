package cl.bovinoapp.rumboaustral.gpx

import android.util.Xml
import cl.bovinoapp.rumboaustral.data.Label
import cl.bovinoapp.rumboaustral.data.LabelKind
import cl.bovinoapp.rumboaustral.data.Point
import cl.bovinoapp.rumboaustral.data.Track
import cl.bovinoapp.rumboaustral.data.TrackPoint
import cl.bovinoapp.rumboaustral.data.labelIdFor
import java.io.OutputStream
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Exportación a GPX 1.1. Las etiquetas viajan de dos formas, ambas previstas por el estándar:
 * - `<type>`: la etiqueta de Tipo, que otras apps muestran como categoría del waypoint.
 * - `<extensions>` con namespace propio (`rumbo:label`): las tres etiquetas con su color, para recuperarlas
 *   al importar. Las apps que no conocen el namespace deben ignorarlas, como manda el estándar.
 *
 * El orden de los hijos de `wpt` es el del esquema (name, desc, type, extensions); algunas apps validan estrictamente.
 */
object GpxIo {
    const val RUMBO_NAMESPACE = "https://bovinoapp.cl/rumboaustral/gpx/1"
    const val EXTENSION_TAG = "rumbo:label"

    fun export(points: List<Point>, out: OutputStream, labels: List<Label> = emptyList()) {
        val labelsById = labels.associateBy { it.id }
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "gpx")
        serializer.attribute(null, "version", "1.1")
        serializer.attribute(null, "creator", "Rumbo Austral")
        serializer.attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1")
        serializer.attribute(null, "xmlns:rumbo", RUMBO_NAMESPACE)

        points.forEach { point ->
            // Solo etiquetas que existen: un ID huérfano no debe producir un elemento vacío.
            val pointLabels = LabelKind.entries.mapNotNull { kind -> point.labelIdFor(kind)?.let { labelsById[it] } }
            serializer.startTag(null, "wpt")
            serializer.attribute(null, "lat", point.latitude.toString())
            serializer.attribute(null, "lon", point.longitude.toString())
            serializer.startTag(null, "name")
            serializer.text(point.name)
            serializer.endTag(null, "name")
            if (point.notes.isNotBlank()) {
                serializer.startTag(null, "desc")
                serializer.text(point.notes)
                serializer.endTag(null, "desc")
            }
            pointLabels.firstOrNull { it.kind == LabelKind.TYPE }?.let { type ->
                serializer.startTag(null, "type")
                serializer.text(type.name)
                serializer.endTag(null, "type")
            }
            if (pointLabels.isNotEmpty()) {
                serializer.startTag(null, "extensions")
                pointLabels.forEach { label ->
                    serializer.startTag(null, EXTENSION_TAG)
                    serializer.attribute(null, "kind", label.kind.name)
                    serializer.attribute(null, "color", formatColor(label.color))
                    serializer.text(label.name)
                    serializer.endTag(null, EXTENSION_TAG)
                }
                serializer.endTag(null, "extensions")
            }
            serializer.endTag(null, "wpt")
        }

        serializer.endTag(null, "gpx")
        serializer.endDocument()
        out.write(writer.toString().toByteArray(Charsets.UTF_8))
    }

    private val ISO_UTC = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Exporta un recorrido grabado como un único `<trk>` con sus puntos en orden. */
    fun exportTrack(track: Track, points: List<TrackPoint>, out: OutputStream) {
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "gpx")
        serializer.attribute(null, "version", "1.1")
        serializer.attribute(null, "creator", "Rumbo Austral")
        serializer.attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1")

        serializer.startTag(null, "trk")
        serializer.startTag(null, "name")
        serializer.text(track.name)
        serializer.endTag(null, "name")
        serializer.startTag(null, "trkseg")
        points.forEach { point ->
            serializer.startTag(null, "trkpt")
            serializer.attribute(null, "lat", point.latitude.toString())
            serializer.attribute(null, "lon", point.longitude.toString())
            serializer.startTag(null, "time")
            serializer.text(ISO_UTC.format(Date(point.timestampMillis)))
            serializer.endTag(null, "time")
            serializer.endTag(null, "trkpt")
        }
        serializer.endTag(null, "trkseg")
        serializer.endTag(null, "trk")

        serializer.endTag(null, "gpx")
        serializer.endDocument()
        out.write(writer.toString().toByteArray(Charsets.UTF_8))
    }

    /** ARGB -> "#RRGGBB" (los colores de etiqueta son opacos). */
    fun formatColor(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** "#RRGGBB" -> ARGB opaco, o null si el texto no es un color válido. */
    fun parseColor(text: String?): Int? {
        val hex = text?.trim()?.removePrefix("#") ?: return null
        // Solo dígitos hexadecimales: toIntOrNull(16) aceptaría también un signo ("-12345").
        if (hex.length != 6 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return 0xFF000000.toInt() or hex.toInt(16)
    }
}
