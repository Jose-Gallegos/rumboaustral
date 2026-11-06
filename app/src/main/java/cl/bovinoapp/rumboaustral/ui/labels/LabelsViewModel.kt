package cl.bovinoapp.rumboaustral.ui.labels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.bovinoapp.rumboaustral.data.Label
import cl.bovinoapp.rumboaustral.data.LabelKind
import cl.bovinoapp.rumboaustral.data.LabelsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LabelsViewModel(private val repository: LabelsRepository) : ViewModel() {
    val labels: StateFlow<List<Label>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    /** Mensaje del último error de escritura; la interfaz lo muestra hasta [clearError] o la siguiente operación. */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    /** true mientras hay una escritura en curso, para evitar toques repetidos. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    /**
     * Ejecuta una escritura capturando fallos. [block] devuelve false si no se pudo aplicar.
     * [onSuccess] solo se invoca si salió bien, para que los diálogos se cierren únicamente entonces.
     */
    private fun launchWrite(failureMessage: String, onSuccess: () -> Unit, block: suspend () -> Boolean) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            val ok = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _busy.value = false
            if (ok) onSuccess() else _error.value = failureMessage
        }
    }

    fun create(kind: LabelKind, name: String, color: Int, onSuccess: () -> Unit = {}) =
        launchWrite("No se pudo crear la etiqueta", onSuccess) {
            repository.create(kind, name, color)
            true
        }

    fun update(label: Label, onSuccess: () -> Unit = {}) =
        launchWrite("No se pudo guardar la etiqueta", onSuccess) { repository.update(label) }

    /** Mueve [label] una posición ([delta] = -1 arriba, +1 abajo) dentro de su categoría. */
    fun move(label: Label, delta: Int) {
        val siblings = labels.value.filter { it.kind == label.kind }.toMutableList()
        val from = siblings.indexOfFirst { it.id == label.id }
        val to = from + delta
        if (from < 0 || to !in siblings.indices) return
        siblings.add(to, siblings.removeAt(from))
        launchWrite("No se pudo cambiar el orden", onSuccess = {}) {
            repository.reorder(siblings)
            true
        }
    }

    /** Cantidad de puntos que usan la etiqueta, o null si no se pudo consultar (se informa en [error]). */
    suspend fun usageCount(label: Label): Int? = try {
        repository.countPointsUsing(label.id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        _error.value = "No se pudo consultar el uso de la etiqueta"
        null
    }

    fun delete(label: Label, replacementId: Long?, onSuccess: () -> Unit = {}) =
        launchWrite("No se pudo eliminar: la etiqueta o su reemplazo ya no existe", onSuccess) {
            repository.delete(label, replacementId)
        }
}
