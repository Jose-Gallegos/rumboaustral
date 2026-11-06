package cl.bovinoapp.rumboaustral.data

import kotlinx.coroutines.flow.Flow

class LabelsRepository(private val dao: LabelDao) {
    fun observeAll(): Flow<List<Label>> = dao.observeAll()

    suspend fun create(kind: LabelKind, name: String, color: Int): Long =
        dao.insert(Label(kind = kind, name = name.trim(), color = color, sortOrder = dao.maxSortOrder(kind) + 1))

    /** @return false si la etiqueta ya no existe. */
    suspend fun update(label: Label): Boolean = dao.update(label.copy(name = label.name.trim())) > 0

    /** Persiste un nuevo orden: [ordered] son las etiquetas de una categoría en el orden deseado. */
    suspend fun reorder(ordered: List<Label>) =
        dao.updateAll(ordered.mapIndexed { index, label -> label.copy(sortOrder = index) })

    suspend fun countPointsUsing(labelId: Long): Int = dao.countPointsUsing(labelId)

    /** @return false si no se borró (etiqueta inexistente o reemplazo inválido). */
    suspend fun delete(label: Label, replacementId: Long? = null): Boolean = dao.deleteReplacing(label, replacementId)
}
