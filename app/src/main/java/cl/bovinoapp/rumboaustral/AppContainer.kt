package cl.bovinoapp.rumboaustral

import android.content.Context
import cl.bovinoapp.rumboaustral.data.AppDatabase
import cl.bovinoapp.rumboaustral.data.PointsRepository
import cl.bovinoapp.rumboaustral.location.CompassTracker
import cl.bovinoapp.rumboaustral.location.LocationTracker
import cl.bovinoapp.rumboaustral.map.OfflineRegionsRepository

/** Contenedor manual de dependencias: la app es pequena y no justifica un framework de DI. */
class AppContainer(context: Context) {
    val pointsRepository = PointsRepository(AppDatabase.get(context).pointDao())
    val locationTracker = LocationTracker(context)
    val compassTracker = CompassTracker(context)
    val offlineRegionsRepository = OfflineRegionsRepository()
}
