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
}
