package so.lai.recalo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import so.lai.recalo.data.local.model.MealItemWithNutrients

@Composable
fun MealItemCard(
    item: MealItemWithNutrients,
    editable: Boolean = false,
    onEditClick: ((String) -> Unit)? = null
) {
    val mealItem = item.mealItem
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        text = mealItem.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    androidx.compose.material3.Text(
                        text = mealItem.quantity,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Text(
                        text = "${mealItem.calories} kcal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (editable && onEditClick != null) {
                        IconButton(
                            onClick = { onEditClick(mealItem.id) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Adjust portion",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item.nutrients.let { nutrients ->
                val p = nutrients.find { it.name.contains("Protein", ignoreCase = true) }?.amount?.toInt() ?: 0
                val f = nutrients.find { it.name.contains("Fat", ignoreCase = true) }?.amount?.toInt() ?: 0
                val c = nutrients.find { it.name.contains("Carbohydrate", ignoreCase = true) }?.amount?.toInt() ?: 0

                Spacer(modifier = Modifier.height(8.dp))

                PFCBadges(protein = p, fat = f, carbs = c)
            }
        }
    }
}
