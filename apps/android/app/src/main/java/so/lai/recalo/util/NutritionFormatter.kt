package so.lai.recalo.util

import so.lai.recalo.data.local.entity.NutrientEntity

object NutritionFormatter {
    fun formatCalories(calories: Int?): String {
        return calories?.let { "$it kcal" } ?: "Analyzing..."
    }

    private fun formatNutrientLine(name: String, amount: Double, unit: String): String {
        val resolvedUnit = if (unit.isBlank()) "" else " $unit"
        return "$name: $amount$resolvedUnit"
    }

    fun formatNutrientsFromEntities(nutrients: List<NutrientEntity>?): List<String> {
        if (nutrients.isNullOrEmpty()) {
            return listOf("Analyzing...")
        }
        return nutrients.map { formatNutrientLine(it.name, it.amount, it.unit) }
    }
}
