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

/**
 * ResultScreen 表示時のキャンセルボブントテスト
 *
 * 問題：キャンセルボタンを押しても DB から削除されず、
 * Home 画面に表示されたままだった
 */
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

        // リポジトリを直接セット
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

    /**
     * [回帰テスト] ResultScreen でキャンセル→食事が削除される
     */
    @Test
    fun cancel_button_should_delete_meal_from_database() = runTest {
        val mealId = UUID.randomUUID().toString()

        // 食事と栄養データを登録（解析完了状態）
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

        // Flow 更新を待つ
        for (i in 0..10) {
            if (viewModel.meals.isNotEmpty()) break
            kotlinx.coroutines.delay(100)
        }

        // 登録確認
        assertEquals(1, database.mealDao().getMealById(mealId)?.let { 1 } ?: 0)
        assertEquals(1, database.mealDao().getAllMealsWithNutrition().first().size)
        assertNotNull(database.mealDao().getMealById(mealId))

        // キャンセル実行（削除）
        val deleteJob = viewModel.deleteMeal(mealId, null)
        deleteJob.join()

        // 削除確認
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

    /**
     * [回帰テスト] キャンセル後に Home 画面から消える
     */
    @Test
    fun cancel_should_remove_meal_from_home_screen() = runTest {
        val mealId = "cancel-home-test"

        // 食事登録
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

        // キャンセル（削除）して完了を待つ
        val deleteJob = viewModel.deleteMeal(mealId, null)
        deleteJob.join()

        // Home 画面から消えていることを確認
        assertTrue(
            "Meal should disappear from home screen after cancel",
            database.mealDao().getAllMealsWithNutrition().first().isEmpty()
        )
    }
}
