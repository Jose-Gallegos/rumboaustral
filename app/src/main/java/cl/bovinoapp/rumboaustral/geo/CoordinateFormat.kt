package cl.bovinoapp.rumboaustral.geo

/**
 * Formatos de presentacion de coordenadas. Por ahora solo esta implementado
 * [DecimalDegrees]; UTM y grados/minutos/segundos quedan como variantes futuras
 * del mismo sellado, para que la UI (lista, edicion, navegacion) no dependa
 * del formato concreto sino de esta interfaz.
 */
sealed interface CoordinateFormat {
    fun format(latitude: Double, longitude: Double): String

    data object DecimalDegrees : CoordinateFormat {
        override fun format(latitude: Double, longitude: Double): String =
            "%.6f, %.6f".format(latitude, longitude)
    }
}

fun defaultCoordinateFormat(): CoordinateFormat = CoordinateFormat.DecimalDegrees
