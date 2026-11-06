package cl.bovinoapp.rumboaustral.map

import android.content.Context
import cl.bovinoapp.rumboaustral.data.Point
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

    /** Waypoint hacia el que se esta navegando (null = sin navegacion). Persistido en [prefs]. */
    val navigationTargetId = MutableStateFlow<Long?>(
        if (prefs.contains(KEY_NAV_ID)) prefs.getLong(KEY_NAV_ID, 0L) else null
    )

    /** Destino sin guardar (coordenadas del formulario). Persistido; excluyente con [navigationTargetId]. */
    val temporaryDestination = MutableStateFlow<Point?>(
        if (prefs.contains(KEY_TMP_LAT) && prefs.contains(KEY_TMP_LON)) {
            Point(
                id = 0L,
                name = "Destino",
                latitude = Double.fromBits(prefs.getLong(KEY_TMP_LAT, 0L)),
                longitude = Double.fromBits(prefs.getLong(KEY_TMP_LON, 0L))
            )
        } else null
    )

    /** Si el mapa agrupa los marcadores cercanos en círculos con su cantidad. Elección del usuario, persistida; por defecto no. */
    val clusteringEnabled = MutableStateFlow(prefs.getBoolean(KEY_CLUSTERING, false))

    fun setClustering(enabled: Boolean) {
        clusteringEnabled.value = enabled
        prefs.edit().putBoolean(KEY_CLUSTERING, enabled).apply()
    }

    private fun persistNavigation() {
        val id = navigationTargetId.value
        val tmp = temporaryDestination.value
        prefs.edit().apply {
            if (id != null) putLong(KEY_NAV_ID, id) else remove(KEY_NAV_ID)
            if (tmp != null) {
                putLong(KEY_TMP_LAT, tmp.latitude.toBits())
                putLong(KEY_TMP_LON, tmp.longitude.toBits())
            } else {
                remove(KEY_TMP_LAT)
                remove(KEY_TMP_LON)
            }
        }.apply()
    }

    fun navigateToPoint(id: Long) {
        temporaryDestination.value = null
        fittedTargetId = null
        navigationTargetId.value = id
        persistNavigation()
    }

    fun navigateToCoordinates(latitude: Double, longitude: Double) {
        navigationTargetId.value = null
        fittedTargetId = null
        temporaryDestination.value = Point(id = 0L, name = "Destino", latitude = latitude, longitude = longitude)
        persistNavigation()
    }

    /** Termina la navegacion y descarta cualquier destino (guardado o temporal). */
    fun stopNavigation() {
        navigationTargetId.value = null
        temporaryDestination.value = null
        fittedTargetId = null
        persistNavigation()
    }

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
        const val KEY_NAV_ID = "nav_target_id"
        const val KEY_TMP_LAT = "nav_tmp_lat"
        const val KEY_TMP_LON = "nav_tmp_lon"
        const val KEY_CLUSTERING = "clustering_enabled"
    }
}
