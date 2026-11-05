package cl.bovinoapp.rumboaustral.gpx

import android.util.Xml
import cl.bovinoapp.rumboaustral.data.Point
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
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

    fun import(input: InputStream): List<Point> {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(input, null)

        val points = mutableListOf<Point>()
        var name = ""
        var notes = ""
        var lat: Double? = null
        var lon: Double? = null
        var inWpt = false
        var currentTag: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    if (tag == "wpt") {
                        inWpt = true
                        name = ""
                        notes = ""
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                    } else if (inWpt) {
                        currentTag = tag
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inWpt) {
                        when (currentTag) {
                            "name" -> name = parser.text?.trim().orEmpty()
                            "desc", "cmt" -> if (notes.isBlank()) notes = parser.text?.trim().orEmpty()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "wpt") {
                        val latitude = lat
                        val longitude = lon
                        if (latitude != null && longitude != null) {
                            points += Point(
                                name = name.ifBlank { "Punto importado" },
                                latitude = latitude,
                                longitude = longitude,
                                notes = notes
                            )
                        }
                        inWpt = false
                        currentTag = null
                    } else {
                        currentTag = null
                    }
                }
            }
            eventType = parser.next()
        }
        return points
    }
}
