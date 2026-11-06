package cl.bovinoapp.rumboaustral.ui.labels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.bovinoapp.rumboaustral.data.Label
import cl.bovinoapp.rumboaustral.data.LabelKind

/** Selector de la etiqueta de una categoría; [selectedId] null (o inexistente en [labels]) = "Sin etiqueta". */
@Composable
fun LabelSelector(
    kind: LabelKind,
    labels: List<Label>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val selected = labels.firstOrNull { it.id == selectedId }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { open = true }) {
            selected?.let { ColorDot(it.color, 14) }
            Text("${kind.displayName}: ${selected?.name ?: "Sin etiqueta"}", modifier = Modifier.padding(start = if (selected != null) 8.dp else 0.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Sin etiqueta") }, onClick = { onSelect(null); open = false })
            labels.forEach { label ->
                DropdownMenuItem(
                    text = { Text(label.name) },
                    leadingIcon = { ColorDot(label.color, 14) },
                    onClick = { onSelect(label.id); open = false }
                )
            }
        }
    }
}
