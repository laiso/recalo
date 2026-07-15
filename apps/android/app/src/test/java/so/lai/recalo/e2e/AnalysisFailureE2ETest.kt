package so.lai.recalo.e2e

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import so.lai.recalo.data.local.CaroliDatabase
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.openai.NutritionAnalyzerFactory
import so.lai.recalo.data.openai.OpenAiService
import so.lai.recalo.data.repository.AnalysisErrorCode
import so.lai.recalo.data.repository.MealRepository
import so.lai.recalo.ui.screens.analysisErrorPresentation

@RunWith(RobolectricTestRunner::class)
class AnalysisFailureE2ETest {
    private lateinit var context: Context
    private lateinit var database: CaroliDatabase
    private lateinit var server: MockWebServer
    private lateinit var sourceImage: File
    private lateinit var repository: MealRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = CaroliDatabase.createInMemoryDatabase(context)
        server = MockWebServer().also { it.start() }
        sourceImage = File(context.cacheDir, "e2e_meal.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            FileOutputStream(sourceImage).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            bitmap.recycle()
        }
        val baseUrl = server.url("/v1/responses").toString()
        repository = MealRepository(
            dao = database.mealDao(),
            database = database,
            analyzerFactory = NutritionAnalyzerFactory { apiKey ->
                OpenAiService(apiKey = apiKey, baseUrl = baseUrl)
            }
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
        sourceImage.delete()
        File(context.filesDir, "images").deleteRecursively()
    }

    @Test
    fun `failed analysis is explained and retry succeeds without duplicate meal`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val failedResult = repository.uploadAndAnalyzeMeal(
            context = context,
            imageUri = Uri.fromFile(sourceImage),
            openAiApiKey = "fake-key",
            modelName = "test-model"
        )

        assertTrue(failedResult.isFailure)
        val mealsAfterFailure = repository.getAllMealsWithNutrition().first()
        assertEquals(1, mealsAfterFailure.size)
        val failedMeal = mealsAfterFailure.single()
        assertEquals(MealLogEntity.AnalysisStatus.ERROR, failedMeal.meal.analysisStatus)
        assertEquals(AnalysisErrorCode.SERVICE_UNAVAILABLE.name, failedMeal.meal.analysisError)
        assertNull(failedMeal.nutritionResult)

        val errorPresentation = failedMeal.analysisErrorPresentation()
        assertNotNull(errorPresentation)
        assertEquals("Analysis failed", errorPresentation?.title)
        assertEquals(
            AnalysisErrorCode.SERVICE_UNAVAILABLE.userMessage,
            errorPresentation?.message
        )
        assertTrue(errorPresentation?.retryable == true)
        assertFalse(errorPresentation?.message?.contains("500") == true)

        server.enqueue(MockResponse().setResponseCode(200).setBody(successResponse))

        val retryResult = repository.retryAnalysis(
            mealId = failedMeal.meal.id,
            openAiApiKey = "fake-key",
            modelName = "test-model"
        )

        assertTrue(retryResult.isSuccess)
        val mealsAfterRetry = repository.getAllMealsWithNutrition().first()
        assertEquals(1, mealsAfterRetry.size)
        val completedMeal = mealsAfterRetry.single()
        assertEquals(failedMeal.meal.id, completedMeal.meal.id)
        assertEquals(MealLogEntity.AnalysisStatus.COMPLETED, completedMeal.meal.analysisStatus)
        assertNull(completedMeal.meal.analysisError)
        assertEquals(450, completedMeal.nutritionResult?.calories)
        assertNull(completedMeal.analysisErrorPresentation())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `retry with a missing saved image becomes non-retryable without an HTTP request`() = runTest {
        val missingImageMeal = MealLogEntity(
            id = "missing-image-meal",
            imageUrl = null,
            capturedAt = 1000L,
            imagePath = File(context.filesDir, "missing.jpg").absolutePath,
            analysisStatus = MealLogEntity.AnalysisStatus.ERROR,
            analysisError = AnalysisErrorCode.SERVICE_UNAVAILABLE.name
        )
        database.mealDao().insertMeal(missingImageMeal)

        val result = repository.retryAnalysis(
            mealId = missingImageMeal.id,
            openAiApiKey = "fake-key",
            modelName = "test-model"
        )

        assertTrue(result.isFailure)
        val storedMeal = database.mealDao().getMealById(missingImageMeal.id)
        assertEquals(AnalysisErrorCode.IMAGE_UNAVAILABLE.name, storedMeal?.analysisError)
        val storedMealWithNutrition = database.mealDao()
            .getMealWithNutritionById(missingImageMeal.id)
        assertFalse(storedMealWithNutrition?.analysisErrorPresentation()?.retryable == true)
        assertEquals(0, server.requestCount)
    }

    private val successResponse = """
        {
          "id": "resp_e2e",
          "output": [
            {
              "type": "message",
              "content": [
                {
                  "type": "output_text",
                  "text": "{\"title\":\"Grilled Salmon\",\"calories\":450,\"confidence\":0.9,\"nutrients\":[{\"name\":\"Protein\",\"amount\":30,\"unit\":\"g\"}],\"items\":[{\"name\":\"Salmon\",\"quantity\":\"1 fillet\",\"calories\":450,\"nutrients\":[{\"name\":\"Protein\",\"amount\":30,\"unit\":\"g\"}]}]}"
                }
              ]
            }
          ]
        }
    """.trimIndent()
}
