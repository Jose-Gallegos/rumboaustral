package cl.bovinoapp.rumboaustral.ui.points

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.remember
import cl.bovinoapp.rumboaustral.data.LabelImport
import cl.bovinoapp.rumboaustral.data.LabelKind
import cl.bovinoapp.rumboaustral.geo.defaultCoordinateFormat
import cl.bovinoapp.rumboaustral.gpx.ImportStatus

private const val MAX_INVALID_SHOWN = 50
private const val MAX_NEW_LABELS_SHOWN = 5

@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    saving: Boolean,
    onToggle: (Int) -> Unit,
    onSelect: (onlyNew: Boolean?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val plan = preview.plan
    val format = defaultCoordinateFormat()
    val newCount = plan.candidates.count { it.status == ImportStatus.NEW }
    val duplicateCount = plan.candidates.size - newCount

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.statusBarsPadding().navigationBarsPadding()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Vista previa de importación", style = MaterialTheme.typography.titleLarge)
                    Text(preview.fileName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "$newCount nuevos · $duplicateCount duplicados · ${plan.invalid.size} omitidos",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    // Etiquetas del archivo que no existen todavía: se crean al importar, y conviene verlo antes.
                    val newLabels = remember(preview) {
                        LabelImport.missing(preview.selected.flatMap { plan.candidates[it].labels }, preview.existingLabels)
                    }
                    if (newLabels.isNotEmpty()) {
                        val summary = LabelKind.entries.mapNotNull { kind ->
                            val names = newLabels.filter { it.kind == kind }.map { it.name }
                            if (names.isEmpty()) null
                            else "${kind.displayName}: ${names.take(MAX_NEW_LABELS_SHOWN).joinToString(", ")}" +
                                if (names.size > MAX_NEW_LABELS_SHOWN) " y ${names.size - MAX_NEW_LABELS_SHOWN} más" else ""
                        }.joinToString(" · ")
                        Text(
                            "Se crearán ${newLabels.size} etiquetas nuevas — $summary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (plan.truncated) {
                        Text(
                            "El archivo tiene más puntos; solo se leyeron los primeros.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { onSelect(true) }) { Text("Solo nuevos") }
                        OutlinedButton(onClick = { onSelect(false) }) { Text("Todos") }
                        TextButton(onClick = { onSelect(null) }) { Text("Ninguno") }
                    }
                }
                HorizontalDivider()

                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(plan.candidates) { index, c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onToggle(index) }.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Checkbox(checked = index in preview.selected, onCheckedChange = { onToggle(index) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(format.format(c.latitude, c.longitude), style = MaterialTheme.typography.bodySmall)
                                if (c.notes.isNotBlank()) {
                                    Text(c.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (c.labels.isNotEmpty()) {
                                    Text(
                                        c.labels.joinToString(" · ") { it.name },
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                when (c.status) {
                                    ImportStatus.NEW -> Unit
                                    ImportStatus.DUPLICATE_EXISTING -> StatusLabel("Ya existe en tus puntos")
                                    ImportStatus.DUPLICATE_IN_FILE -> StatusLabel("Repetido en el archivo")
                                }
                            }
                        }
                    }
                    if (plan.invalid.isNotEmpty()) {
                        item {
                            Text(
                                "Omitidos (${plan.invalid.size})",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                            )
                        }
                        itemsIndexed(plan.invalid.take(MAX_INVALID_SHOWN)) { _, entry ->
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                                Text(entry.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (plan.invalid.size > MAX_INVALID_SHOWN) {
                            item {
                                Text(
                                    "… y ${plan.invalid.size - MAX_INVALID_SHOWN} más",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
                    Button(onClick = onConfirm, enabled = preview.selected.isNotEmpty() && !saving, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Importar (${preview.selected.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
}
