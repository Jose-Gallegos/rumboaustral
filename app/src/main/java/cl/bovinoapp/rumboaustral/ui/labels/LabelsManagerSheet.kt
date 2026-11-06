package cl.bovinoapp.rumboaustral.ui.labels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cl.bovinoapp.rumboaustral.data.Label
import cl.bovinoapp.rumboaustral.data.LabelKind
import kotlinx.coroutines.launch

private val palette = listOf(
    0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00897B, 0xFF1E88E5,
    0xFF3949AB, 0xFF8E24AA, 0xFFD81B60, 0xFF6D4C41, 0xFF757575, 0xFF212121
).map { it.toInt() }

/** Edición de una etiqueta existente ([label] != null) o alta de una nueva en [kind]. */
private data class EditorState(val kind: LabelKind, val label: Label?)

private data class PendingDelete(val label: Label, val usageCount: Int)

/**
 * Panel para crear, renombrar, recolorear, reordenar y borrar etiquetas. Es un panel inferior
 * (no una pantalla) para poder abrirlo desde el formulario de un punto sin perder lo escrito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsManagerSheet(viewModel: LabelsViewModel, onDismiss: () -> Unit) {
    val allLabels by viewModel.labels.collectAsState()
    val error by viewModel.error.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val kinds = LabelKind.entries
    var selectedIndex by remember { mutableStateOf(0) }
    val kind = kinds[selectedIndex]
    val labels = allLabels.filter { it.kind == kind }
    var editor by remember { mutableStateOf<EditorState?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.clearError()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                "Etiquetas",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            PrimaryTabRow(selectedTabIndex = selectedIndex) {
                kinds.forEachIndexed { index, k ->
                    Tab(selected = index == selectedIndex, onClick = { selectedIndex = index }, text = { Text(k.displayName) })
                }
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (labels.isEmpty()) {
                Text(
                    "No hay etiquetas de ${kind.displayName.lowercase()}. Agrega la primera.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(labels, key = { it.id }) { label ->
                    val index = labels.indexOf(label)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp)
                    ) {
                        ColorDot(label.color, 20)
                        Text(label.name, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        IconButton(enabled = index > 0 && !busy, onClick = { viewModel.move(label, -1) }) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Subir")
                        }
                        IconButton(enabled = index < labels.lastIndex && !busy, onClick = { viewModel.move(label, 1) }) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Bajar")
                        }
                        IconButton(enabled = !busy, onClick = {
                            viewModel.clearError()
                            editor = EditorState(kind, label)
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar")
                        }
                        IconButton(enabled = !busy, onClick = {
                            viewModel.clearError()
                            scope.launch {
                                // Si la consulta falla, el ViewModel informa el error y no se abre el diálogo.
                                viewModel.usageCount(label)?.let { pendingDelete = PendingDelete(label, it) }
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    viewModel.clearError()
                    editor = EditorState(kind, null)
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Agregar etiqueta de ${kind.displayName.lowercase()}", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    editor?.let { state ->
        LabelEditorDialog(
            state = state,
            existingNames = allLabels.filter { it.kind == state.kind && it.id != state.label?.id }
                .map { it.name.lowercase() }.toSet(),
            busy = busy,
            error = error,
            onDismiss = { if (!busy) editor = null },
            // Se cierra solo si la escritura salió bien y solo este mismo diálogo (identidad, no igualdad).
            onSave = { name, color ->
                val existing = state.label
                val close = { if (editor === state) editor = null }
                if (existing == null) viewModel.create(state.kind, name, color, close)
                else viewModel.update(existing.copy(name = name, color = color), close)
            }
        )
    }

    pendingDelete?.let { pending ->
        DeleteLabelDialog(
            pending = pending,
            replacements = allLabels.filter { it.kind == pending.label.kind && it.id != pending.label.id },
            busy = busy,
            error = error,
            onDismiss = { if (!busy) pendingDelete = null },
            onConfirm = { replacementId ->
                viewModel.delete(pending.label, replacementId) { if (pendingDelete === pending) pendingDelete = null }
            }
        )
    }
}

@Composable
internal fun ColorDot(color: Int, sizeDp: Int, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

@Composable
private fun LabelEditorDialog(
    state: EditorState,
    existingNames: Set<String>,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Int) -> Unit
) {
    var name by remember { mutableStateOf(state.label?.name ?: "") }
    var color by remember { mutableStateOf(state.label?.color ?: palette.first()) }
    val trimmed = name.trim()
    val duplicate = trimmed.lowercase() in existingNames
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.label == null) "Nueva etiqueta de ${state.kind.displayName.lowercase()}" else "Editar etiqueta") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) ({ Text("Ya existe una etiqueta con ese nombre") }) else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Color", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                palette.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        row.forEach { c -> ColorDot(c, 32, selected = c == color, onClick = { color = c }) }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(enabled = trimmed.isNotEmpty() && !duplicate && !busy, onClick = { onSave(trimmed, color) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun DeleteLabelDialog(
    pending: PendingDelete,
    replacements: List<Label>,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (replacementId: Long?) -> Unit
) {
    // null = dejar los puntos sin etiqueta
    var replacement by remember { mutableStateOf<Label?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val count = pending.usageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar \"${pending.label.name}\"") },
        text = {
            Column {
                if (count == 0) {
                    Text("Ningún punto usa esta etiqueta.")
                } else {
                    Text(
                        "Esta etiqueta está en uso en $count ${if (count == 1) "punto" else "puntos"}. " +
                            "Puedes reemplazarla por otra; si no, esos puntos quedarán sin etiqueta."
                    )
                    Box(modifier = Modifier.padding(top = 12.dp)) {
                        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            replacement?.let { ColorDot(it.color, 14) }
                            Text(
                                "Reemplazar por: ${replacement?.name ?: "Sin etiqueta"}",
                                modifier = Modifier.padding(start = if (replacement != null) 8.dp else 0.dp)
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Sin etiqueta") }, onClick = { replacement = null; menuOpen = false })
                            replacements.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.name) },
                                    leadingIcon = { ColorDot(option.color, 14) },
                                    onClick = { replacement = option; menuOpen = false }
                                )
                            }
                        }
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onConfirm(replacement?.id) }) { Text("Eliminar") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } }
    )
}
