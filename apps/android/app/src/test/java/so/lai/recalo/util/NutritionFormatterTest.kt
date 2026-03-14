package so.lai.recalo.util

import org.junit.Assert.assertEquals
import org.junit.Test
import so.lai.recalo.data.local.entity.NutrientEntity

class NutritionFormatterTest {
    @Test
    fun formatCalories_returnsPlaceholderWhenNull() {
        assertEquals("Analyzing...", NutritionFormatter.formatCalories(null))
    }

    @Test
    fun formatCalories_formatsValue() {
        assertEquals("420 kcal", NutritionFormatter.formatCalories(420))
    }

    @Test
    fun formatNutrientsFromEntities_returnsPlaceholderWhenNullOrEmpty() {
        assertEquals(listOf("Analyzing..."), NutritionFormatter.formatNutrientsFromEntities(null))
        assertEquals(
            listOf("Analyzing..."),
            NutritionFormatter.formatNutrientsFromEntities(emptyList())
        )
    }

    @Test
    fun formatNutrientsFromEntities_formatsWithUnit() {
        val nutrients = listOf(
            NutrientEntity(
                id = "dummy",
                nutritionResultId = null,
                mealItemId = null,
                name = "Protein",
                amount = 10.5,
                unit = "g"
            )
        )
        assertEquals(
            listOf("Protein: 10.5 g"),
            NutritionFormatter.formatNutrientsFromEntities(nutrients)
        )
    }
}
