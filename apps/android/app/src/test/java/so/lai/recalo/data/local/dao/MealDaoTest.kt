package so.lai.recalo.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import so.lai.recalo.data.local.entity.NutritionResultEntity

@RunWith(RobolectricTestRunner::class)
class MealDaoTest {

    private lateinit var database: CaroliDatabase
    private lateinit var dao: MealDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CaroliDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.mealDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert meal and verify it can be retrieved`() = runTest {
        val mealId = UUID.randomUUID().toString()
        val meal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "pending"
        )

        dao.insertMeal(meal)

        val retrieved = dao.getMealById(mealId)
        assertNotNull(retrieved)
        assertEquals(mealId, retrieved?.id)
        assertEquals("pending", retrieved?.analysisStatus)
    }

    @Test
    fun `insert meal with nutrition and verify MealWithNutrition`() = runTest {
        val mealId = UUID.randomUUID().toString()
        val nutritionId = UUID.randomUUID().toString()

        // Insert meal
        val meal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "analyzing"
        )
        dao.insertMeal(meal)

        // Insert nutrition result
        val nutrition = NutritionResultEntity(
            id = nutritionId,
            mealLogId = mealId,
            calories = 450,
            confidence = 0.85
        )
        dao.insertNutritionResult(nutrition)

        // Update status using updateMeal
        val updatedMeal = meal.copy(analysisStatus = "completed")
        dao.updateMeal(updatedMeal)

        // Verify MealWithNutrition
        val mealsWithNutrition = dao.getAllMealsWithNutrition().first()
        assertEquals(1, mealsWithNutrition.size)

        val result = mealsWithNutrition[0]
        assertEquals(mealId, result.meal.id)
        assertEquals("completed", result.meal.analysisStatus)
        assertEquals(450, result.nutritionResult?.calories)
        assertEquals(0.85, result.nutritionResult?.confidence!!, 0.01)
    }

    @Test
    fun `meal without nutrition returns null nutrition`() = runTest {
        val mealId = UUID.randomUUID().toString()

        val meal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "pending"
        )
        dao.insertMeal(meal)

        val mealsWithNutrition = dao.getAllMealsWithNutrition().first()
        assertEquals(1, mealsWithNutrition.size)

        val result = mealsWithNutrition[0]
        assertEquals(mealId, result.meal.id)
        assertNull(result.nutritionResult)
    }

    @Test
    fun `getLatestMealWithNutrition returns most recent meal`() = runTest {
        // Insert first meal
        val meal1 = MealLogEntity(
            id = UUID.randomUUID().toString(),
            imageUrl = null,
            capturedAt = 1000L,
            imagePath = null,
            analysisStatus = "completed"
        )
        dao.insertMeal(meal1)
        dao.insertNutritionResult(
            NutritionResultEntity(
                id = UUID.randomUUID().toString(),
                mealLogId = meal1.id,
                calories = 300,
                confidence = 0.7
            )
        )

        // Insert second (more recent) meal
        val meal2 = MealLogEntity(
            id = UUID.randomUUID().toString(),
            imageUrl = null,
            capturedAt = 2000L,
            imagePath = null,
            analysisStatus = "completed"
        )
        dao.insertMeal(meal2)
        dao.insertNutritionResult(
            NutritionResultEntity(
                id = UUID.randomUUID().toString(),
                mealLogId = meal2.id,
                calories = 500,
                confidence = 0.9
            )
        )

        val latest = dao.getLatestMealWithNutrition()
        assertNotNull(latest)
        assertEquals(meal2.id, latest?.meal?.id)
        assertEquals(500, latest?.nutritionResult?.calories)
    }

    @Test
    fun `searchMealsByFoodName finds meals by title and item name without duplicates`() = runTest {
        val curryMeal = MealLogEntity(
            id = "meal-curry",
            imageUrl = null,
            capturedAt = 3000L,
            imagePath = null,
            analysisStatus = "completed"
        )
        val proteinMeal = MealLogEntity(
            id = "meal-protein",
            imageUrl = null,
            capturedAt = 2000L,
            imagePath = null,
            analysisStatus = "completed"
        )
        dao.insertMeal(curryMeal)
        dao.insertMeal(proteinMeal)

        dao.insertNutritionResult(
            NutritionResultEntity(
                id = "nutrition-curry",
                mealLogId = curryMeal.id,
                title = "Curry Rice",
                calories = 700,
                confidence = 0.9
            )
        )
        dao.insertNutritionResult(
            NutritionResultEntity(
                id = "nutrition-protein",
                mealLogId = proteinMeal.id,
                title = "Breakfast",
                calories = 180,
                confidence = 0.8
            )
        )

        dao.insertMealItem(
            MealItemEntity(
                id = "item-curry",
                nutritionResultId = "nutrition-curry",
                name = "Curry",
                quantity = "1 plate",
                calories = 700
            )
        )
        dao.insertMealItem(
            MealItemEntity(
                id = "item-protein",
                nutritionResultId = "nutrition-protein",
                name = "Protein Shake",
                quantity = "1 cup",
                calories = 180
            )
        )

        val curryResults = dao.searchMealsByFoodName("curry")
        assertEquals(1, curryResults.size)
        assertEquals("meal-curry", curryResults.first().meal.id)

        val proteinResults = dao.searchMealsByFoodName("protein")
        assertEquals(1, proteinResults.size)
        assertEquals("meal-protein", proteinResults.first().meal.id)
    }

    @Test
    fun `searchMealsByFoodName finds previous meals by meal and food names`() = runTest {
        dao.insertMeal(
            MealLogEntity(
                id = "meal-curry-rice",
                imageUrl = null,
                capturedAt = 1000L,
                imagePath = null,
                analysisStatus = "completed"
            )
        )
        dao.insertMeal(
            MealLogEntity(
                id = "meal-natto-rice",
                imageUrl = null,
                capturedAt = 2000L,
                imagePath = null,
                analysisStatus = "completed"
            )
        )
        dao.insertMeal(
            MealLogEntity(
                id = "meal-created-only-rice",
                imageUrl = null,
                capturedAt = null,
                imagePath = null,
                analysisStatus = "completed",
                createdAt = 3000L
            )
        )

        dao.insertNutritionResult(
            NutritionResultEntity(
                id = "nutrition-curry-rice",
                mealLogId = "meal-curry-rice",
                title = "Curry Rice",
                calories = 750,
                confidence = 0.9
            )
        )
        dao.insertNutritionResult(
            NutritionResultEntity(
                id = "nutrition-natto-rice",
                mealLogId = "meal-natto-rice",
                title = "Natto Rice",
                calories = 430,
                confidence = 0.85
            )
        )
        dao.insertNutritionResult(
            NutritionResultEntity(
                id = "nutrition-created-only-rice",
                mealLogId = "meal-created-only-rice",
                title = "Egg Rice",
                calories = 500,
                confidence = 0.8
            )
        )

        dao.insertMealItem(
            MealItemEntity(
                id = "item-curry",
                nutritionResultId = "nutrition-curry-rice",
                name = "Curry",
                quantity = "1 plate",
                calories = 450
            )
        )
        dao.insertMealItem(
            MealItemEntity(
                id = "item-curry-rice",
                nutritionResultId = "nutrition-curry-rice",
                name = "Rice",
                quantity = "1 bowl",
                calories = 300
            )
        )
        dao.insertMealItem(
            MealItemEntity(
                id = "item-natto",
                nutritionResultId = "nutrition-natto-rice",
                name = "Natto",
                quantity = "1 pack",
                calories = 100
            )
        )
        dao.insertMealItem(
            MealItemEntity(
                id = "item-natto-rice",
                nutritionResultId = "nutrition-natto-rice",
                name = "Rice",
                quantity = "1 bowl",
                calories = 330
            )
        )
        dao.insertMealItem(
            MealItemEntity(
                id = "item-created-only-rice",
                nutritionResultId = "nutrition-created-only-rice",
                name = "Rice",
                quantity = "1 bowl",
                calories = 300
            )
        )

        val curryResults = dao.searchMealsByFoodName("curry")
        assertEquals(listOf("meal-curry-rice"), curryResults.map { it.meal.id })

        val riceResults = dao.searchMealsByFoodName("rice")
        assertEquals(
            listOf("meal-created-only-rice", "meal-natto-rice", "meal-curry-rice"),
            riceResults.map { it.meal.id }
        )

        assertTrue(dao.searchMealsByFoodName("pizza-zzz").isEmpty())
    }

    @Test
    fun `delete meal also deletes nutrition result`() = runTest {
        val mealId = UUID.randomUUID().toString()

        val meal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "completed"
        )
        dao.insertMeal(meal)

        dao.insertNutritionResult(
            NutritionResultEntity(
                id = UUID.randomUUID().toString(),
                mealLogId = mealId,
                calories = 400,
                confidence = 0.8
            )
        )

        // Delete meal
        dao.deleteMealById(mealId)

        // Verify both meal and nutrition are deleted
        val meals = dao.getAllMealsWithNutrition().first()
        assertTrue(meals.isEmpty())
    }

    @Test
    fun `update meal status preserves nutrition data`() = runTest {
        val mealId = UUID.randomUUID().toString()

        // Insert meal with analyzing status
        val meal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "analyzing"
        )
        dao.insertMeal(meal)

        // Insert nutrition
        dao.insertNutritionResult(
            NutritionResultEntity(
                id = UUID.randomUUID().toString(),
                mealLogId = mealId,
                calories = 550,
                confidence = 0.9
            )
        )

        // Update status to completed using updateMeal
        val updatedMeal = meal.copy(
            analysisStatus = "completed",
            analysisCompletedAt = System.currentTimeMillis()
        )
        dao.updateMeal(updatedMeal)

        // Verify nutrition data is preserved
        val result = dao.getLatestMealWithNutrition()
        assertNotNull(result)
        assertEquals("completed", result?.meal?.analysisStatus)
        assertEquals(550, result?.nutritionResult?.calories)
        assertEquals(0.9, result?.nutritionResult?.confidence!!, 0.01)
    }

    // ============================================================
    // REGRESSION TESTS: CASCADE DELETE issue (#2026-03-07)
    // ============================================================
    // Problem: Updating MealLogEntity with OnConflictStrategy.REPLACE
    // would trigger DELETE -> INSERT internally in Room,
    // which deleted NutritionResultEntity due to ForeignKey.CASCADE.
    //
    // Fix: Use updateMeal() to perform UPDATE only.
    // ============================================================

    @Test
    fun `REGRESSION nutrition data should be preserved when updating meal status from analyzing to completed`() = runTest {
        val mealId = UUID.randomUUID().toString()
        val expectedCalories = 450
        val expectedConfidence = 0.75

        // Step 1: Create meal in analyzing state
        val analyzingMeal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "analyzing"
        )
        dao.insertMeal(analyzingMeal)

        // Step 2: Insert nutrition result
        dao.insertNutritionResult(
            NutritionResultEntity(
                id = UUID.randomUUID().toString(),
                mealLogId = mealId,
                calories = expectedCalories,
                confidence = expectedConfidence
            )
        )

        // Step 3: Verify nutrition data is present in analyzing state
        val analyzingResult = dao.getLatestMealWithNutrition()
        assertNotNull("Analyzing state should have nutrition", analyzingResult)
        assertEquals("analyzing", analyzingResult?.meal?.analysisStatus)
        assertEquals(expectedCalories, analyzingResult?.nutritionResult?.calories)

        // Step 4: Update status to completed using updateMeal
        val completedMeal = analyzingMeal.copy(
            analysisStatus = "completed",
            analysisCompletedAt = System.currentTimeMillis()
        )
        dao.updateMeal(completedMeal)

        // Step 5: Verify nutrition data is preserved in completed state
        val completedResult = dao.getLatestMealWithNutrition()
        assertNotNull("Completed state should preserve nutrition", completedResult)
        assertEquals("completed", completedResult?.meal?.analysisStatus)
        assertEquals(
            "Calories should be preserved after status update",
            expectedCalories,
            completedResult?.nutritionResult?.calories
        )
        assertEquals(
            "Confidence should be preserved after status update",
            expectedConfidence,
            completedResult?.nutritionResult?.confidence!!,
            0.01
        )
    }

    @Test
    fun `REGRESSION nutrition data should be preserved through multiple status updates`() = runTest {
        val mealId = UUID.randomUUID().toString()
        val expectedCalories = 600

        // Initial meal creation
        val meal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "pending"
        )
        dao.insertMeal(meal)

        // Insert nutrition result
        dao.insertNutritionResult(
            NutritionResultEntity(
                id = UUID.randomUUID().toString(),
                mealLogId = mealId,
                calories = expectedCalories,
                confidence = 0.8
            )
        )

        // Update through multiple statuses: analyzing -> completed -> error -> completed
        val analyzingMeal = meal.copy(analysisStatus = "analyzing")
        dao.updateMeal(analyzingMeal)

        val completedMeal = analyzingMeal.copy(analysisStatus = "completed")
        dao.updateMeal(completedMeal)

        val errorMeal = completedMeal.copy(
            analysisStatus = "error",
            analysisError = "Test error"
        )
        dao.updateMeal(errorMeal)

        val completedAgainMeal = errorMeal.copy(
            analysisStatus = "completed",
            analysisError = null
        )
        dao.updateMeal(completedAgainMeal)

        // Verify nutrition data is still preserved
        val result = dao.getLatestMealWithNutrition()
        assertNotNull(result)
        assertEquals("completed", result?.meal?.analysisStatus)
        assertEquals(expectedCalories, result?.nutritionResult?.calories)
    }

    @Test
    fun `REGRESSION insertMeal ABORT should reject duplicate and updateMeal should preserve nutrition`() = runTest {
        val mealId = UUID.randomUUID().toString()
        val expectedCalories = 500

        // Step 1: Create meal and nutrition data
        val meal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "analyzing"
        )
        dao.insertMeal(meal)
        dao.insertNutritionResult(
            NutritionResultEntity(
                id = UUID.randomUUID().toString(),
                mealLogId = mealId,
                calories = expectedCalories,
                confidence = 0.8
            )
        )

        // Step 2: insertMeal(ABORT) should throw exception on duplicate ID
        val duplicateMeal = meal.copy(analysisStatus = "duplicate_test")
        try {
            dao.insertMeal(duplicateMeal)
            fail("insertMeal with duplicate ID should throw exception with ABORT strategy")
        } catch (e: Exception) {
            // Expected
        }

        // Verify nutrition is still there
        val afterAbort = dao.getLatestMealWithNutrition()
        assertNotNull(
            "Nutrition should be preserved after ABORT",
            afterAbort?.nutritionResult
        )
        assertEquals(expectedCalories, afterAbort?.nutritionResult?.calories)

        // Step 3: updateMeal should preserve nutrition
        val updatedMeal = meal.copy(analysisStatus = "completed")
        dao.updateMeal(updatedMeal)

        val updatedResult = dao.getLatestMealWithNutrition()
        assertNotNull(
            "updateMeal should preserve nutrition",
            updatedResult?.nutritionResult
        )
        assertEquals(expectedCalories, updatedResult?.nutritionResult?.calories)
    }

    @Test
    fun `REGRESSION nutrition data should be preserved when updating to error status`() = runTest {
        val mealId = UUID.randomUUID().toString()
        val expectedCalories = 400

        val meal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "analyzing"
        )
        dao.insertMeal(meal)
        dao.insertNutritionResult(
            NutritionResultEntity(
                id = UUID.randomUUID().toString(),
                mealLogId = mealId,
                calories = expectedCalories,
                confidence = 0.7
            )
        )

        // Update to error status
        val errorMeal = meal.copy(
            analysisStatus = "error",
            analysisError = "API timeout"
        )
        dao.updateMeal(errorMeal)

        // Verify nutrition is preserved
        val result = dao.getLatestMealWithNutrition()
        assertNotNull(result)
        assertEquals("error", result?.meal?.analysisStatus)
        assertEquals("API timeout", result?.meal?.analysisError)
        assertEquals(expectedCalories, result?.nutritionResult?.calories)
    }

    @Test
    fun `REGRESSION MealWithNutrition nutritionResult property should work correctly`() = runTest {
        val mealId = UUID.randomUUID().toString()

        // Meal with nutrition
        val meal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "completed"
        )
        dao.insertMeal(meal)
        dao.insertNutritionResult(
            NutritionResultEntity(
                id = UUID.randomUUID().toString(),
                mealLogId = mealId,
                calories = 350,
                confidence = 0.9
            )
        )

        val result = dao.getLatestMealWithNutrition()
        assertNotNull(result)

        // Verify nutritionResult property returns NutritionResultEntity
        val nutrition = result?.nutritionResult
        assertNotNull(nutrition)
        assertEquals(350, nutrition?.calories)
        assertEquals(0.9, nutrition?.confidence!!, 0.01)
    }

    @Test
    fun `beginAnalysisRetry only claims an error meal once`() = runTest {
        val meal = MealLogEntity(
            id = "retry-meal",
            imageUrl = null,
            capturedAt = 1000L,
            imagePath = "/path/to/meal.jpg",
            analysisStatus = MealLogEntity.AnalysisStatus.ERROR,
            analysisError = "SERVICE_UNAVAILABLE"
        )
        dao.insertMeal(meal)

        assertEquals(1, dao.beginAnalysisRetry(meal.id))
        assertEquals(0, dao.beginAnalysisRetry(meal.id))

        val claimedMeal = dao.getMealById(meal.id)
        assertEquals(MealLogEntity.AnalysisStatus.ANALYZING, claimedMeal?.analysisStatus)
        assertNull(claimedMeal?.analysisError)
    }

    @Test
    fun `replaceAnalysisResult removes stale result and completes meal atomically`() = runTest {
        val failedMeal = MealLogEntity(
            id = "partially-saved-meal",
            imageUrl = null,
            capturedAt = 1000L,
            imagePath = "/path/to/meal.jpg",
            analysisStatus = MealLogEntity.AnalysisStatus.ERROR,
            analysisError = "UNKNOWN"
        )
        dao.insertMeal(failedMeal)
        dao.insertNutritionResult(
            NutritionResultEntity(
                id = "stale-result",
                mealLogId = failedMeal.id,
                calories = 100,
                confidence = 0.1
            )
        )

        val completedMeal = failedMeal.copy(
            analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED,
            analysisError = null,
            analysisCompletedAt = 2000L
        )
        dao.replaceAnalysisResult(
            meal = completedMeal,
            result = NutritionResultEntity(
                id = "fresh-result",
                mealLogId = failedMeal.id,
                calories = 450,
                confidence = 0.9
            ),
            items = emptyList(),
            nutrients = emptyList()
        )

        val result = dao.getMealWithNutritionById(failedMeal.id)
        assertEquals(MealLogEntity.AnalysisStatus.COMPLETED, result?.meal?.analysisStatus)
        assertNull(result?.meal?.analysisError)
        assertEquals("fresh-result", result?.nutritionResult?.id)
        assertEquals(450, result?.nutritionResult?.calories)
    }
}
