package cl.bovinoapp.rumboaustral.ui.labels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.bovinoapp.rumboaustral.data.Label
import cl.bovinoapp.rumboaustral.data.LabelKind
import cl.bovinoapp.rumboaustral.data.NO_LABEL_ID
import cl.bovinoapp.rumboaustral.data.PointFilter

/**
 * Panel de filtros y "Colorear por", compartido por el mapa y la lista. [filter] ya viene podado
 * (sin etiquetas borradas); [shown] y [total] son los puntos visibles y los totales.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PointFilterSheet(
    filter: PointFilter,
    labels: List<Label>,
    shown: Int,
    total: Int,
    onToggle: (LabelKind, Long) -> Unit,
    onColorBy: (LabelKind) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text("Filtros", style = MaterialTheme.typography.titleLarge)
            Text(
                // Por diferencia y no por filtro activo: en la lista [shown] también descuenta la búsqueda por nombre.
                if (shown != total) "Mostrando $shown de $total puntos" else "$total puntos",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text("Colorear por", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelKind.entries.forEach { kind ->
                    FilterChip(
                        selected = filter.colorBy == kind,
                        onClick = { onColorBy(kind) },
                        label = { Text(kind.displayName) }
                    )
                }
            }

            LabelKind.entries.forEach { kind ->
                val selectedIds = filter.selected[kind].orEmpty()
                Text(kind.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = NO_LABEL_ID in selectedIds,
                        onClick = { onToggle(kind, NO_LABEL_ID) },
                        label = { Text("Sin etiqueta") }
                    )
                    labels.filter { it.kind == kind }.forEach { label ->
                        FilterChip(
                            selected = label.id in selectedIds,
                            onClick = { onToggle(kind, label.id) },
                            leadingIcon = { ColorDot(label.color, 14) },
                            label = { Text(label.name) }
                        )
                    }
                }
            }

            OutlinedButton(
                enabled = filter.isActive,
                onClick = onClear,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp)
            ) { Text("Limpiar filtros") }
        }
    }
}
