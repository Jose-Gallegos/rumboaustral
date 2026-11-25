package cl.bovinoapp.rumboaustral.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Marcador de resaltado para el punto que está bajo la cruz del mapa: más grande que el normal, con un halo
 * oscuro (se distingue sobre imagen clara) y un aro blanco grueso alrededor del color del punto.
 */
fun createHighlightBitmap(fillColor: Int, diameterPx: Int = 84): Bitmap {
    val bitmap = Bitmap.createBitmap(diameterPx, diameterPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = diameterPx / 2f
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 0, 0, 0) }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    canvas.drawCircle(radius, radius, radius * 0.97f, halo)
    canvas.drawCircle(radius, radius, radius * 0.78f, ring)
    canvas.drawCircle(radius, radius, radius * 0.58f, fill)
    return bitmap
}

/** Genera un marcador circular de color [fillColor] (ARGB) con borde blanco, sin depender de recursos. */
fun createPointMarkerBitmap(diameterPx: Int = 48, fillColor: Int = Color.RED): Bitmap {
    val bitmap = Bitmap.createBitmap(diameterPx, diameterPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = diameterPx / 2f

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = diameterPx * 0.12f
    }

    canvas.drawCircle(radius, radius, radius * 0.75f, fillPaint)
    canvas.drawCircle(radius, radius, radius * 0.75f, borderPaint)
    return bitmap
}

/**
 * Icono invisible (totalmente transparente, sin dibujar nada) para un marcador que solo existe para poder
 * consultarse por posición en pantalla (p. ej. el sondeo de la cruz sobre los puntos de un recorrido guardado),
 * sin agregar nada visible sobre la línea que ya lo representa.
 */
fun createInvisibleBitmap(sizePx: Int = 8): Bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)

/** Bandera a cuadros con el pie del mástil centrado en el borde inferior. */
fun createDestinationFlagBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(76, 56, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val poleX = bitmap.width / 2f

    paint.color = Color.BLACK
    paint.strokeWidth = 8f
    canvas.drawLine(poleX, 4f, poleX, 54f, paint)
    canvas.drawRoundRect(poleX - 4f, 3f, poleX + 34f, 33f, 4f, 4f, paint)

    paint.color = Color.WHITE
    paint.strokeWidth = 4f
    canvas.drawLine(poleX, 7f, poleX, 54f, paint)
    canvas.drawRect(poleX, 6f, poleX + 30f, 30f, paint)

    paint.color = Color.BLACK
    for (row in 0 until 3) {
        for (column in 0 until 3) {
            if ((row + column) % 2 == 0) {
                val left = poleX + column * 10f
                val top = 6f + row * 8f
                canvas.drawRect(left, top, left + 10f, top + 8f, paint)
            }
        }
    }
    return bitmap
}
