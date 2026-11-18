package cl.bovinoapp.rumboaustral

import android.content.Context
import cl.bovinoapp.rumboaustral.data.AppDatabase
import cl.bovinoapp.rumboaustral.data.LabelsRepository
import cl.bovinoapp.rumboaustral.data.PointFilterStore
import cl.bovinoapp.rumboaustral.data.PointsRepository
import cl.bovinoapp.rumboaustral.data.TracksRepository
import cl.bovinoapp.rumboaustral.location.CompassTracker
import cl.bovinoapp.rumboaustral.location.LocationTracker
import cl.bovinoapp.rumboaustral.map.MapStateStore
import cl.bovinoapp.rumboaustral.map.OfflineRegionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Contenedor manual de dependencias: la app es pequena y no justifica un framework de DI. */
class AppContainer(context: Context) {
    private val database = AppDatabase.get(context)
    val pointsRepository = PointsRepository(database.pointDao())
    val labelsRepository = LabelsRepository(database.labelDao())
    val tracksRepository = TracksRepository(database.trackDao())
    val pointFilterStore = PointFilterStore()
    val locationTracker = LocationTracker(context)
    val compassTracker = CompassTracker(context)
    val offlineRegionsRepository = OfflineRegionsRepository()
    val mapStateStore = MapStateStore(context)

    /**
     * Cierra los recorridos que quedaron sin terminar en un proceso anterior (crash, kill del sistema): el servicio
     * no se reinicia solo, así que ninguno sigue grabándoles. TrackRecordingService espera a que este trabajo
     * termine antes de crear un recorrido nuevo; así, mientras corre, no existe ningún recorrido vivo que pudiera
     * cerrarse por error y no hace falta distinguirlos por fecha.
     */
    val interruptedRecovery: Job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        // Un fallo aquí no debe tumbar la app ni bloquear grabaciones nuevas: el recorrido seguirá "interrumpido".
        try {
            tracksRepository.recoverInterrupted()
        } catch (e: Exception) {
            android.util.Log.w("AppContainer", "No se pudieron recuperar recorridos interrumpidos", e)
        }
    }
}
