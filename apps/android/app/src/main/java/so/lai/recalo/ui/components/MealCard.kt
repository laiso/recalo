package so.lai.recalo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import so.lai.recalo.data.local.model.MealWithNutrition
import java.io.File

private val CalorieColor = Color(0xFF4CAF50)

@Composable
fun MealCard(
    mealWithNutrition: MealWithNutrition,
    onClick: () -> Unit
) {
    val meal = mealWithNutrition.meal
    val nutrition = mealWithNutrition.nutritionResult
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            meal.imagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = "Meal thumbnail",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Text(
                        text = nutrition?.title ?: "Unknown Meal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Portion ratio label (simple gray text)
                    nutrition?.let { result ->
                        if (result.portionRatio != 1.0) {
                            androidx.compose.material3.Text(
                                text = "${result.portionRatio} x",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                androidx.compose.material3.Text(
                    text = "${nutrition?.calories ?: 0} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalorieColor,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                mealWithNutrition.nutrients?.let { nutrients ->
                    val p = nutrients.find { it.name.contains("Protein", ignoreCase = true) }?.amount?.toInt() ?: 0
                    val f = nutrients.find { it.name.contains("Fat", ignoreCase = true) }?.amount?.toInt() ?: 0
                    val c = nutrients.find { it.name.contains("Carbohydrate", ignoreCase = true) }?.amount?.toInt() ?: 0
                    
                    PFCBadges(protein = p, fat = f, carbs = c)
                }
            }
        }
    }
}
