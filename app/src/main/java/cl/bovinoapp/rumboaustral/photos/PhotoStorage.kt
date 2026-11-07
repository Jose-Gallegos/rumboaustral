package cl.bovinoapp.rumboaustral.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Guarda una foto por punto en el almacenamiento privado de la app (`filesDir/photos`), siempre reducida y
 * comprimida: son fotos de referencia de campo (una aguada, un cerco), no fotos en alta resolución.
 */
object PhotoStorage {
    private const val MAX_DIMENSION_PX = 1024
    private const val JPEG_QUALITY = 70

    private fun photosDir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    /** Archivo nuevo (vacío) para que la cámara escriba en él, y su content:// Uri para el intent de captura. */
    fun newCaptureTarget(context: Context): Pair<File, Uri> {
        val file = File(photosDir(context), "foto_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /** Reduce y comprime la foto recién tomada en el mismo archivo (la cámara escribe a resolución completa). */
    fun downscaleInPlace(file: File) {
        val bitmap = decodeScaled(file.absolutePath) ?: return
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        bitmap.recycle()
    }

    /** Copia una imagen elegida en la galería al almacenamiento privado, reducida y comprimida. @return ruta absoluta. */
    fun persistFromGallery(context: Context, sourceUri: Uri): String? {
        val tempFile = File(photosDir(context), "tmp_${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        val bitmap = decodeScaled(tempFile.absolutePath)
        tempFile.delete()
        if (bitmap == null) return null
        val destFile = File(photosDir(context), "foto_${System.currentTimeMillis()}.jpg")
        FileOutputStream(destFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        bitmap.recycle()
        return destFile.absolutePath
    }

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }

    /** Decodifica con el menor inSampleSize que mantenga el lado mayor por debajo de [MAX_DIMENSION_PX]. */
    private fun decodeScaled(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= MAX_DIMENSION_PX || bounds.outHeight / (sampleSize * 2) >= MAX_DIMENSION_PX) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(path, options)
    }
}
