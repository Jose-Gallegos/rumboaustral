package cl.bovinoapp.rumboaustral.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class LocationState(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long,
    /** Rumbo de desplazamiento reportado por el GPS, null si el equipo esta quieto. */
    val gpsCourseDegrees: Float?
)

/** Satélites que el receptor GNSS ve ([visible]) y cuántos usa para calcular la posición ([used]). */
data class SatelliteStatus(val used: Int, val visible: Int)

/** Edad de una ubicacion a partir de la cual se advierte que puede estar desactualizada. */
const val LOCATION_STALE_AFTER_MILLIS = 15_000L

/** Precision por sobre la cual se advierte que el GPS esta impreciso. */
const val LOCATION_LOW_ACCURACY_METERS = 30f

class LocationTracker(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<LocationState> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toState()) }
            }
        }
        client.requestLocationUpdates(request, callback, null)
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Estado de los satélites (GPS, GLONASS, Galileo, BeiDou…) mientras se observa; null = sin datos. Emite null al
     * empezar (para no arrastrar un conteo viejo de una observación anterior) y cuando el sistema detiene el GNSS
     * (por ejemplo, al desactivar la ubicación). Termina sin más emisiones si no hay permiso o el equipo no expone el
     * estado GNSS; en ese caso la interfaz simplemente no lo muestra.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // la variante con Executor exige API 30; la app soporta desde la 26
    fun observeSatellites(): Flow<SatelliteStatus?> = callbackFlow {
        trySend(null)
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val visible = status.satelliteCount
                val used = (0 until visible).count { status.usedInFix(it) }
                trySend(SatelliteStatus(used, visible))
            }

            // El sistema avisa así de que el GNSS se detuvo: el último conteo ya no es válido.
            override fun onStopped() {
                trySend(null)
            }
        }
        val registered = try {
            locationManager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            false
        }
        if (!registered) {
            close()
            return@callbackFlow
        }
        awaitClose { locationManager.unregisterGnssStatusCallback(callback) }
    }

    /** Ultima ubicacion conocida por el sistema (puede ser null o antigua). No activa el GPS. */
    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): LocationState? = try {
        suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { cont.resume(it?.toState()) }
                .addOnFailureListener { cont.resume(null) }
        }
    } catch (e: SecurityException) {
        null
    }

    private fun Location.toState() = LocationState(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        timestampMillis = time,
        gpsCourseDegrees = if (hasBearing() && hasSpeed() && speed > 0.5f) bearing else null
    )
}
