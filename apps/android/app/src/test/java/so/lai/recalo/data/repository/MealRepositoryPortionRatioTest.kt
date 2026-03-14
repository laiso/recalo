package so.lai.recalo.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import java.util.UUID

/**
 * Tests for portion ratio adjustment feature
 *
 * Feature overview:
 * - Adjust the portion of the entire meal by a ratio (0.5x, 1.0x, 1.5x, 2.0x)
 * - Automatically recalculate calories, nutrients, and quantities when the ratio is changed
 * - Correctly recalculate from a previous ratio to a new ratio
 */
@RunWith(RobolectricTestRunner::class)
class MealRepositoryPortionRatioTest {

    private lateinit var database: CaroliDatabase
    private lateinit var repository: MealRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CaroliDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MealRepository(database.mealDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createMealWithNutrition(portionRatio: Double = 1.0): Triple<String, String, String> {
        val mealId = UUID.randomUUID().toString()
        val resultId = UUID.randomUUID().toString()
        val itemId = UUID.randomUUID().toString()
        
        // Insert meal log first (required for foreign key)
        val mealLog = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = "/test/image.jpg",
            analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
        )
        database.mealDao().insertMeal(mealLog)
        
        val nutritionResult = NutritionResultEntity(
            id = resultId,
            mealLogId = mealId,
            title = "Test Meal",
            calories = 500,
            confidence = 0.9,
            portionRatio = portionRatio
        )
        database.mealDao().insertNutritionResult(nutritionResult)
        
        val mealItem = MealItemEntity(
            id = itemId,
            nutritionResultId = resultId,
            name = "Rice",
            quantity = "1 cup",
            calories = (200 * portionRatio).toInt()
        )
        database.mealDao().insertMealItem(mealItem)
        
        return Triple(mealId, resultId, itemId)
    }

    @Test
    fun updatePortionRatio_shouldUpdateNutritionResultRatio() = runTest {
        // Arrange
        val (_, resultId, _) = createMealWithNutrition(portionRatio = 1.0)
        
        // Act
        repository.updatePortionRatio(resultId, 1.5)
        
        // Assert
        val updatedResult = database.mealDao().getNutritionResultById(resultId)
        assertNotNull(updatedResult)
        assertEquals(1.5, updatedResult?.portionRatio ?: 0.0, 0.01)
    }

    @Test
    fun updatePortionRatio_shouldScaleItemCalories() = runTest {
        // Arrange
        val (_, resultId, itemId) = createMealWithNutrition(portionRatio = 1.0)
        
        // Act
        repository.updatePortionRatio(resultId, 2.0)
        
        // Assert
        val updatedItem = database.mealDao().getMealItemById(itemId)
        assertNotNull(updatedItem)
        assertEquals(400, updatedItem?.calories) // 200 * 2.0
    }

    @Test
    fun updatePortionRatio_shouldScaleItemQuantity() = runTest {
        // Arrange
        val (_, resultId, itemId) = createMealWithNutrition(portionRatio = 1.0)
        
        // Act
        repository.updatePortionRatio(resultId, 2.0)
        
        // Assert
        val updatedItem = database.mealDao().getMealItemById(itemId)
        assertNotNull(updatedItem)
        assertTrue(updatedItem?.quantity?.contains("2") == true) // "1 cup" -> "2 cup"
    }

    @Test
    fun updatePortionRatio_shouldRecalculateTotalCalories() = runTest {
        // Arrange
        val mealId = UUID.randomUUID().toString()
        val resultId = UUID.randomUUID().toString()
        val item1Id = UUID.randomUUID().toString()
        val item2Id = UUID.randomUUID().toString()
        
        val mealLog = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = "/test/image.jpg",
            analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
        )
        database.mealDao().insertMeal(mealLog)
        
        val nutritionResult = NutritionResultEntity(
            id = resultId,
            mealLogId = mealId,
            title = "Test Meal",
            calories = 500,
            confidence = 0.9,
            portionRatio = 1.0
        )
        database.mealDao().insertNutritionResult(nutritionResult)
        
        val item1 = MealItemEntity(
            id = item1Id,
            nutritionResultId = resultId,
            name = "Rice",
            quantity = "1 cup",
            calories = 200
        )
        val item2 = MealItemEntity(
            id = item2Id,
            nutritionResultId = resultId,
            name = "Fish",
            quantity = "1 piece",
            calories = 300
        )
        database.mealDao().insertMealItem(item1)
        database.mealDao().insertMealItem(item2)
        
        // Act
        repository.updatePortionRatio(resultId, 0.5)
        
        // Assert
        val updatedResult = database.mealDao().getNutritionResultById(resultId)
        // Total should be (200 * 0.5) + (300 * 0.5) = 250
        assertEquals(250, updatedResult?.calories)
    }

    @Test
    fun updatePortionRatio_shouldScaleNutrients() = runTest {
        // Arrange
        val mealId = UUID.randomUUID().toString()
        val resultId = UUID.randomUUID().toString()
        val itemId = UUID.randomUUID().toString()
        val nutrientId = UUID.randomUUID().toString()
        
        val mealLog = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = "/test/image.jpg",
            analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
        )
        database.mealDao().insertMeal(mealLog)
        
        val nutritionResult = NutritionResultEntity(
            id = resultId,
            mealLogId = mealId,
            title = "Test Meal",
            calories = 500,
            confidence = 0.9,
            portionRatio = 1.0
        )
        database.mealDao().insertNutritionResult(nutritionResult)
        
        val mealItem = MealItemEntity(
            id = itemId,
            nutritionResultId = resultId,
            name = "Rice",
            quantity = "1 cup",
            calories = 200
        )
        database.mealDao().insertMealItem(mealItem)
        
        val nutrient = NutrientEntity(
            id = nutrientId,
            nutritionResultId = null,
            mealItemId = itemId,
            name = "Protein",
            amount = 10.0,
            unit = "g"
        )
        database.mealDao().insertNutrients(listOf(nutrient))
        
        // Act
        repository.updatePortionRatio(resultId, 1.5)
        
        // Assert
        val updatedNutrient = database.mealDao().getNutrientsByMealItemId(itemId).firstOrNull()
        assertNotNull(updatedNutrient)
        assertEquals(15.0, updatedNutrient?.amount ?: 0.0, 0.01) // 10.0 * 1.5
    }

    @Test
    fun updatePortionRatio_changingFromNonOneRatio_shouldCalculateCorrectly() = runTest {
        // Arrange
        val mealId = UUID.randomUUID().toString()
        val resultId = UUID.randomUUID().toString()
        val itemId = UUID.randomUUID().toString()
        
        // Start with 1.5x ratio
        val mealLog = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = "/test/image.jpg",
            analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
        )
        database.mealDao().insertMeal(mealLog)
        
        val nutritionResult = NutritionResultEntity(
            id = resultId,
            mealLogId = mealId,
            title = "Test Meal",
            calories = 750, // 500 * 1.5
            confidence = 0.9,
            portionRatio = 1.5
        )
        database.mealDao().insertNutritionResult(nutritionResult)
        
        val mealItem = MealItemEntity(
            id = itemId,
            nutritionResultId = resultId,
            name = "Rice",
            quantity = "1.5 cup", // Already scaled
            calories = 300 // 200 * 1.5
        )
        database.mealDao().insertMealItem(mealItem)
        
        // Act: Change from 1.5x to 2.0x
        repository.updatePortionRatio(resultId, 2.0)
        
        // Assert
        val updatedItem = database.mealDao().getMealItemById(itemId)
        assertNotNull(updatedItem)
        // Base calories (200) * 2.0 = 400
        assertEquals(400, updatedItem?.calories)
        
        val updatedResult = database.mealDao().getNutritionResultById(resultId)
        assertEquals(2.0, updatedResult?.portionRatio ?: 0.0, 0.01)
    }

    @Test
    fun updatePortionRatio_shouldHandleDecreaseFromHighRatio() = runTest {
        // Arrange
        val mealId = UUID.randomUUID().toString()
        val resultId = UUID.randomUUID().toString()
        val itemId = UUID.randomUUID().toString()
        
        // Start with 2.0x ratio
        val mealLog = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = "/test/image.jpg",
            analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
        )
        database.mealDao().insertMeal(mealLog)
        
        val nutritionResult = NutritionResultEntity(
            id = resultId,
            mealLogId = mealId,
            title = "Test Meal",
            calories = 1000, // 500 * 2.0
            confidence = 0.9,
            portionRatio = 2.0
        )
        database.mealDao().insertNutritionResult(nutritionResult)
        
        val mealItem = MealItemEntity(
            id = itemId,
            nutritionResultId = resultId,
            name = "Rice",
            quantity = "2 cup", // Already scaled
            calories = 400 // 200 * 2.0
        )
        database.mealDao().insertMealItem(mealItem)
        
        // Act: Change from 2.0x to 0.5x
        repository.updatePortionRatio(resultId, 0.5)
        
        // Assert
        val updatedItem = database.mealDao().getMealItemById(itemId)
        assertNotNull(updatedItem)
        // Base calories (200) * 0.5 = 100
        assertEquals(100, updatedItem?.calories)
        
        val updatedResult = database.mealDao().getNutritionResultById(resultId)
        assertEquals(0.5, updatedResult?.portionRatio ?: 0.0, 0.01)
    }

    @Test
    fun updatePortionRatio_shouldHandleMultipleItems() = runTest {
        // Arrange
        val mealId = UUID.randomUUID().toString()
        val resultId = UUID.randomUUID().toString()
        val item1Id = UUID.randomUUID().toString()
        val item2Id = UUID.randomUUID().toString()
        val item3Id = UUID.randomUUID().toString()
        
        val mealLog = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = "/test/image.jpg",
            analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
        )
        database.mealDao().insertMeal(mealLog)
        
        val nutritionResult = NutritionResultEntity(
            id = resultId,
            mealLogId = mealId,
            title = "Test Meal",
            calories = 600,
            confidence = 0.9,
            portionRatio = 1.0
        )
        database.mealDao().insertNutritionResult(nutritionResult)
        
        listOf(
            MealItemEntity(item1Id, resultId, "Rice", "1 cup", 200),
            MealItemEntity(item2Id, resultId, "Fish", "1 piece", 300),
            MealItemEntity(item3Id, resultId, "Soup", "1 bowl", 100)
        ).forEach { database.mealDao().insertMealItem(it) }
        
        // Act
        repository.updatePortionRatio(resultId, 1.5)
        
        // Assert - all items should be scaled
        val items = database.mealDao().getMealItemsByNutritionResultId(resultId)
        assertEquals(3, items.size)
        assertEquals(300, items[0].calories) // 200 * 1.5
        assertEquals(450, items[1].calories) // 300 * 1.5
        assertEquals(150, items[2].calories) // 100 * 1.5
        
        // Total should be 900
        val updatedResult = database.mealDao().getNutritionResultById(resultId)
        assertEquals(900, updatedResult?.calories)
    }
}
