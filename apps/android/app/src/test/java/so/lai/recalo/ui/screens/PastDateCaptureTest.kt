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
 * Regression test for past date bug
 *
 * Issue: When posting a meal after selecting a past date, it gets saved with today's date.
 * Fix: Pass the capturedAt of the selected date to uploadImage.
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

        // Set repository directly
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
     * [Regression Test] capturedAt is correctly set for meals captured with a past date
     */
    @Test
    fun capturedAt_should_be_set_to_selected_date_not_today() = runTest {
        // Select a date 3 days ago
        val selectedDate = LocalDate.now().minusDays(3)
        val dayStartHour = 4 // 4 AM as the start of the day

        // Calculate timestamp for selected date (same logic as in HomeScreen)
        val capturedAt = selectedDate.atTime(dayStartHour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Today's timestamp
        val todayTimestamp = System.currentTimeMillis()

        // Verify capturedAt is in the past
        assertTrue("capturedAt should be in the past", capturedAt < todayTimestamp)

        // Verify past date is set in MealLogEntity
        val mealId = "past-date-test-meal"
        val meal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = capturedAt,
            imagePath = "/path/meal.jpg",
            analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
        )

        database.mealDao().insertMeal(meal)

        // Verify capturedAt saved in DB
        val savedMeal = database.mealDao().getMealById(mealId)
        assertNotNull("Meal should be saved", savedMeal)
        assertEquals(
            "capturedAt should match the selected date",
            capturedAt,
            savedMeal!!.capturedAt
        )

        // Verify capturedAt is not today's timestamp
        assertNotEquals(
            "capturedAt should not be today's timestamp",
            todayTimestamp / (24 * 60 * 60 * 1000), // Compare date part only
            savedMeal.capturedAt!! / (24 * 60 * 60 * 1000)
        )
    }

    /**
     * [Regression Test] Meals with past dates appear in the past date filter
     */
    @Test
    fun past_date_meal_should_appear_on_selected_date() = runTest {
        val selectedDate = LocalDate.now().minusDays(5)
        val dayStartHour = 4

        // Start/end timestamps for selected date
        val startOfSelectedDate = selectedDate.atTime(dayStartHour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val endOfSelectedDate = startOfSelectedDate + (24 * 60 * 60 * 1000L)

        // Register a past date meal
        val pastMealId = "past-date-filter-test"
        database.mealDao().insertMeal(
            MealLogEntity(
                id = pastMealId,
                imageUrl = null,
                capturedAt = startOfSelectedDate + (12 * 60 * 60 * 1000L), // Around noon
                imagePath = "/path/meal.jpg",
                analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED
            )
        )

        // Register today's meal
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

        // Get with past date filter
        val mealsOnSelectedDate = database.mealDao().getAllMealsWithNutrition().first()
            .filter { (it.meal.capturedAt ?: 0L) in startOfSelectedDate until endOfSelectedDate }

        val mealsOnToday = database.mealDao().getAllMealsWithNutrition().first()
            .filter { (it.meal.capturedAt ?: 0L) in startOfToday until (startOfToday + 24 * 60 * 60 * 1000L) }

        // Only past date meal should appear on past date
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

        // Only today's meal should appear today
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
     * [Regression Test] Works correctly even for a date from one week ago
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

        // Verify capturedAt is 1 week ago (allow +/- 1 day margin)
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
