package cl.bovinoapp.rumboaustral

import android.app.Application
import com.mapbox.common.MapboxOptions

class RumboAustralApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.MAPBOX_ACCESS_TOKEN.isNotBlank()) {
            MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
        }
        container = AppContainer(this)
    }
}
