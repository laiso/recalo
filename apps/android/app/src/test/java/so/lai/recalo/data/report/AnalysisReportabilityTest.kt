package so.lai.recalo.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import so.lai.recalo.data.local.entity.MealItemEntity
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.entity.NutrientEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity
import so.lai.recalo.data.local.model.MealItemWithNutrients
import so.lai.recalo.data.local.model.MealWithNutrition
import so.lai.recalo.data.local.model.NutritionResultWithDetails

class AnalysisReportabilityTest {
    @Test
    fun `error status is always reportable`() {
        val meal = meal(status = MealLogEntity.AnalysisStatus.ERROR)

        val reportability = meal.analysisReportability()

        assertTrue(reportability.isReportable)
        assertEquals(
            AnalysisReportReason.ANALYSIS_ERROR,
            (reportability as AnalysisReportability.Reportable).reason
        )
    }

    @Test
    fun `explicit all-zero result including items is reportable`() {
        val meal = meal(
            status = MealLogEntity.AnalysisStatus.COMPLETED,
            calories = 0,
            totalAmounts = listOf(0.0, 0.0),
            items = listOf(0 to listOf(0.0))
        )

        val reportability = meal.analysisReportability()

        assertTrue(reportability.isReportable)
        assertEquals(
            AnalysisReportReason.ALL_ZERO_VALUES,
            (reportability as AnalysisReportability.Reportable).reason
        )
    }

    @Test
    fun `item-level zero calories keep an otherwise normal result out of all-zero detection`() {
        val meal = meal(
            status = MealLogEntity.AnalysisStatus.COMPLETED,
            calories = 450,
            totalAmounts = listOf(30.0),
            items = listOf(0 to listOf(0.0))
        )

        assertFalse(meal.analysisReportability().isReportable)
    }

    @Test
    fun `a single non-zero nutrient keeps the result unreportable`() {
        val meal = meal(
            status = MealLogEntity.AnalysisStatus.COMPLETED,
            calories = 0,
            totalAmounts = listOf(0.0, 0.0),
            items = listOf(0 to listOf(0.0)),
            extraItemAmount = 12.0
        )

        assertFalse(meal.analysisReportability().isReportable)
    }

    @Test
    fun `some zero nutrients with non-zero calories are not reportable`() {
        val meal = meal(
            status = MealLogEntity.AnalysisStatus.COMPLETED,
            calories = 320,
            totalAmounts = listOf(0.0, 18.0),
            items = emptyList()
        )

        assertFalse(meal.analysisReportability().isReportable)
    }

    @Test
    fun `completed result without stored values is reportable as missing values`() {
        val meal = MealWithNutrition(
            meal = MealLogEntity(
                id = "meal-missing",
                imageUrl = null,
                capturedAt = 1L,
                imagePath = "/tmp/meal.jpg",
                analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
            ),
            nutritionResultDetails = null
        )

        val reportability = meal.analysisReportability()

        assertTrue(reportability.isReportable)
        assertEquals(
            AnalysisReportReason.MISSING_VALUES,
            (reportability as AnalysisReportability.Reportable).reason
        )
    }

    @Test
    fun `analyzing and pending meals are never reportable`() {
        assertFalse(
            meal(status = MealLogEntity.AnalysisStatus.ANALYZING).analysisReportability().isReportable
        )
        assertFalse(
            meal(status = MealLogEntity.AnalysisStatus.PENDING).analysisReportability().isReportable
        )
    }

    @Test
    fun `water-like all zero completion is reportable without being treated as an error`() {
        val meal = meal(
            status = MealLogEntity.AnalysisStatus.COMPLETED,
            calories = 0,
            totalAmounts = listOf(0.0, 0.0),
            items = listOf(0 to listOf(0.0))
        )

        val reportability = meal.analysisReportability()

        assertTrue(reportability.isReportable)
        assertEquals(MealLogEntity.AnalysisStatus.COMPLETED, meal.meal.analysisStatus)
    }

    private fun meal(
        status: String,
        calories: Int? = null,
        totalAmounts: List<Double> = emptyList(),
        items: List<Pair<Int, List<Double>>> = emptyList(),
        extraItemAmount: Double? = null
    ): MealWithNutrition {
        val resultId = "result-1"
        val nutritionResult = NutritionResultEntity(
            id = resultId,
            mealLogId = "meal-1",
            title = "Meal",
            calories = calories,
            confidence = 0.5
        )
        val totalNutrients = totalAmounts.mapIndexed { index, amount ->
            NutrientEntity(
                id = "total-$index",
                nutritionResultId = resultId,
                mealItemId = null,
                name = "Protein",
                amount = amount,
                unit = "g"
            )
        }
        val itemWithNutrients = items.mapIndexed { itemIndex, (itemCalories, amounts) ->
            val itemId = "item-$itemIndex"
            MealItemWithNutrients(
                mealItem = MealItemEntity(
                    id = itemId,
                    nutritionResultId = resultId,
                    name = "Item $itemIndex",
                    quantity = "1",
                    calories = itemCalories
                ),
                nutrients = amounts.mapIndexed { nutrientIndex, amount ->
                    NutrientEntity(
                        id = "item-$itemIndex-nutrient-$nutrientIndex",
                        nutritionResultId = null,
                        mealItemId = itemId,
                        name = "Protein",
                        amount = amount,
                        unit = "g"
                    )
                } + extraItemAmount?.let { amount ->
                    listOf(
                        NutrientEntity(
                            id = "item-$itemIndex-extra",
                            nutritionResultId = null,
                            mealItemId = itemId,
                            name = "Fat",
                            amount = amount,
                            unit = "g"
                        )
                    )
                }.orEmpty()
            )
        }
        return MealWithNutrition(
            meal = MealLogEntity(
                id = "meal-1",
                imageUrl = null,
                capturedAt = 1L,
                imagePath = "/tmp/meal.jpg",
                analysisStatus = status
            ),
            nutritionResultDetails = NutritionResultWithDetails(
                nutritionResult = nutritionResult,
                nutrients = totalNutrients,
                items = itemWithNutrients
            )
        )
    }
}
