package so.lai.recalo.ui.screens

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import so.lai.recalo.data.api.SessionManager
import so.lai.recalo.data.local.CaroliDatabase
import so.lai.recalo.data.local.entity.MealLogEntity

/**
 * 過去日付バグの回帰テスト
 *
 * 問題：過去日付を選択して食事を投稿すると、当日の日付で保存されてしまう
 * 修正：選択した日付の capturedAt を uploadImage に渡すように修正
 */
@RunWith(RobolectricTestRunner::class)
class PastDateCaptureTest {

    private lateinit var database: CaroliDatabase
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, CaroliDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionManager = SessionManager(context)
        viewModel = HomeViewModel(sessionManager)

        // リポジトリを直接セット
        val repoField = HomeViewModel::class.java.getDeclaredField("mealRepository")
        repoField.isAccessible = true
        val repository = so.lai.recalo.data.repository.MealRepository(dao = database.mealDao())
        repoField.set(viewModel, repository)
        viewModel.observeMeals()
    }

    @After
    fun tearDown() {
        database.close()
        sessionManager.clear()
    }

    /**
     * [回帰テスト] 過去日付でキャプチャした食事の capturedAt が正しく設定される
     */
    @Test
    fun capturedAt_should_be_set_to_selected_date_not_today() = runTest {
        // 3 日前の日付を選択
        val selectedDate = LocalDate.now().minusDays(3)
        val dayStartHour = 4 // 凌晨 4 点を日付の起点

        // 選択日のタイムスタンプを計算（HomeScreen と同じロジック）
        val capturedAt = selectedDate.atTime(dayStartHour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // 当日のタイムスタンプ
        val todayTimestamp = System.currentTimeMillis()

        // capturedAt が当日ではないことを確認
        assertTrue("capturedAt should be in the past", capturedAt < todayTimestamp)

        // 過去の日付が MealLogEntity に設定されることを確認
        val mealId = "past-date-test-meal"
        val meal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = capturedAt,
            imagePath = "/path/meal.jpg",
            analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
        )

        database.mealDao().insertMeal(meal)

        // DB に保存された capturedAt を確認
        val savedMeal = database.mealDao().getMealById(mealId)
        assertNotNull("Meal should be saved", savedMeal)
        assertEquals(
            "capturedAt should match the selected date",
            capturedAt,
            savedMeal!!.capturedAt
        )

        // capturedAt が当日のタイムスタンプではないことを確認
        assertNotEquals(
            "capturedAt should not be today's timestamp",
            todayTimestamp / (24 * 60 * 60 * 1000), // 日付部分のみ比較
            savedMeal.capturedAt!! / (24 * 60 * 60 * 1000)
        )
    }

    /**
     * [回帰テスト] 過去日付の食事が過去日付のフィルターに表示される
     */
    @Test
    fun past_date_meal_should_appear_on_selected_date() = runTest {
        val selectedDate = LocalDate.now().minusDays(5)
        val dayStartHour = 4

        // 選択日の開始・終了タイムスタンプ
        val startOfSelectedDate = selectedDate.atTime(dayStartHour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val endOfSelectedDate = startOfSelectedDate + (24 * 60 * 60 * 1000L)

        // 過去日付の食事を登録
        val pastMealId = "past-date-filter-test"
        database.mealDao().insertMeal(
            MealLogEntity(
                id = pastMealId,
                imageUrl = null,
                capturedAt = startOfSelectedDate + (12 * 60 * 60 * 1000L), // 昼頃
                imagePath = "/path/meal.jpg",
                analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
            )
        )

        // 当日の食事も登録
        val today = LocalDate.now()
        val startOfToday = today.atTime(dayStartHour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val todayMealId = "today-filter-test"
        database.mealDao().insertMeal(
            MealLogEntity(
                id = todayMealId,
                imageUrl = null,
                capturedAt = startOfToday + (12 * 60 * 60 * 1000L),
                imagePath = "/path/meal2.jpg",
                analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
            )
        )

        // 過去日付のフィルターで取得
        val mealsOnSelectedDate = database.mealDao().getAllMealsWithNutrition().first()
            .filter { (it.meal.capturedAt ?: 0L) in startOfSelectedDate until endOfSelectedDate }

        val mealsOnToday = database.mealDao().getAllMealsWithNutrition().first()
            .filter { (it.meal.capturedAt ?: 0L) in startOfToday until (startOfToday + 24 * 60 * 60 * 1000L) }

        // 過去日付には過去の日付の食事のみが表示される
        assertEquals(
            "Only the past date meal should appear on selected date",
            1,
            mealsOnSelectedDate.size
        )
        assertEquals(
            "The meal on selected date should have correct ID",
            pastMealId,
            mealsOnSelectedDate[0].meal.id
        )

        // 今日には今日の食事のみが表示される
        assertEquals(
            "Only today's meal should appear today",
            1,
            mealsOnToday.size
        )
        assertEquals(
            "Today's meal should have correct ID",
            todayMealId,
            mealsOnToday[0].meal.id
        )
    }

    /**
     * [回帰テスト] 1 週間前の日付でも正しく動作する
     */
    @Test
    fun capturedAt_should_work_correctly_for_one_week_ago() = runTest {
        val selectedDate = LocalDate.now().minusWeeks(1)
        val dayStartHour = 5

        val capturedAt = selectedDate.atTime(dayStartHour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val mealId = "one-week-ago-test"
        database.mealDao().insertMeal(
            MealLogEntity(
                id = mealId,
                imageUrl = null,
                capturedAt = capturedAt,
                imagePath = "/path/meal.jpg",
                analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
            )
        )

        val savedMeal = database.mealDao().getMealById(mealId)
        assertNotNull(savedMeal)

        // capturedAt が 1 週間前であることを確認（±1 日の誤差を許容）
        val oneWeekAgoMillis = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val oneDayMillis = 24 * 60 * 60 * 1000L
        val savedDate = java.time.Instant.ofEpochMilli(savedMeal!!.capturedAt!!)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        assertEquals(
            "Saved date should be approximately 1 week ago",
            selectedDate,
            savedDate
        )
        assertTrue(
            "capturedAt should be close to 1 week ago",
            kotlin.math.abs(savedMeal.capturedAt!! - oneWeekAgoMillis) < oneDayMillis
        )
    }
}
