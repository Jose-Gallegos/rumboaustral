package cl.bovinoapp.rumboaustral.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PointsRepository(private val dao: PointDao) {
    fun observeAll(): Flow<List<Point>> = dao.observeAll()

    suspend fun getById(id: Long): Point? = dao.getById(id)

    suspend fun save(point: Point): Long = dao.insert(point)

    /** Siguiente nombre libre con el formato "Waypoint N" (N = mayor sufijo existente + 1). */
    suspend fun nextDefaultName(): String {
        val regex = Regex("^Waypoint (\\d+)$", RegexOption.IGNORE_CASE)
        val max = observeAll().first()
            .mapNotNull { regex.find(it.name.trim())?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "Waypoint ${max + 1}"
    }

    suspend fun update(point: Point) = dao.update(point)

    /** Actualiza nombre, coordenadas, notas y etiquetas (no la fecha). @return filas actualizadas (0 si el punto ya no existe). */
    suspend fun updateDetails(point: Point): Int = dao.updateDetails(
        point.id, point.name, point.latitude, point.longitude, point.notes,
        point.typeLabelId, point.statusLabelId, point.zoneLabelId, point.photoPath
    )

    /**
     * Asigna [labelId] (null = quitar la etiqueta) a [pointIds] en la categoría [kind], pisando lo que tengan.
     * @return puntos modificados, o null si la etiqueta no existe o es de otra categoría.
     */
    suspend fun assignLabel(kind: LabelKind, pointIds: Collection<Long>, labelId: Long?): Int? =
        dao.assignLabels(kind, listOf(labelId to pointIds.toList()), overwrite = true)

    /**
     * Aplica un plan de autoclasificación (categoría -> etiqueta -> IDs de puntos) sin pisar etiquetas ya
     * asignadas, todo o nada. @return puntos modificados; lanza excepción si el plan es inválido.
     */
    suspend fun applyAutoClassification(plan: Map<LabelKind, Map<Long, List<Long>>>): Int =
        dao.autoClassify(plan.mapValues { (_, byLabel) -> byLabel.map { (labelId, ids) -> labelId to ids } })

    suspend fun delete(point: Point) = dao.delete(point)

    /** Inserta los puntos con sus etiquetas (creando las que falten), todo o nada. @return puntos insertados. */
    suspend fun importWithLabels(entries: List<Pair<Point, List<LabelRef>>>): Int = dao.importWithLabels(entries)
}
