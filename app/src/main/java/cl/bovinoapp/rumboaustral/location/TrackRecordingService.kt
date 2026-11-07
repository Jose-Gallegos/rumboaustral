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
    // true desde que se acepta un ACTION_START hasta que termina el ACTION_STOP correspondiente.
    // onStartCommand corre siempre en el hilo principal y de a uno por Intent, asi que esta
    // bandera alcanza para que un doble toque en "grabar" no cree dos recorridos a la vez.
    private var isRecording = false
    // Se activa al pedir "Detener": bloquea nuevas escrituras desde onNewFix aunque todavia
    // lleguen fixes mientras se espera que termine lo ya encolado.
    private var isStopping = false

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
            // isRecording sigue en true durante todo el cierre (se apaga recien al terminar
            // stopRecording, no aca): asi un inicio que llegue mientras el cierre anterior
            // todavia esta en curso no reinicia trackId/isStopping a mitad de camino.
            ACTION_START -> if (!isRecording && !isStopping) {
                isRecording = true
                startRecording(intent.getStringExtra(EXTRA_NAME) ?: "Recorrido")
            }
            ACTION_STOP -> if (isRecording && !isStopping) {
                stopRecording()
            }
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
        // isStopping no hace falta resetearlo aca: onStartCommand ya rechaza un ACTION_START
        // mientras isStopping sea true, asi que si llegamos aca siempre esta en false.
        // startForeground() va primero e incondicional: Android exige llamarlo enseguida tras
        // startForegroundService(), sin importar lo que pase despues (si no, se cae con una ANR).
        startForeground(NOTIFICATION_ID, buildNotification(name, 0.0))
        // El resto de la app (MainActivity) deja entrar con ubicacion aproximada o precisa: exigir
        // solo la precisa aca hacia que el boton de grabar pareciera no hacer nada para quien solo
        // concedio la aproximada. Se graba con la que haya (menos precision, pero funciona).
        val hasLocationPermission =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) {
            isRecording = false
            android.widget.Toast.makeText(
                this, "Sin permiso de ubicación: no se puede grabar el recorrido", android.widget.Toast.LENGTH_LONG
            ).show()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        // El GPS no debe suscribirse hasta tener el ID persistido: un fix que llegara antes
        // intentaria insertar un TrackPoint con trackId = 0, inexistente, y violaria la clave foranea.
        // Se encola en la misma cadena que las escrituras de puntos (en vez de un scope.launch aparte)
        // para que "Detener" (que espera el ultimo eslabon de esa cadena) nunca lea trackId = 0 por
        // haberse ejecutado antes de que esto termine.
        enqueueWrite {
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
        // onLocationResult y onStartCommand corren siempre en el hilo principal: una vez que
        // stopRecording() marca isStopping, ningun fix posterior (en ese mismo hilo) vuelve a encolar
        // una escritura, sin importar cuanto tarde en resolverse la cadena ya encolada.
        if (isStopping) return
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
        // Orden exacto: (1) bloquear fixes nuevos ANTES que nada, (2) esperar lo ya encolado (el
        // inicio, si todavia estaba en curso, mas cualquier fix que alcanzo a entrar antes del
        // bloqueo), (3) recien ahi retirar la suscripcion al GPS, (4) drenar lo que se haya
        // encolado mientras se esperaba/desuscribia, y solo entonces calcular las estadisticas
        // finales. stopSelf() se llama al final: onDestroy() cancela este mismo scope, asi que
        // detener el servicio antes le cortaria el guardado.
        isStopping = true
        val endedAt = System.currentTimeMillis()
        val writesBeforeStop = pendingWrites
        scope.launch {
            writesBeforeStop?.join()
            callback?.let { fusedClient.removeLocationUpdates(it) }
            callback = null
            pendingWrites?.join()
            val id = trackId
            if (id != 0L) {
                repository.finishTrack(id, endedAt)
                TrackRecordingState.update(null)
            }
            // isStopping/isRecording NO se resetean aca: esta corrutina corre en Dispatchers.IO, y
            // stopSelf() solo PIDE destruir el servicio (no es inmediato) - onStartCommand sigue
            // corriendo en el hilo principal mientras tanto. Si se liberaran aca, un ACTION_START
            // que llegara justo en ese hueco reusaria esta misma instancia (y su mismo "scope"), y
            // cuando Android recien destruya esta instancia, onDestroy() cancelaria el scope y se
            // llevaria puesta la grabacion nueva. Se quedan en true hasta que onDestroy() se lleve
            // la instancia entera: la siguiente instancia (si hay una) arranca sus campos en false.
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
