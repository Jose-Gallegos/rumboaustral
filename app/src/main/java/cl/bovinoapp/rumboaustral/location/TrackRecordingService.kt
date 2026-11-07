package cl.bovinoapp.rumboaustral.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import cl.bovinoapp.rumboaustral.R
import cl.bovinoapp.rumboaustral.data.AppDatabase
import cl.bovinoapp.rumboaustral.data.TrackPoint
import cl.bovinoapp.rumboaustral.data.TracksRepository
import cl.bovinoapp.rumboaustral.geo.distanceMeters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Graba un recorrido en primer plano (sigue funcionando con la pantalla apagada o la app en 2do plano),
 * con una notificación persistente como exige Android para un servicio de ubicación en 1er plano.
 */
class TrackRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var repository: TracksRepository
    private lateinit var notificationManager: NotificationManager
    private var callback: LocationCallback? = null

    private var trackId: Long = 0
    private var sequence = 0
    private var lastFix: Pair<Double, Double>? = null
    private var distanceMeters = 0.0
    // Cada escritura encadena a la anterior: finishTrack() espera esta cadena, asi no corre
    // en paralelo con un appendPoints() todavia pendiente del ultimo fix (perderia ese punto,
    // o peor, onDestroy() podria cancelar esa insercion al detener el servicio).
    private var pendingWrites: Job? = null

    private fun enqueueWrite(block: suspend () -> Unit) {
        val previous = pendingWrites
        pendingWrites = scope.launch {
            previous?.join()
            block()
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        repository = TracksRepository(AppDatabase.get(applicationContext).trackDao())
        notificationManager = getSystemService(NotificationManager::class.java)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent.getStringExtra(EXTRA_NAME) ?: "Recorrido")
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        callback?.let { fusedClient.removeLocationUpdates(it) }
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(name: String) {
        val startedAt = System.currentTimeMillis()
        sequence = 0
        distanceMeters = 0.0
        lastFix = null
        trackId = 0
        startForeground(NOTIFICATION_ID, buildNotification(name, 0.0))
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        // El GPS no debe suscribirse hasta tener el ID persistido: un fix que llegara antes
        // intentaria insertar un TrackPoint con trackId = 0, inexistente, y violaria la clave foranea.
        scope.launch {
            trackId = repository.startTrack(name, startedAt)
            TrackRecordingState.update(TrackRecordingState.Snapshot(trackId, name, 0.0, startedAt))
            startLocationUpdates(name)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(name: String) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .build()
        val newCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                onNewFix(name, location.latitude, location.longitude, System.currentTimeMillis())
            }
        }
        callback = newCallback
        fusedClient.requestLocationUpdates(request, newCallback, Looper.getMainLooper())
    }

    private fun onNewFix(name: String, latitude: Double, longitude: Double, timestamp: Long) {
        val point = TrackPoint(
            trackId = trackId, latitude = latitude, longitude = longitude,
            timestampMillis = timestamp, sequence = sequence++
        )
        lastFix?.let { (lat, lon) -> distanceMeters += distanceMeters(lat, lon, latitude, longitude) }
        lastFix = latitude to longitude
        enqueueWrite { repository.appendPoints(listOf(point)) }
        TrackRecordingState.snapshot.value?.let {
            TrackRecordingState.update(it.copy(distanceMeters = distanceMeters))
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(name, distanceMeters))
    }

    private fun stopRecording() {
        callback?.let { fusedClient.removeLocationUpdates(it) }
        callback = null
        val id = trackId
        val endedAt = System.currentTimeMillis()
        // stopSelf() se llama DESPUES de terminar de guardar: onDestroy() cancela este mismo scope,
        // asi que detener el servicio antes de que finishTrack termine le cortaria el guardado final.
        // Tambien se espera la cadena de escrituras pendientes, para no calcular estadisticas sin el
        // ultimo fix ni cancelar su insercion a mitad de camino.
        val lastWrite = pendingWrites
        scope.launch {
            lastWrite?.join()
            if (id != 0L) {
                repository.finishTrack(id, endedAt)
                TrackRecordingState.update(null)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(name: String, distanceMeters: Double): Notification {
        val km = distanceMeters / 1000.0
        val stopIntent = Intent(this, TrackRecordingService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Grabando: $name")
            .setContentText("%.1f km".format(km))
            .setOngoing(true)
            .addAction(0, "Detener", stopPendingIntent)
            .build()
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Grabación de recorridos", NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "cl.bovinoapp.rumboaustral.action.START_TRACK"
        const val ACTION_STOP = "cl.bovinoapp.rumboaustral.action.STOP_TRACK"
        const val EXTRA_NAME = "name"
        private const val CHANNEL_ID = "track_recording"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, name: String) {
            val intent = Intent(context, TrackRecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_NAME, name)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TrackRecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}
