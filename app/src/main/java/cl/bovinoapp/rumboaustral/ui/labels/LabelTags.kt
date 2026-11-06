package cl.bovinoapp.rumboaustral.ui.labels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.bovinoapp.rumboaustral.data.Label

/** Etiquetas de un punto como pequeñas marcas de color con su nombre; no muestra nada si no hay. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelTags(labels: List<Label>, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        labels.forEach { label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(label.color, 10)
                Text(label.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
