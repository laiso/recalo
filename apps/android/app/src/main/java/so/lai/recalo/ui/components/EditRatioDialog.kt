package so.lai.recalo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRatioDialog(
    itemId: String,
    currentRatio: Double = 1.0,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var ratioText by remember { mutableStateOf(currentRatio.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Adjust Portion",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )
        },
        text = {
            Column {
                Text(
                    text = "Select portion size for entire meal",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.5, 1.0, 1.5, 2.0).forEach { ratio ->
                        val isSelected = ratioText.toDoubleOrNull() == ratio
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                ratioText = ratio.toString()
                            },
                            label = {
                                Text(
                                    text = "$ratio x",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.widthIn(min = 60.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ratio = ratioText.toDoubleOrNull() ?: 1.0
                    onSave(itemId, ratio)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
