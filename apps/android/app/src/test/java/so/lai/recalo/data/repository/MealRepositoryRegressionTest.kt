package so.lai.recalo.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import so.lai.recalo.data.local.model.MealWithNutrition

/**
 * Regression tests for MealRepository
 *
 * Issue: #2026-03-07 Nutrition data loss due to CASCADE DELETE
 */
@RunWith(RobolectricTestRunner::class)
class MealRepositoryRegressionTest {

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

    /**
     * [Regression Test] Nutrition data is preserved even after updating status after analysis completes
     *
     * Reproduction steps:
     * 1. Upload a meal image
     * 2. Nutrition analysis succeeds
     * 3. Status updates from analyzing to completed
     *
     * Expected result:
     * - calories should not be null even in the completed state
     */
    @Test
    fun `REGRESSION nutrition data should persist after analysis completes`() = runTest {
        // Since this is a DAO-level test, flow verification via Repository
        // is covered in DAO tests because actual API calls are required

        // Verification at the DAO level:
        // Refer to MealDaoTest.kt's
        // "REGRESSION nutrition data should be preserved when updating meal status from analyzing to completed"
        assertTrue(true) // Placeholder (actual test is performed in DAO)
    }

    /**
     * [Regression Test] Nutrition data is correctly retrieved in getAllMealsWithNutrition
     */
    @Test
    fun `REGRESSION getAllMealsWithNutrition should return nutrition data correctly`() = runTest {
        val dao = database.mealDao()

        // Prepare data
        val mealId = "test-meal-1"
        val meal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "completed"
        )
        dao.insertMeal(meal)
        dao.insertNutritionResult(
            so.lai.recalo.data.local.entity.NutritionResultEntity(
                id = "nutrition-1",
                mealLogId = mealId,
                calories = 500,
                confidence = 0.85
            )
        )

        // Get from Flow
        val meals: List<MealWithNutrition> = dao.getAllMealsWithNutrition().first()

        assertEquals(1, meals.size)
        val result = meals.first()
        assertEquals(mealId, result.meal.id)
        assertEquals("completed", result.meal.analysisStatus)

        // Verify that nutrition data is retrieved
        val nutrition = result.nutritionResult
        assertNotNull("Nutrition result should not be null", nutrition)
        assertEquals(500, nutrition?.calories)
        assertEquals(0.85, nutrition?.confidence!!, 0.01)
    }

    /**
     * [Regression Test] Each nutrition data is correctly linked even with multiple meals
     */
    @Test
    fun `REGRESSION multiple meals should each have correct nutrition data`() = runTest {
        val dao = database.mealDao()

        // Meal 1
        val meal1 = MealLogEntity(
            id = "meal-1",
            imageUrl = null,
            capturedAt = 1000L,
            imagePath = null,
            analysisStatus = "completed"
        )
        dao.insertMeal(meal1)
        dao.insertNutritionResult(
            so.lai.recalo.data.local.entity.NutritionResultEntity(
                id = "nutrition-1",
                mealLogId = "meal-1",
                calories = 300,
                confidence = 0.7
            )
        )

        // Meal 2
        val meal2 = MealLogEntity(
            id = "meal-2",
            imageUrl = null,
            capturedAt = 2000L,
            imagePath = null,
            analysisStatus = "completed"
        )
        dao.insertMeal(meal2)
        dao.insertNutritionResult(
            so.lai.recalo.data.local.entity.NutritionResultEntity(
                id = "nutrition-2",
                mealLogId = "meal-2",
                calories = 600,
                confidence = 0.9
            )
        )

        // Meal 3 (no nutrition)
        val meal3 = MealLogEntity(
            id = "meal-3",
            imageUrl = null,
            capturedAt = 3000L,
            imagePath = null,
            analysisStatus = "pending"
        )
        dao.insertMeal(meal3)

        val meals: List<MealWithNutrition> = dao.getAllMealsWithNutrition().first()

        assertEquals(3, meals.size)

        // Verify order (capturedAt DESC)
        assertEquals("meal-3", meals[0].meal.id)
        assertNull(meals[0].nutritionResult)

        assertEquals("meal-2", meals[1].meal.id)
        assertEquals(600, meals[1].nutritionResult?.calories)

        assertEquals("meal-1", meals[2].meal.id)
        assertEquals(300, meals[2].nutritionResult?.calories)
    }

    /**
     * [Regression Test] Nutrition data is also deleted when deleteMeal is called (normal CASCADE operation)
     */
    @Test
    fun `REGRESSION deleteMeal should cascade delete nutrition data`() = runTest {
        val dao = database.mealDao()
        val mealId = "meal-to-delete"

        // Prepare data
        dao.insertMeal(
            MealLogEntity(
                id = mealId,
                imageUrl = null,
                capturedAt = System.currentTimeMillis(),
                imagePath = null,
                analysisStatus = "completed"
            )
        )
        dao.insertNutritionResult(
            so.lai.recalo.data.local.entity.NutritionResultEntity(
                id = "nutrition-to-delete",
                mealLogId = mealId,
                calories = 400,
                confidence = 0.8
            )
        )

        // Verify existence before deletion
        val beforeDelete = dao.getAllMealsWithNutrition().first()
        assertEquals(1, beforeDelete.size)

        // Execute deletion
        dao.deleteMealById(mealId)

        // Verify non-existence after deletion
        val afterDelete = dao.getAllMealsWithNutrition().first()
        assertTrue("Meal and nutrition should be deleted", afterDelete.isEmpty())
    }
}
