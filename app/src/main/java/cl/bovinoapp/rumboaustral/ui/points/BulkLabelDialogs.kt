package cl.bovinoapp.rumboaustral.ui.points

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.bovinoapp.rumboaustral.data.AutoClassifyPlan
import cl.bovinoapp.rumboaustral.data.Label
import cl.bovinoapp.rumboaustral.data.LabelKind
import cl.bovinoapp.rumboaustral.ui.labels.ColorDot

/** Opción elegida en el diálogo; [labelId] null = "Sin etiqueta" (quitar). Aparte de null = aún no eligió. */
private data class Choice(val labelId: Long?)

/** Asigna (o quita) una etiqueta de una categoría a los [selectedCount] puntos seleccionados. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssignLabelDialog(
    selectedCount: Int,
    labels: List<Label>,
    busy: Boolean,
    onApply: (LabelKind, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(LabelKind.TYPE) }
    var choice by remember { mutableStateOf<Choice?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (selectedCount == 1) "Asignar etiqueta a 1 punto" else "Asignar etiqueta a $selectedCount puntos") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            enabled = !busy,
                            onClick = { kind = k; choice = null },
                            label = { Text(k.displayName) }
                        )
                    }
                }
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    ChoiceRow("Sin etiqueta (quitar)", null, choice == Choice(null), !busy) { choice = Choice(null) }
                    labels.filter { it.kind == kind }.forEach { label ->
                        ChoiceRow(label.name, label, choice == Choice(label.id), !busy) { choice = Choice(label.id) }
                    }
                }
                Text(
                    "Reemplaza la etiqueta de ${kind.displayName.lowercase()} que ya tengan.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = choice != null && !busy, onClick = { choice?.let { onApply(kind, it.labelId) } }) { Text("Aplicar") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ChoiceRow(text: String, label: Label?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        label?.let { ColorDot(it.color, 14) }
        Text(text, modifier = Modifier.padding(start = if (label != null) 8.dp else 0.dp))
    }
}

/** Vista previa de la autoclasificación por nombre: qué etiquetas recibirían cuántos puntos. */
@Composable
fun AutoClassifyDialog(plan: AutoClassifyPlan, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Autoclasificar por nombre") },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Se busca el nombre de cada etiqueta dentro del nombre de los puntos sin etiqueta. " +
                        "No se cambia ningún punto que ya tenga etiqueta.",
                    style = MaterialTheme.typography.bodyMedium
                )
                // Un punto puede recibir una etiqueta por categoría: se distinguen etiquetas y puntos.
                Text(
                    "${plan.assignmentCount} ${if (plan.assignmentCount == 1) "etiqueta" else "etiquetas"} " +
                        "en ${plan.pointCount} ${if (plan.pointCount == 1) "punto" else "puntos"}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                plan.perKind.filter { it.assignments.isNotEmpty() }.forEach { kindPlan ->
                    Text(kindPlan.kind.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    kindPlan.assignments.forEach { (label, ids) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            ColorDot(label.color, 12)
                            Text(
                                "${label.name}: ${ids.size} ${if (ids.size == 1) "punto" else "puntos"}",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                if (plan.ambiguous > 0) {
                    Text(
                        "${plan.ambiguous} ${if (plan.ambiguous == 1) "punto coincide" else "puntos coinciden"} con varias etiquetas por igual y se omite" +
                            if (plan.ambiguous == 1) "." else "n.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = plan.pointCount > 0 && !busy, onClick = onConfirm) {
                Text(if (plan.pointCount == 1) "Clasificar 1 punto" else "Clasificar ${plan.pointCount} puntos")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } }
    )
}
