package cl.bovinoapp.rumboaustral.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY kind, sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Label>>

    @Query("SELECT * FROM labels WHERE id = :id")
    suspend fun getById(id: Long): Label?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM labels WHERE kind = :kind")
    suspend fun maxSortOrder(kind: LabelKind): Int

    @Insert
    suspend fun insert(label: Label): Long

    @Update
    suspend fun update(label: Label): Int

    @Update
    suspend fun updateAll(labels: List<Label>)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Cantidad de puntos que usan una etiqueta, para advertir antes de borrarla.
    @Query(
        "SELECT COUNT(*) FROM points WHERE typeLabelId = :id OR statusLabelId = :id OR zoneLabelId = :id"
    )
    suspend fun countPointsUsing(id: Long): Int

    // Reasignación al reemplazar una etiqueta: cada categoría tiene su propia columna.
    @Query("UPDATE points SET typeLabelId = :newId WHERE typeLabelId = :oldId")
    suspend fun replaceInType(oldId: Long, newId: Long?)

    @Query("UPDATE points SET statusLabelId = :newId WHERE statusLabelId = :oldId")
    suspend fun replaceInStatus(oldId: Long, newId: Long?)

    @Query("UPDATE points SET zoneLabelId = :newId WHERE zoneLabelId = :oldId")
    suspend fun replaceInZone(oldId: Long, newId: Long?)

    /**
     * Borra la etiqueta; si [replacementId] no es null, sus puntos pasan a esa etiqueta. Si es null quedan sin etiqueta.
     * Devuelve false sin cambiar nada si la etiqueta ya no existe o el reemplazo no existe, es la misma
     * etiqueta o es de otra categoría.
     */
    @Transaction
    suspend fun deleteReplacing(label: Label, replacementId: Long?): Boolean {
        // Se usa la categoría guardada en la base, no la del objeto recibido.
        val current = getById(label.id) ?: return false
        if (replacementId != null) {
            val replacement = getById(replacementId)
            if (replacement == null || replacement.id == current.id || replacement.kind != current.kind) return false
            when (current.kind) {
                LabelKind.TYPE -> replaceInType(current.id, replacementId)
                LabelKind.STATUS -> replaceInStatus(current.id, replacementId)
                LabelKind.ZONE -> replaceInZone(current.id, replacementId)
            }
        }
        deleteById(current.id) // si no hubo reemplazo, la FK SET_NULL deja los puntos sin etiqueta
        return true
    }
}
