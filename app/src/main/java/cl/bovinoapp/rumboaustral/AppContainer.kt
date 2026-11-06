package cl.bovinoapp.rumboaustral

import android.content.Context
import cl.bovinoapp.rumboaustral.data.AppDatabase
import cl.bovinoapp.rumboaustral.data.LabelsRepository
import cl.bovinoapp.rumboaustral.data.PointFilterStore
import cl.bovinoapp.rumboaustral.data.PointsRepository
import cl.bovinoapp.rumboaustral.location.CompassTracker
import cl.bovinoapp.rumboaustral.location.LocationTracker
import cl.bovinoapp.rumboaustral.map.MapStateStore
import cl.bovinoapp.rumboaustral.map.OfflineRegionsRepository

/** Contenedor manual de dependencias: la app es pequena y no justifica un framework de DI. */
class AppContainer(context: Context) {
    private val database = AppDatabase.get(context)
    val pointsRepository = PointsRepository(database.pointDao())
    val labelsRepository = LabelsRepository(database.labelDao())
    val pointFilterStore = PointFilterStore()
    val locationTracker = LocationTracker(context)
    val compassTracker = CompassTracker(context)
    val offlineRegionsRepository = OfflineRegionsRepository()
    val mapStateStore = MapStateStore(context)
}
