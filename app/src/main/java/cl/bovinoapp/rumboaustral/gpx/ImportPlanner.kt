package cl.bovinoapp.rumboaustral.gpx

import cl.bovinoapp.rumboaustral.data.LabelImport
import cl.bovinoapp.rumboaustral.data.LabelRef
import cl.bovinoapp.rumboaustral.data.Point
import cl.bovinoapp.rumboaustral.geo.distanceMeters

enum class ImportStatus { NEW, DUPLICATE_EXISTING, DUPLICATE_IN_FILE }

data class ImportCandidate(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val notes: String,
    val status: ImportStatus,
    /** Etiquetas del archivo (una por categoría como máximo), ya limpias; se resuelven al guardar. */
    val labels: List<LabelRef> = emptyList()
) {
    fun toPoint() = Point(name = name, latitude = latitude, longitude = longitude, notes = notes)
}

data class InvalidEntry(val label: String, val reason: String)

data class ImportPlan(
    val candidates: List<ImportCandidate>,
    val invalid: List<InvalidEntry>,
    val truncated: Boolean
)

/** Valida las entradas leidas y las clasifica como nuevas o duplicadas. Logica pura (sin Android). */
object ImportPlanner {
    const val DEFAULT_NAME = "Punto importado"
    const val MAX_NAME = 120
    const val MAX_NOTES = 2_000

    /** Mismo nombre y a menos de esta distancia (metros) se considera duplicado. */
    const val DUPLICATE_RADIUS_METERS = 10.0

    fun plan(file: ParsedFile, existing: List<Point>): ImportPlan {
        // nombre normalizado -> coordenadas ya conocidas con ese nombre
        val existingByName = HashMap<String, MutableList<Pair<Double, Double>>>()
        existing.forEach { existingByName.getOrPut(normalize(it.name)) { mutableListOf() } += it.latitude to it.longitude }
        val seenInFile = HashMap<String, MutableList<Pair<Double, Double>>>()

        val candidates = mutableListOf<ImportCandidate>()
        val invalid = mutableListOf<InvalidEntry>()

        for (entry in file.entries) {
            val lat = entry.latitude
            val lon = entry.longitude
            val reason = when {
                entry.problem != null -> entry.problem
                lat == null || lon == null || !lat.isFinite() || !lon.isFinite() -> "Coordenadas ilegibles"
                lat !in -90.0..90.0 -> "Latitud fuera de rango (%.4f)".format(lat)
                lon !in -180.0..180.0 -> "Longitud fuera de rango (%.4f)".format(lon)
                else -> null
            }
            if (reason != null || lat == null || lon == null) {
                invalid += InvalidEntry(entry.name.ifBlank { entry.label }, reason ?: "Inválido")
                continue
            }

            val name = entry.name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).ifBlank { DEFAULT_NAME }
            val notes = entry.notes.trim().take(MAX_NOTES)
            val key = normalize(name)

            val status = when {
                existingByName[key].isNear(lat, lon) -> ImportStatus.DUPLICATE_EXISTING
                seenInFile[key].isNear(lat, lon) -> ImportStatus.DUPLICATE_IN_FILE
                else -> ImportStatus.NEW
            }
            seenInFile.getOrPut(key) { mutableListOf() } += lat to lon
            candidates += ImportCandidate(name, lat, lon, notes, status, LabelImport.sanitize(entry.labels))
        }
        return ImportPlan(candidates, invalid, file.truncated)
    }

    private fun normalize(name: String) = name.trim().replace(Regex("\\s+"), " ").lowercase()

    private fun List<Pair<Double, Double>>?.isNear(lat: Double, lon: Double): Boolean =
        this?.any { distanceMeters(lat, lon, it.first, it.second) <= DUPLICATE_RADIUS_METERS } == true
}
