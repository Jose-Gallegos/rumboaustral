package cl.bovinoapp.rumboaustral.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import cl.bovinoapp.rumboaustral.data.Label
import cl.bovinoapp.rumboaustral.data.Point
import cl.bovinoapp.rumboaustral.data.Track
import cl.bovinoapp.rumboaustral.data.TrackPoint
import cl.bovinoapp.rumboaustral.gpx.GpxIo
import java.io.File

/** Exporta un punto o un recorrido a un GPX temporal y abre el selector de apps de Android para compartirlo. */
object ShareFiles {
    private fun sharedDir(context: Context): File =
        File(context.cacheDir, "shared").apply { mkdirs() }

    private fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun sharePointGpx(context: Context, point: Point, labels: List<Label>) {
        val file = File(sharedDir(context), "${sanitize(point.name)}.gpx")
        file.outputStream().use { GpxIo.export(listOf(point), it, labels) }
        shareFile(context, file)
    }

    fun shareTrackGpx(context: Context, track: Track, points: List<TrackPoint>) {
        val file = File(sharedDir(context), "${sanitize(track.name)}.gpx")
        file.outputStream().use { GpxIo.exportTrack(track, points, it) }
        shareFile(context, file)
    }

    private fun sanitize(name: String): String =
        name.ifBlank { "sin-nombre" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
