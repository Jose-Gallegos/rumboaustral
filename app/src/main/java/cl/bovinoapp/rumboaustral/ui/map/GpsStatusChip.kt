package cl.bovinoapp.rumboaustral.ui.map

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.bovinoapp.rumboaustral.location.LOCATION_LOW_ACCURACY_METERS
import cl.bovinoapp.rumboaustral.location.LOCATION_STALE_AFTER_MILLIS
import cl.bovinoapp.rumboaustral.location.LocationState
import cl.bovinoapp.rumboaustral.location.SatelliteStatus
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Pastilla con el estado del GPS: precisión y satélites ("GPS ±5 m · 14/22 sat", donde 14 son los que se usan para
 * calcular la posición y 22 los que se ven). Se pone de color de error si la precisión es baja o la última posición
 * es antigua; así se nota un GPS malo antes de fiarse de una distancia.
 */
@Composable
fun GpsStatusChip(location: LocationState?, satellites: SatelliteStatus?, modifier: Modifier = Modifier) {
    // La posición deja de llegar si se pierde la señal: un reloj propio detecta que la última quedó vieja.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000L)
            value = System.currentTimeMillis()
        }
    }
    val stale = location != null && now - location.timestampMillis > LOCATION_STALE_AFTER_MILLIS
    val lowAccuracy = location != null && location.accuracyMeters >= LOCATION_LOW_ACCURACY_METERS
    val warning = location == null || stale || lowAccuracy

    val accuracy = when {
        location == null -> "buscando…"
        stale -> "sin señal"
        location.accuracyMeters == Float.MAX_VALUE -> "±? m"
        else -> "±${location.accuracyMeters.roundToInt()} m"
    }
    val text = buildString {
        append("GPS ").append(accuracy)
        if (satellites != null) append(" · ").append(satellites.used).append('/').append(satellites.visible).append(" sat")
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = (if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface).copy(alpha = 0.88f),
        contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
