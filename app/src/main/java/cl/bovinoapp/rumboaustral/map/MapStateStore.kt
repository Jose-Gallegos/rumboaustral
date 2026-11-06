package cl.bovinoapp.rumboaustral.map

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

data class CameraSnapshot(val latitude: Double, val longitude: Double, val zoom: Double)

data class LatLon(val latitude: Double, val longitude: Double)

/**
 * Estado del mapa que debe sobrevivir a la pantalla:
 * - [camera]: posicion y zoom al salir de la pestana (solo en memoria, para restaurarlos al volver).
 * - [autoCenterPending]: si aun falta el centrado automatico sobre el GPS (solo en memoria).
 * - [lastLocation]: ultima ubicacion conocida, persistida para usarla al abrir sin senal GPS.
 */
class MapStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("map_state", Context.MODE_PRIVATE)

    /** Waypoint hacia el que se esta navegando (null = sin navegacion). Solo en memoria. */
    val navigationTargetId = MutableStateFlow<Long?>(null)

    /** Destino para el que ya se encuadro la camara, para no reencuadrar al volver a la pestana. */
    @Volatile var fittedTargetId: Long? = null

    @Volatile var camera: CameraSnapshot? = null
    @Volatile var autoCenterPending: Boolean = true

    var lastLocation: LatLon?
        get() = if (prefs.contains(KEY_LAT) && prefs.contains(KEY_LON)) {
            LatLon(
                Double.fromBits(prefs.getLong(KEY_LAT, 0L)),
                Double.fromBits(prefs.getLong(KEY_LON, 0L))
            )
        } else null
        set(value) {
            if (value == null) return
            prefs.edit()
                .putLong(KEY_LAT, value.latitude.toBits())
                .putLong(KEY_LON, value.longitude.toBits())
                .apply()
        }

    private companion object {
        const val KEY_LAT = "last_lat"
        const val KEY_LON = "last_lon"
    }
}
