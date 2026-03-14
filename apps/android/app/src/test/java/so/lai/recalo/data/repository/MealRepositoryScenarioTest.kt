package so.lai.recalo.data.repository

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
}
