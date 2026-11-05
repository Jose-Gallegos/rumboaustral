package cl.bovinoapp.rumboaustral.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
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

    private fun Location.toState() = LocationState(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        timestampMillis = time,
        gpsCourseDegrees = if (hasBearing() && hasSpeed() && speed > 0.5f) bearing else null
    )
}
