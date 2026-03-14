package so.lai.recalo.ui.screens

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import so.lai.recalo.data.api.SessionManager
import so.lai.recalo.data.local.CaroliDatabase
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity
import so.lai.recalo.data.repository.MealRepository

@RunWith(RobolectricTestRunner::class)
class ResultScreenCancelTest {

    private lateinit var database: CaroliDatabase
    private lateinit var repository: MealRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, CaroliDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MealRepository(dao = database.mealDao())
        sessionManager = SessionManager(context)
        viewModel = HomeViewModel(sessionManager)

        // Set repository directly via reflection
        val field = HomeViewModel::class.java.getDeclaredField("mealRepository")
        field.isAccessible = true
        field.set(viewModel, repository)
        viewModel.observeMeals()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
        sessionManager.clear()
    }

    @Test
    fun cancel_button_should_delete_meal_from_database() = runTest {
        val mealId = UUID.randomUUID().toString()

        // Register meal and nutrition data
        database.mealDao().insertMeal(
            MealLogEntity(
                id = mealId,
                imageUrl = null,
                capturedAt = System.currentTimeMillis(),
                imagePath = "/path/meal.jpg",
                analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
            )
        )
        database.mealDao().insertNutritionResult(
            NutritionResultEntity(
                id = "nutrition-cancel-test",
                mealLogId = mealId,
                title = "Cancel Test Meal",
                calories = 500,
                confidence = 0.85
            )
        )

        // Wait for Flow update
        for (i in 0..10) {
            if (viewModel.meals.isNotEmpty()) break
            kotlinx.coroutines.delay(100)
        }

        // Verify registration
        assertEquals(1, database.mealDao().getMealById(mealId)?.let { 1 } ?: 0)
        assertEquals(1, database.mealDao().getAllMealsWithNutrition().first().size)
        assertNotNull(database.mealDao().getMealById(mealId))

        // Execute cancel (delete)
        val deleteJob = viewModel.deleteMeal(mealId, null)
        deleteJob.join()

        // Verify deletion
        assertEquals(
            "Meal should be deleted after cancel",
            0,
            database.mealDao().getAllMealsWithNutrition().first().size
        )
        assertNull(
            "Meal should be removed from database",
            database.mealDao().getMealById(mealId)
        )
        assertNull(
            "Nutrition result should be cascade deleted",
            database.mealDao().getMealWithNutritionById(mealId)
        )
    }

    @Test
    fun cancel_should_remove_meal_from_home_screen() = runTest {
        val mealId = "cancel-home-test"

        // Register meal
        database.mealDao().insertMeal(
            MealLogEntity(
                id = mealId,
                imageUrl = null,
                capturedAt = System.currentTimeMillis(),
                imagePath = "/path/meal.jpg",
                analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
            )
        )
        database.mealDao().insertNutritionResult(
            NutritionResultEntity(
                id = "nutrition-home-test",
                mealLogId = mealId,
                calories = 400,
                confidence = 0.75
            )
        )

        for (i in 0..10) {
            if (viewModel.meals.isNotEmpty()) break
            kotlinx.coroutines.delay(100)
        }
        assertEquals(1, database.mealDao().getMealById(mealId)?.let { 1 } ?: 0)
        assertEquals(1, database.mealDao().getAllMealsWithNutrition().first().size)

        // Cancel (delete) and wait for completion
        val deleteJob = viewModel.deleteMeal(mealId, null)
        deleteJob.join()

        // Verify it disappeared from home screen
        assertTrue(
            "Meal should disappear from home screen after cancel",
            database.mealDao().getAllMealsWithNutrition().first().isEmpty()
        )
    }
}
