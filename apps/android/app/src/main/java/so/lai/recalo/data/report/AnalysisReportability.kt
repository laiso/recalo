package so.lai.recalo.data.report

import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.model.MealItemWithNutrients
import so.lai.recalo.data.local.model.MealWithNutrition

/**
 * Why a meal's analysis can be reported to support.
 *
 * The same predicate is used for the card button and for deciding whether an
 * analysis attempt is kept as a diagnostic record, so the two never disagree.
 */
enum class AnalysisReportReason {
    /** The analysis finished with an error (HTTP failure, timeout, ...). */
    ANALYSIS_ERROR,

    /** The analysis completed but every stored calorie/nutrient value is zero. */
    ALL_ZERO_VALUES,

    /** The analysis completed but no nutrition values were stored at all. */
    MISSING_VALUES
}

sealed class AnalysisReportability {
    val isReportable: Boolean
        get() = this is Reportable

    data class Reportable(val reason: AnalysisReportReason) : AnalysisReportability()

    object NotReportable : AnalysisReportability()

    companion object {
        @JvmStatic
        fun of(
            analysisStatus: String?,
            nutritionCalories: Int?,
            hasNutritionResult: Boolean,
            itemCalories: List<Int>,
            nutrientAmounts: List<Double?>,
            itemNutrientAmounts: List<Double?>
        ): AnalysisReportability {
            return when (analysisStatus) {
                MealLogEntity.AnalysisStatus.ERROR -> Reportable(
                    AnalysisReportReason.ANALYSIS_ERROR
                )
                MealLogEntity.AnalysisStatus.COMPLETED -> {
                    if (!hasNutritionResult) {
                        Reportable(AnalysisReportReason.MISSING_VALUES)
                    } else if (
                        isZero(nutritionCalories) &&
                        itemCalories.all { isZero(it) } &&
                        nutrientAmounts.all { isZero(it) } &&
                        itemNutrientAmounts.all { isZero(it) }
                    ) {
                        Reportable(AnalysisReportReason.ALL_ZERO_VALUES)
                    } else {
                        NotReportable
                    }
                }
                else -> NotReportable
            }
        }

        private fun isZero(value: Int?): Boolean = value == null || value == 0

        private fun isZero(value: Double?): Boolean = value == null || value == 0.0
    }
}

fun MealWithNutrition.analysisReportability(): AnalysisReportability {
    val items: List<MealItemWithNutrients> = items.orEmpty()
    return AnalysisReportability.of(
        analysisStatus = meal.analysisStatus,
        nutritionCalories = nutritionResult?.calories,
        hasNutritionResult = nutritionResult != null,
        itemCalories = items.map { it.mealItem.calories },
        nutrientAmounts = nutrients.orEmpty().map { it.amount },
        itemNutrientAmounts = items.flatMap { item -> item.nutrients.map { it.amount } }
    )
}
