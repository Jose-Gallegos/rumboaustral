package cl.bovinoapp.rumboaustral.map

import com.mapbox.geojson.Point as GeoPoint
import com.mapbox.maps.MapboxMapsOptions
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.TilesetDescriptorOptions
import com.mapbox.common.TileRegion
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.common.Cancelable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Nombre del estilo satelital con caminos y nombres superpuestos, similar a Microsoft Hybrid. */
const val OFFLINE_STYLE_URI = Style.SATELLITE_STREETS

data class RegionProgress(
    val id: String,
    val completedResourceCount: Long,
    val requiredResourceCount: Long
) {
    val fraction: Float
        get() = if (requiredResourceCount <= 0L) 0f else
            (completedResourceCount.toFloat() / requiredResourceCount.toFloat()).coerceIn(0f, 1f)
}

data class DownloadedRegion(
    val id: String,
    val completedResourceSize: Long,
    val completedResourceCount: Long,
    val requiredResourceCount: Long
) {
    val isComplete: Boolean get() = requiredResourceCount > 0 && completedResourceCount >= requiredResourceCount
}

class OfflineRegionsRepository {
    private val offlineManager = OfflineManager()
    private val tileStore: TileStore = MapboxMapsOptions.tileStore
        ?: TileStore.create()

    suspend fun listRegions(): List<DownloadedRegion> = suspendCancellableCoroutine { cont ->
        tileStore.getAllTileRegions { expected ->
            if (expected.isValue) {
                val regions = expected.value.orEmpty().map { it.toDownloadedRegion() }
                cont.resume(regions)
            } else {
                cont.resumeWithException(IllegalStateException(expected.error?.message))
            }
        }
    }

    fun download(
        id: String,
        center: GeoPoint,
        radiusMetersSide: Double,
        minZoom: Byte = 10,
        maxZoom: Byte = 15
    ): Flow<RegionProgress> = callbackFlow {
        val bounds = squareAround(center, radiusMetersSide)
        var regionCancelable: Cancelable? = null

        val stylePackOptions = com.mapbox.maps.StylePackLoadOptions.Builder()
            .glyphsRasterizationMode(com.mapbox.maps.GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
            .build()

        val styleCancelable: Cancelable = offlineManager.loadStylePack(
            OFFLINE_STYLE_URI,
            stylePackOptions,
            { },
            { stylePackExpected ->
                if (!stylePackExpected.isValue) {
                    close(IllegalStateException(stylePackExpected.error?.message))
                    return@loadStylePack
                }

                val descriptorOptions = TilesetDescriptorOptions.Builder()
                    .styleURI(OFFLINE_STYLE_URI)
                    .minZoom(minZoom)
                    .maxZoom(maxZoom)
                    .build()
                val descriptor = offlineManager.createTilesetDescriptor(descriptorOptions)

                val loadOptions = TileRegionLoadOptions.Builder()
                    .geometry(bounds)
                    .descriptors(listOf(descriptor))
                    .acceptExpired(true)
                    .networkRestriction(com.mapbox.common.NetworkRestriction.NONE)
                    .build()

                regionCancelable = tileStore.loadTileRegion(
                    id,
                    loadOptions,
                    { progress ->
                        trySend(
                            RegionProgress(
                                id = id,
                                completedResourceCount = progress.completedResourceCount,
                                requiredResourceCount = progress.requiredResourceCount
                            )
                        )
                    },
                    { regionExpected ->
                        if (regionExpected.isValue) {
                            close()
                        } else {
                            close(IllegalStateException(regionExpected.error?.message))
                        }
                    }
                )
            }
        )
        awaitClose {
            styleCancelable.cancel()
            regionCancelable?.cancel()
        }
    }

    /**
     * Borra solo las teselas de la region. El paquete de estilo (OFFLINE_STYLE_URI) es
     * compartido por todas las regiones descargadas, asi que no se elimina aqui: si se
     * quitara, el resto de las regiones quedarian sin estilo para mostrarse sin conexion.
     */
    suspend fun delete(id: String) {
        tileStore.removeTileRegion(id)
    }

    private fun TileRegion.toDownloadedRegion() = DownloadedRegion(
        id = id,
        completedResourceSize = completedResourceSize,
        completedResourceCount = completedResourceCount,
        requiredResourceCount = requiredResourceCount
    )

    private fun squareAround(center: GeoPoint, sideMeters: Double): com.mapbox.geojson.Geometry {
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLon = 111_320.0 * kotlin.math.cos(Math.toRadians(center.latitude()))
        val halfLat = (sideMeters / 2.0) / metersPerDegreeLat
        val halfLon = (sideMeters / 2.0) / metersPerDegreeLon
        val minLat = center.latitude() - halfLat
        val maxLat = center.latitude() + halfLat
        val minLon = center.longitude() - halfLon
        val maxLon = center.longitude() + halfLon
        return com.mapbox.geojson.Polygon.fromLngLats(
            listOf(
                listOf(
                    GeoPoint.fromLngLat(minLon, minLat),
                    GeoPoint.fromLngLat(maxLon, minLat),
                    GeoPoint.fromLngLat(maxLon, maxLat),
                    GeoPoint.fromLngLat(minLon, maxLat),
                    GeoPoint.fromLngLat(minLon, minLat)
                )
            )
        )
    }
}
