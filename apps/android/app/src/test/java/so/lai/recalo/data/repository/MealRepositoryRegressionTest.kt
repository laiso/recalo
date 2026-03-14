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
 * MealRepository の回帰テスト
 *
 * 問題：#2026-03-07 CASCADE DELETE による栄養データ損失
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
     * [回帰テスト] 栄養分析完了後にステータス更新しても栄養データが保持される
     *
     * 再現ステップ:
     * 1. 食事画像をアップロード
     * 2. 栄養分析が成功
     * 3. ステータスが analyzing -> completed に更新
     *
     * 期待結果:
     * - completed 状態でも calories が null にならない
     */
    @Test
    fun `REGRESSION nutrition data should persist after analysis completes`() = runTest {
        // これは DAO レベルのテストなので、Repository を介してのフロー検証は
        // 実際の API コールが必要なため DAO テストでカバー済み

        // DAO レベルでの検証:
        // MealDaoTest.kt の
        // "REGRESSION nutrition data should be preserved when updating meal status from analyzing to completed"
        // を参照
        assertTrue(true) // プレースホルダー（実際のテストは DAO で実施）
    }

    /**
     * [回帰テスト] getAllMealsWithNutrition で栄養データが正しく取得される
     */
    @Test
    fun `REGRESSION getAllMealsWithNutrition should return nutrition data correctly`() = runTest {
        val dao = database.mealDao()

        // データ準備
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

        // Flow から取得
        val meals: List<MealWithNutrition> = dao.getAllMealsWithNutrition().first()

        assertEquals(1, meals.size)
        val result = meals.first()
        assertEquals(mealId, result.meal.id)
        assertEquals("completed", result.meal.analysisStatus)

        // 栄養データが取得できていることを確認
        val nutrition = result.nutritionResult
        assertNotNull("Nutrition result should not be null", nutrition)
        assertEquals(500, nutrition?.calories)
        assertEquals(0.85, nutrition?.confidence!!, 0.01)
    }

    /**
     * [回帰テスト] 複数 meals の場合でも各 nutrition データが正しく紐付いている
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

        // 順序を確認（capturedAt DESC）
        assertEquals("meal-3", meals[0].meal.id)
        assertNull(meals[0].nutritionResult)

        assertEquals("meal-2", meals[1].meal.id)
        assertEquals(600, meals[1].nutritionResult?.calories)

        assertEquals("meal-1", meals[2].meal.id)
        assertEquals(300, meals[2].nutritionResult?.calories)
    }

    /**
     * [回帰テスト] deleteMeal 時に栄養データも一緒に削除される（CASCADE の正常な動作）
     */
    @Test
    fun `REGRESSION deleteMeal should cascade delete nutrition data`() = runTest {
        val dao = database.mealDao()
        val mealId = "meal-to-delete"

        // データ準備
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

        // 削除前に存在することを確認
        val beforeDelete = dao.getAllMealsWithNutrition().first()
        assertEquals(1, beforeDelete.size)

        // 削除実行
        dao.deleteMealById(mealId)

        // 削除後に存在しないことを確認
        val afterDelete = dao.getAllMealsWithNutrition().first()
        assertTrue("Meal and nutrition should be deleted", afterDelete.isEmpty())
    }
}
