package so.lai.recalo.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import so.lai.recalo.data.local.CaroliDatabase
import so.lai.recalo.data.local.entity.MealItemEntity
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.entity.NutrientEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity

/**
 * MealRepository Scenario tests
 *
 * Verify the flow from registration -> analysis -> cancellation
 */
@RunWith(RobolectricTestRunner::class)
class MealRepositoryScenarioTest {

    private lateinit var database: CaroliDatabase
    private lateinit var repository: MealRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CaroliDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.mealDao()
        repository = MealRepository(dao = dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `SCENARIO register meal analyze complete cancel delete`() = runTest {
        val mealId = UUID.randomUUID().toString()
        val capturedAt = System.currentTimeMillis()

        // ============================================================
        // Step 1: Meal registration (analyzing state)
        // ============================================================
        val analyzingMeal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = capturedAt,
            imagePath = "/path/to/meal.jpg",
            analysisStatus = MealLogEntity.AnalysisStatus.ANALYZING
        )
        database.mealDao().insertMeal(analyzingMeal)

        // Verify registration
        val step1Meal = database.mealDao().getMealById(mealId)
        assertNotNull("Step 1: Meal should be registered", step1Meal)
        assertEquals("analyzing", step1Meal?.analysisStatus)

        // ============================================================
        // Step 2: Nutrition analysis complete (update to completed state)
        // ============================================================
        val nutritionResult = NutritionResultEntity(
            id = UUID.randomUUID().toString(),
            mealLogId = mealId,
            title = "Test Meal",
            calories = 550,
            confidence = 0.88
        )
        database.mealDao().insertNutritionResult(nutritionResult)

        val completedMeal = analyzingMeal.copy(
            analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED,
            analysisCompletedAt = System.currentTimeMillis()
        )
        database.mealDao().updateMeal(completedMeal)

        // Verify analysis complete state
        val step2Result = database.mealDao().getMealWithNutritionById(mealId)
        assertNotNull("Step 2: MealWithNutrition should exist", step2Result)
        assertEquals("completed", step2Result?.meal?.analysisStatus)
        assertEquals(550, step2Result?.nutritionResult?.calories)
        assertEquals(0.88, step2Result?.nutritionResult?.confidence!!, 0.01)

        // Verify via Flow
        val allMeals = database.mealDao().getAllMealsWithNutrition().first()
        assertEquals(1, allMeals.size)
        assertEquals(mealId, allMeals.first().meal.id)

        // ============================================================
        // Step 3: Cancellation (deletion)
        // ============================================================
        repository.deleteMeal(mealId)

        // Verify deletion
        val step3Meal = database.mealDao().getMealById(mealId)
        assertNull("Step 3: Meal should be deleted", step3Meal)

        val step3Result = database.mealDao().getMealWithNutritionById(mealId)
        assertNull("Step 3: MealWithNutrition should be deleted", step3Result)

        val allMealsAfterDelete = database.mealDao().getAllMealsWithNutrition().first()
        assertTrue("Step 3: All meals should be empty after delete", allMealsAfterDelete.isEmpty())
    }

    @Test
    fun `SCENARIO register meal analyzing cancel delete before analysis complete`() = runTest {
        val mealId = UUID.randomUUID().toString()

        // Step 1: Meal registration (analyzing state)
        val analyzingMeal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = "/path/to/meal.jpg",
            analysisStatus = MealLogEntity.AnalysisStatus.ANALYZING
        )
        database.mealDao().insertMeal(analyzingMeal)

        // Step 2: Cancel before analysis completes
        repository.deleteMeal(mealId)

        // Verify deletion
        val deletedMeal = database.mealDao().getMealById(mealId)
        assertNull("Meal should be deleted before analysis", deletedMeal)
    }

    @Test
    fun `SCENARIO duplicate meal from history copies nutrition items and nutrients to selected date`() = runTest {
        val sourceMealId = "source-meal"
        val sourceNutritionId = "source-nutrition"
        val sourceItemId = "source-item"
        val targetCapturedAt = 987654321L
        val sourceImage = File.createTempFile("source-meal", ".jpg", context.cacheDir).apply {
            writeText("test-image")
        }

        database.mealDao().insertMeal(
            MealLogEntity(
                id = sourceMealId,
                imageUrl = null,
                capturedAt = 1000L,
                imagePath = sourceImage.absolutePath,
                analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
            )
        )
        database.mealDao().insertNutritionResult(
            NutritionResultEntity(
                id = sourceNutritionId,
                mealLogId = sourceMealId,
                title = "Protein Curry",
                calories = 650,
                confidence = 0.91,
                portionRatio = 1.25
            )
        )
        database.mealDao().insertMealItem(
            MealItemEntity(
                id = sourceItemId,
                nutritionResultId = sourceNutritionId,
                name = "Chicken Curry",
                quantity = "1 bowl",
                calories = 650
            )
        )
        database.mealDao().insertNutrients(
            listOf(
                NutrientEntity(
                    id = "source-total-protein",
                    nutritionResultId = sourceNutritionId,
                    mealItemId = null,
                    name = "Protein",
                    amount = 35.0,
                    unit = "g"
                ),
                NutrientEntity(
                    id = "source-total-fat",
                    nutritionResultId = sourceNutritionId,
                    mealItemId = null,
                    name = "Fat",
                    amount = 18.0,
                    unit = "g"
                ),
                NutrientEntity(
                    id = "source-total-carbs",
                    nutritionResultId = sourceNutritionId,
                    mealItemId = null,
                    name = "Carbohydrate",
                    amount = 80.0,
                    unit = "g"
                ),
                NutrientEntity(
                    id = "source-item-protein",
                    nutritionResultId = null,
                    mealItemId = sourceItemId,
                    name = "Protein",
                    amount = 35.0,
                    unit = "g"
                )
            )
        )

        val source = database.mealDao().getMealWithNutritionById(sourceMealId)
        assertNotNull(source)

        val duplicatedMeal = repository.duplicateMealFromHistory(source!!, targetCapturedAt)
        val duplicated = database.mealDao().getMealWithNutritionById(duplicatedMeal.id)

        assertNotNull(duplicated)
        assertNotEquals(sourceMealId, duplicated?.meal?.id)
        assertEquals(targetCapturedAt, duplicated?.meal?.capturedAt)
        assertEquals(sourceImage.absolutePath, duplicated?.meal?.imagePath)
        assertEquals(MealLogEntity.AnalysisStatus.COMPLETED, duplicated?.meal?.analysisStatus)

        assertNotEquals(sourceNutritionId, duplicated?.nutritionResult?.id)
        assertEquals("Protein Curry", duplicated?.nutritionResult?.title)
        assertEquals(650, duplicated?.nutritionResult?.calories)
        assertEquals(0.91, duplicated?.nutritionResult?.confidence!!, 0.01)
        assertEquals(1.25, duplicated.nutritionResult?.portionRatio!!, 0.01)

        val copiedItem = duplicated.items?.single()?.mealItem
        assertNotNull(copiedItem)
        assertNotEquals(sourceItemId, copiedItem?.id)
        assertEquals("Chicken Curry", copiedItem?.name)
        assertEquals(duplicated.nutritionResult?.id, copiedItem?.nutritionResultId)

        val totalNutrients = duplicated.nutrients.orEmpty()
        assertEquals(3, totalNutrients.size)
        assertEquals(35.0, totalNutrients.first { it.name == "Protein" }.amount, 0.01)
        assertEquals(18.0, totalNutrients.first { it.name == "Fat" }.amount, 0.01)
        assertEquals(80.0, totalNutrients.first { it.name == "Carbohydrate" }.amount, 0.01)

        val copiedItemNutrients = duplicated.items?.single()?.nutrients.orEmpty()
        assertEquals(1, copiedItemNutrients.size)
        assertEquals(copiedItem?.id, copiedItemNutrients.single().mealItemId)

        val allMeals = database.mealDao().getAllMealsWithNutrition().first()
        assertEquals(2, allMeals.size)

        repository.deleteMeal(duplicatedMeal.id)
        assertTrue("Shared source image should remain while the source meal exists", sourceImage.exists())

        repository.deleteMeal(sourceMealId)
        assertFalse("Shared source image should be deleted after the last meal reference is gone", sourceImage.exists())
    }

    @Test
    fun `SCENARIO search previous meals and duplicate selected meal into daily totals`() = runTest {
        val zoneId = ZoneId.systemDefault()
        val oldCapturedAt = LocalDate.of(2026, 1, 10)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val otherCapturedAt = LocalDate.of(2026, 1, 11)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val selectedDateStart = LocalDate.of(2026, 6, 29)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val selectedDateEnd = selectedDateStart + 24 * 60 * 60 * 1000L

        insertMealFixture(
            mealId = "meal-curry-rice",
            nutritionId = "nutrition-curry-rice",
            capturedAt = oldCapturedAt,
            title = "Curry Rice",
            calories = 750,
            items = listOf(
                TestMealItem("item-curry", "Curry", "1 plate", 450),
                TestMealItem("item-rice", "Rice", "1 bowl", 300)
            ),
            totalNutrients = listOf(
                TestNutrient("Protein", 20.0, "g"),
                TestNutrient("Fat", 25.0, "g"),
                TestNutrient("Carbohydrate", 110.0, "g")
            )
        )
        insertMealFixture(
            mealId = "meal-natto-rice",
            nutritionId = "nutrition-natto-rice",
            capturedAt = otherCapturedAt,
            title = "Natto Rice",
            calories = 430,
            items = listOf(
                TestMealItem("item-natto", "Natto", "1 pack", 100),
                TestMealItem("item-natto-rice", "Rice", "1 bowl", 330)
            ),
            totalNutrients = listOf(
                TestNutrient("Protein", 16.0, "g"),
                TestNutrient("Fat", 7.0, "g"),
                TestNutrient("Carbohydrate", 72.0, "g")
            )
        )

        val curryResults = repository.searchPreviousMeals("curry")
        assertEquals(1, curryResults.size)
        assertEquals("Curry Rice", curryResults.single().nutritionResult?.title)

        val riceResults = repository.searchPreviousMeals("rice")
        assertEquals(
            setOf("Curry Rice", "Natto Rice"),
            riceResults.mapNotNull { it.nutritionResult?.title }.toSet()
        )

        assertTrue(repository.searchPreviousMeals("pizza-zzz").isEmpty())
        assertTrue(repository.searchPreviousMeals("").isEmpty())
        assertTrue(repository.searchPreviousMeals("   ").isEmpty())

        val sourceMeal = curryResults.single()
        val duplicatedMeal = repository.duplicateMealFromHistory(sourceMeal, selectedDateStart)
        val duplicated = database.mealDao().getMealWithNutritionById(duplicatedMeal.id)

        assertNotNull(duplicated)
        assertNotEquals(sourceMeal.meal.id, duplicated?.meal?.id)
        assertEquals(selectedDateStart, duplicated?.meal?.capturedAt)
        assertEquals("Curry Rice", duplicated?.nutritionResult?.title)
        assertEquals(750, duplicated?.nutritionResult?.calories)

        val copiedItemNames = duplicated?.items.orEmpty().map { it.mealItem.name }.toSet()
        assertEquals(setOf("Curry", "Rice"), copiedItemNames)

        val copiedNutrients = duplicated?.nutrients.orEmpty()
        assertEquals(20.0, copiedNutrients.first { it.name == "Protein" }.amount, 0.01)
        assertEquals(25.0, copiedNutrients.first { it.name == "Fat" }.amount, 0.01)
        assertEquals(110.0, copiedNutrients.first { it.name == "Carbohydrate" }.amount, 0.01)

        val allMeals = database.mealDao().getAllMealsWithNutrition().first()
        val selectedDateMeals = allMeals.filter {
            (it.meal.capturedAt ?: 0L) in selectedDateStart until selectedDateEnd
        }
        val selectedDateCalories = selectedDateMeals.sumOf {
            it.nutritionResult?.calories ?: 0
        }

        assertEquals(listOf(duplicatedMeal.id), selectedDateMeals.map { it.meal.id })
        assertEquals(750, selectedDateCalories)
    }

    @Test
    fun `SCENARIO multiple meals delete one meal others remain`() = runTest {
        // Meal 1: completed
        val meal1Id = "meal-1"
        database.mealDao().insertMeal(
            MealLogEntity(
                id = meal1Id,
                imageUrl = null,
                capturedAt = 1000L,
                imagePath = "/path/1.jpg",
                analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
            )
        )
        database.mealDao().insertNutritionResult(
            NutritionResultEntity(
                id = "nutrition-1",
                mealLogId = meal1Id,
                calories = 400,
                confidence = 0.8
            )
        )

        // Meal 2: completed
        val meal2Id = "meal-2"
        database.mealDao().insertMeal(
            MealLogEntity(
                id = meal2Id,
                imageUrl = null,
                capturedAt = 2000L,
                imagePath = "/path/2.jpg",
                analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
            )
        )
        database.mealDao().insertNutritionResult(
            NutritionResultEntity(
                id = "nutrition-2",
                mealLogId = meal2Id,
                calories = 600,
                confidence = 0.9
            )
        )

        // Meal 3: analyzing
        val meal3Id = "meal-3"
        database.mealDao().insertMeal(
            MealLogEntity(
                id = meal3Id,
                imageUrl = null,
                capturedAt = 3000L,
                imagePath = "/path/3.jpg",
                analysisStatus = MealLogEntity.AnalysisStatus.ANALYZING
            )
        )

        // Initial state: 3 items
        val initialMeals = database.mealDao().getAllMealsWithNutrition().first()
        assertEquals(3, initialMeals.size)

        // Cancel (delete) Meal 2
        repository.deleteMeal(meal2Id)

        // 2 items remaining
        val remainingMeals = database.mealDao().getAllMealsWithNutrition().first()
        assertEquals(2, remainingMeals.size)

        // Verify that Meal 1 and Meal 3 remain
        val remainingIds = remainingMeals.map { it.meal.id }
        assertTrue("Meal 1 should remain", remainingIds.contains(meal1Id))
        assertTrue("Meal 3 should remain", remainingIds.contains(meal3Id))
        assertFalse("Meal 2 should be deleted", remainingIds.contains(meal2Id))

        // Verify that Meal 2's nutrition data is also deleted
        val meal2Result = database.mealDao().getMealWithNutritionById(meal2Id)
        assertNull("Meal 2 should be cascade deleted", meal2Result)
    }

    private suspend fun insertMealFixture(
        mealId: String,
        nutritionId: String,
        capturedAt: Long,
        title: String,
        calories: Int,
        items: List<TestMealItem>,
        totalNutrients: List<TestNutrient>
    ) {
        database.mealDao().insertMeal(
            MealLogEntity(
                id = mealId,
                imageUrl = null,
                capturedAt = capturedAt,
                imagePath = "/path/to/$mealId.jpg",
                analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
            )
        )
        database.mealDao().insertNutritionResult(
            NutritionResultEntity(
                id = nutritionId,
                mealLogId = mealId,
                title = title,
                calories = calories,
                confidence = 0.9
            )
        )

        items.forEach { item ->
            database.mealDao().insertMealItem(
                MealItemEntity(
                    id = item.id,
                    nutritionResultId = nutritionId,
                    name = item.name,
                    quantity = item.quantity,
                    calories = item.calories
                )
            )
        }

        database.mealDao().insertNutrients(
            totalNutrients.mapIndexed { index, nutrient ->
                NutrientEntity(
                    id = "$nutritionId-nutrient-$index",
                    nutritionResultId = nutritionId,
                    mealItemId = null,
                    name = nutrient.name,
                    amount = nutrient.amount,
                    unit = nutrient.unit
                )
            }
        )
    }

    private data class TestMealItem(
        val id: String,
        val name: String,
        val quantity: String,
        val calories: Int
    )

    private data class TestNutrient(
        val name: String,
        val amount: Double,
        val unit: String
    )
}
