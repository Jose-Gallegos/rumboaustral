package cl.bovinoapp.rumboaustral.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** Distancia en linea recta (formula de Haversine), en metros. */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    val deltaLambda = Math.toRadians(lon2 - lon1)
    val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
        cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}

/** Rumbo inicial (0-360 grados, 0 = norte verdadero) desde el punto 1 hacia el punto 2. */
fun initialBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLambda = Math.toRadians(lon2 - lon1)
    val y = sin(deltaLambda) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
    val theta = atan2(y, x)
    return (Math.toDegrees(theta) + 360.0) % 360.0
}

/** Normaliza un angulo a el rango [0, 360). */
fun normalizeDegrees(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

/** Diferencia angular con signo mas corta entre dos rumbos, en el rango (-180, 180]. */
fun angularDifferenceDegrees(from: Double, to: Double): Double {
    var diff = normalizeDegrees(to) - normalizeDegrees(from)
    if (diff > 180.0) diff -= 360.0
    if (diff <= -180.0) diff += 360.0
    return diff
}

fun formatDistance(meters: Double): String = if (meters >= 1000.0) {
    "%.2f km".format(meters / 1000.0)
} else {
    "%.0f m".format(meters)
}

fun formatBearing(degrees: Double): String = "%.0f°".format(normalizeDegrees(degrees))

fun isValidLatitude(latitude: Double): Boolean = latitude.isFinite() && latitude in -90.0..90.0

fun isValidLongitude(longitude: Double): Boolean = longitude.isFinite() && longitude in -180.0..180.0
