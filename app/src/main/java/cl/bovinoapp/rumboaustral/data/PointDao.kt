package cl.bovinoapp.rumboaustral.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val SQLITE_ID_CHUNK = 500

@Dao
interface PointDao {
    @Query("SELECT * FROM points ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Point>>

    @Query("SELECT * FROM points WHERE id = :id")
    suspend fun getById(id: Long): Point?

    @Insert
    suspend fun insert(point: Point): Long

    @Insert
    suspend fun insertAll(points: List<Point>)

    @Update
    suspend fun update(point: Point)

    // Escribe solo los campos que edita el formulario (no la fecha de creación) en una sola sentencia.
    @Query(
        "UPDATE points SET name = :name, latitude = :latitude, longitude = :longitude, notes = :notes, " +
            "typeLabelId = :typeLabelId, statusLabelId = :statusLabelId, zoneLabelId = :zoneLabelId, " +
            "photoPath = :photoPath WHERE id = :id"
    )
    suspend fun updateDetails(
        id: Long, name: String, latitude: Double, longitude: Double, notes: String,
        typeLabelId: Long?, statusLabelId: Long?, zoneLabelId: Long?, photoPath: String?
    ): Int

    // Asignación masiva de una etiqueta por categoría. Con overwrite = false solo se llena lo que está sin etiqueta.
    @Query("UPDATE points SET typeLabelId = :labelId WHERE id IN (:ids) AND (:overwrite OR typeLabelId IS NULL)")
    suspend fun setTypeLabel(ids: List<Long>, labelId: Long?, overwrite: Boolean): Int

    @Query("UPDATE points SET statusLabelId = :labelId WHERE id IN (:ids) AND (:overwrite OR statusLabelId IS NULL)")
    suspend fun setStatusLabel(ids: List<Long>, labelId: Long?, overwrite: Boolean): Int

    @Query("UPDATE points SET zoneLabelId = :labelId WHERE id IN (:ids) AND (:overwrite OR zoneLabelId IS NULL)")
    suspend fun setZoneLabel(ids: List<Long>, labelId: Long?, overwrite: Boolean): Int

    @Query("SELECT COUNT(*) FROM labels WHERE id = :id AND kind = :kind")
    suspend fun labelExistsInKind(id: Long, kind: LabelKind): Int

    /**
     * Aplica varias asignaciones de la categoría [kind] en una sola transacción: cada par es (etiqueta o null
     * para "sin etiqueta", IDs de puntos). Si alguna etiqueta no existe o es de otra categoría no cambia nada y
     * devuelve null. Si no, devuelve la cantidad de puntos modificados. Con [overwrite] = false no pisa
     * etiquetas ya asignadas.
     */
    @Transaction
    suspend fun assignLabels(kind: LabelKind, assignments: List<Pair<Long?, List<Long>>>, overwrite: Boolean): Int? {
        // Se valida todo antes de escribir, para no dejar una asignación a medias.
        for ((labelId, _) in assignments) {
            if (labelId != null && labelExistsInKind(labelId, kind) == 0) return null
        }
        var changed = 0
        for ((labelId, pointIds) in assignments) {
            // Bloques: SQLite limita las variables por consulta (999 en Android 8).
            for (chunk in pointIds.chunked(SQLITE_ID_CHUNK)) {
                changed += when (kind) {
                    LabelKind.TYPE -> setTypeLabel(chunk, labelId, overwrite)
                    LabelKind.STATUS -> setStatusLabel(chunk, labelId, overwrite)
                    LabelKind.ZONE -> setZoneLabel(chunk, labelId, overwrite)
                }
            }
        }
        return changed
    }

    /**
     * Aplica asignaciones de varias categorías en UNA transacción, sin pisar etiquetas ya asignadas. Si alguna
     * etiqueta es inválida lanza una excepción y se revierte todo (no queda ninguna categoría a medias).
     * @return puntos modificados en total.
     */
    @Transaction
    suspend fun autoClassify(plans: Map<LabelKind, List<Pair<Long?, List<Long>>>>): Int {
        var changed = 0
        for ((kind, assignments) in plans) {
            changed += assignLabels(kind, assignments, overwrite = false)
                ?: throw IllegalStateException("Etiqueta inexistente o de otra categoría en $kind")
        }
        return changed
    }

    @Query("SELECT * FROM labels")
    suspend fun allLabels(): List<Label>

    @Insert
    suspend fun insertLabel(label: Label): Long

    /**
     * Importa puntos junto con las etiquetas que traían (por nombre) en una sola transacción: reutiliza las
     * etiquetas existentes de la misma categoría, crea las que faltan y luego inserta los puntos.
     * @return puntos insertados.
     */
    @Transaction
    suspend fun importWithLabels(entries: List<Pair<Point, List<LabelRef>>>): Int {
        val existing = allLabels()
        val idByKey = existing.associate { (it.kind to LabelImport.key(it.name)) to it.id }.toMutableMap()
        val nextOrder = LabelKind.entries.associateWith { kind ->
            (existing.filter { it.kind == kind }.maxOfOrNull { it.sortOrder } ?: -1) + 1
        }.toMutableMap()
        var created = 0
        for (ref in LabelImport.missing(entries.flatMap { it.second }, existing)) {
            val order = nextOrder.getValue(ref.kind)
            nextOrder[ref.kind] = order + 1
            val color = ref.color ?: LABEL_PALETTE[(existing.size + created) % LABEL_PALETTE.size]
            created++
            idByKey[ref.kind to LabelImport.key(ref.name)] =
                insertLabel(Label(kind = ref.kind, name = ref.name, color = color, sortOrder = order))
        }
        val points = entries.map { (point, refs) ->
            refs.fold(point) { p, ref ->
                idByKey[ref.kind to LabelImport.key(ref.name)]?.let { p.withLabel(ref.kind, it) } ?: p
            }
        }
        insertAll(points)
        return points.size
    }

    @Delete
    suspend fun delete(point: Point)

    @Query("DELETE FROM points WHERE id = :id")
    suspend fun deleteById(id: Long)
}
