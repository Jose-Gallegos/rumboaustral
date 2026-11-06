package cl.bovinoapp.rumboaustral.gpx

import android.util.Xml
import cl.bovinoapp.rumboaustral.data.Point
import java.io.OutputStream
import java.io.StringWriter

object GpxIo {

    fun export(points: List<Point>, out: OutputStream) {
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "gpx")
        serializer.attribute(null, "version", "1.1")
        serializer.attribute(null, "creator", "Rumbo Austral")
        serializer.attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1")

        points.forEach { point ->
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
            serializer.endTag(null, "wpt")
        }

        serializer.endTag(null, "gpx")
        serializer.endDocument()
        out.write(writer.toString().toByteArray(Charsets.UTF_8))
    }
}
