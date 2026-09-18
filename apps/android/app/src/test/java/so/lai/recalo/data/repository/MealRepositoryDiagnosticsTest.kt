package so.lai.recalo.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import so.lai.recalo.data.local.CaroliDatabase
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.openai.NutritionAnalyzer
import so.lai.recalo.data.openai.NutritionAnalyzerFactory
import so.lai.recalo.data.openai.NutritionResultData
import so.lai.recalo.data.openai.OpenAiService
import so.lai.recalo.data.report.AnalysisDiagnosticsRecorder
import so.lai.recalo.data.report.AnalysisDiagnosticsStore
import so.lai.recalo.data.report.AnalysisReportReason
import so.lai.recalo.data.report.AnalysisReportability
import so.lai.recalo.data.report.DiagnosticImageInfo
import so.lai.recalo.data.report.DiagnosticReportFiles
import so.lai.recalo.data.report.DiagnosticReportZipBuilder
import so.lai.recalo.data.report.analysisReportability

@RunWith(RobolectricTestRunner::class)
class MealRepositoryDiagnosticsTest {
    private lateinit var context: Context
    private lateinit var database: CaroliDatabase
    private lateinit var server: MockWebServer
    private lateinit var sourceImage: File
    private lateinit var store: AnalysisDiagnosticsStore
    private lateinit var repository: MealRepository
    private val apiKey = "sk-test-secret-abcdef1234567890"

    @Before
    fun setUp() {
        ShadowLog.clear()
        context = ApplicationProvider.getApplicationContext()
        database = CaroliDatabase.createInMemoryDatabase(context)
        server = MockWebServer().also { it.start() }
        sourceImage = File(context.cacheDir, "diagnostics_meal.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            FileOutputStream(sourceImage).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            bitmap.recycle()
        }
        store = AnalysisDiagnosticsStore(context)
        store.deleteAll()
        repository = repositoryWith(
            NutritionAnalyzerFactory { key ->
                OpenAiService(apiKey = key, baseUrl = server.url("/v1/responses").toString())
            }
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
        sourceImage.delete()
        store.deleteAll()
        File(context.filesDir, "images").deleteRecursively()
    }

    @Test
    fun `http error produces a reportable record with the http status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = repository.uploadAndAnalyzeMeal(
            context = context,
            imageUri = Uri.fromFile(sourceImage),
            openAiApiKey = apiKey,
            modelName = "test-model"
        )

        assertTrue(result.isFailure)
        val mealId = latestMealId()
        val meal = requireNotNull(repository.getMealWithNutritionById(mealId))
        assertEquals(MealLogEntity.AnalysisStatus.ERROR, meal.meal.analysisStatus)
        val reportability = meal.analysisReportability()
        assertTrue(reportability.isReportable)
        assertEquals(
            AnalysisReportReason.ANALYSIS_ERROR,
            (reportability as AnalysisReportability.Reportable).reason
        )
        assertEquals(1, store.countByMealId(mealId))

        val report = archiveReport(mealId)
        assertEquals(500, report.getAsJsonObject("analysis").get("httpStatus").asInt)
        assertEquals(
            "test-model",
            report.getAsJsonObject("analysis").get("requestedModel").asString
        )
    }

    @Test
    fun `explicit all zero completion produces a reportable record`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(nutritionBody(allZeroNutrition)))

        val result = repository.uploadAndAnalyzeMeal(
            context = context,
            imageUri = Uri.fromFile(sourceImage),
            openAiApiKey = apiKey,
            modelName = "test-model"
        )

        assertTrue(result.isSuccess)
        val mealId = result.getOrThrow().id
        val meal = requireNotNull(repository.getMealWithNutritionById(mealId))
        assertEquals(MealLogEntity.AnalysisStatus.COMPLETED, meal.meal.analysisStatus)
        val reportability = meal.analysisReportability()
        assertEquals(
            AnalysisReportReason.ALL_ZERO_VALUES,
            (reportability as AnalysisReportability.Reportable).reason
        )
        assertEquals(1, store.countByMealId(mealId))
    }

    @Test
    fun `missing numeric fields are reportable and recorded`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                nutritionBody(
                    """
                    {"title":"Meal","nutrients":[{"name":"Protein","unit":"g"}],
                     "items":[{"name":"Rice","quantity":"1 bowl",
                               "nutrients":[{"name":"Protein","unit":"g"}]}]}
                    """.trimIndent()
                )
            )
        )

        val result = repository.uploadAndAnalyzeMeal(
            context = context,
            imageUri = Uri.fromFile(sourceImage),
            openAiApiKey = apiKey,
            modelName = "test-model"
        )

        val mealId = result.getOrThrow().id
        val meal = requireNotNull(repository.getMealWithNutritionById(mealId))
        assertTrue(meal.analysisReportability().isReportable)
        assertEquals(1, store.countByMealId(mealId))

        val report = archiveReport(mealId)
        val missingFields = report.getAsJsonObject("analysis").getAsJsonArray("missingValueFields")
        assertTrue(missingFields.any { it.asString == "calories" })
    }

    @Test
    fun `normal result shows no report button and stores no diagnostic`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(nutritionBody(successNutrition)))

        val result = repository.uploadAndAnalyzeMeal(
            context = context,
            imageUri = Uri.fromFile(sourceImage),
            openAiApiKey = apiKey,
            modelName = "test-model"
        )

        val mealId = result.getOrThrow().id
        val meal = requireNotNull(repository.getMealWithNutritionById(mealId))
        assertFalse(meal.analysisReportability().isReportable)
        assertEquals(0, store.countByMealId(mealId))
    }

    @Test
    fun `partially zero nutrients are not reportable`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(nutritionBody(partialZeroNutrition))
        )

        val result = repository.uploadAndAnalyzeMeal(
            context = context,
            imageUri = Uri.fromFile(sourceImage),
            openAiApiKey = apiKey,
            modelName = "test-model"
        )

        val mealId = result.getOrThrow().id
        val meal = requireNotNull(repository.getMealWithNutritionById(mealId))
        assertFalse(meal.analysisReportability().isReportable)
        assertEquals(0, store.countByMealId(mealId))
    }

    @Test
    fun `retry keeps the previous failure as a separate attempt`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        val failed = repository.uploadAndAnalyzeMeal(
            context = context,
            imageUri = Uri.fromFile(sourceImage),
            openAiApiKey = apiKey,
            modelName = "test-model"
        )
        assertTrue(failed.isFailure)
        val mealId = latestMealId()
        assertEquals(1, store.countByMealId(mealId))

        server.enqueue(MockResponse().setResponseCode(200).setBody(nutritionBody(allZeroNutrition)))
        val retried = repository.retryAnalysis(mealId, apiKey, "test-model")
        assertTrue(retried.isSuccess)

        assertEquals(2, store.countByMealId(mealId))
        val ids = store.listDiagnosticIds()
        assertEquals(2, ids.size)
        assertNotEquals(ids[0], ids[1])
        val metas = store.rootDir().listFiles().orEmpty().map { dir ->
            Gson().fromJson(
                File(dir, DiagnosticReportFiles.META_FILE).readText(),
                JsonObject::class.java
            )
        }
        assertEquals(setOf(1, 2), metas.map { it.get("attempt").asInt }.toSet())
    }

    @Test
    fun `deleting a meal deletes its diagnostic records`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        repository.uploadAndAnalyzeMeal(
            context = context,
            imageUri = Uri.fromFile(sourceImage),
            openAiApiKey = apiKey,
            modelName = "test-model"
        )
        val mealId = latestMealId()
        assertEquals(1, store.countByMealId(mealId))

        repository.deleteMeal(mealId)

        assertEquals(0, store.countByMealId(mealId))
        assertEquals(0, store.count())
    }

    @Test
    fun `api key in the response or the logs never reaches the archive`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(500).setBody("{\"error\":\"invalid api key $apiKey\"}")
        )

        repository.uploadAndAnalyzeMeal(
            context = context,
            imageUri = Uri.fromFile(sourceImage),
            openAiApiKey = apiKey,
            modelName = "test-model"
        )
        val mealId = latestMealId()

        val archive = zipBuilder().zipDirectory(
            requireNotNull(store.findDirByMealId(mealId)),
            "diag-archive"
        ).getOrThrow()
        ZipFile(archive).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                val text = zip.getInputStream(entry).readBytes().toString(Charsets.ISO_8859_1)
                assertFalse("$entry leaked the api key", text.contains(apiKey))
            }
        }

        val leakedLogs = ShadowLog.getLogs().filter { log ->
            log.msg?.contains(apiKey) == true
        }
        assertTrue("logs leaked the api key: $leakedLogs", leakedLogs.isEmpty())
    }

    @Test
    fun `analysis failure without any http response still produces a record`() = runTest {
        val failingAnalyzer = object : NutritionAnalyzer {
            override suspend fun analyzeNutrition(
                imagePath: String,
                modelName: String,
                language: String,
                diagnostics: AnalysisDiagnosticsRecorder?
            ): Result<NutritionResultData> {
                diagnostics?.onImageLoaded(DiagnosticImageInfo("hash", 1L, "image/jpeg", null))
                val error = SocketTimeoutException("timed out")
                diagnostics?.onAnalysisException(error)
                return Result.failure(error)
            }
        }
        repository = repositoryWith(NutritionAnalyzerFactory { failingAnalyzer })

        val result = repository.uploadAndAnalyzeMeal(
            context = context,
            imageUri = Uri.fromFile(sourceImage),
            openAiApiKey = apiKey,
            modelName = "test-model"
        )

        assertTrue(result.isFailure)
        val mealId = latestMealId()
        assertEquals(1, store.countByMealId(mealId))
        val response = zipBuilder().zipDirectory(
            requireNotNull(store.findDirByMealId(mealId)),
            "diag-no-http"
        ).getOrThrow().readJsonEntry(DiagnosticReportFiles.RESPONSE_FILE)
        assertFalse(response.getAsJsonObject("primary").get("available").asBoolean)
        assertTrue(response.getAsJsonArray("missing").any { it.asString == "no_http_response" })
        assertEquals(0, server.requestCount)
    }

    private fun repositoryWith(factory: NutritionAnalyzerFactory) = MealRepository(
        dao = database.mealDao(),
        database = database,
        analyzerFactory = factory,
        diagnosticStore = store
    )

    private suspend fun latestMealId(): String =
        requireNotNull(repository.getAllMealsWithNutrition().first().firstOrNull()).meal.id

    private fun zipBuilder() = DiagnosticReportZipBuilder(context)

    private suspend fun archiveReport(mealId: String): JsonObject {
        val dir = requireNotNull(store.findDirByMealId(mealId))
        val archive = zipBuilder().zipDirectory(dir, "diag-report").getOrThrow()
        return archive.readJsonEntry(DiagnosticReportFiles.REPORT_FILE)
    }

    private fun File.readJsonEntry(name: String): JsonObject =
        ZipFile(this).use { zip ->
            val entry = zip.getEntry(name) ?: error("missing $name")
            Gson().fromJson(
                zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8),
                JsonObject::class.java
            )
        }

    private fun nutritionBody(content: String): String = Gson().toJson(
        mapOf(
            "id" to "resp_test",
            "model" to "test-model-2026-01-01",
            "output" to listOf(
                mapOf(
                    "type" to "message",
                    "content" to listOf(mapOf("type" to "output_text", "text" to content))
                )
            )
        )
    )

    private val allZeroNutrition = """
        {"title":"Meal","calories":0,"confidence":0,
         "nutrients":[{"name":"Protein","amount":0,"unit":"g"},
                      {"name":"Fat","amount":0,"unit":"g"}],
         "items":[{"name":"Rice","quantity":"1 bowl","calories":0,
                   "nutrients":[{"name":"Protein","amount":0,"unit":"g"}]}]}
    """.trimIndent()

    private val successNutrition = """
        {"title":"Grilled Salmon","calories":450,"confidence":0.9,
         "nutrients":[{"name":"Protein","amount":30,"unit":"g"}],
         "items":[{"name":"Salmon","quantity":"1 fillet","calories":450,
                   "nutrients":[{"name":"Protein","amount":30,"unit":"g"}]}]}
    """.trimIndent()

    private val partialZeroNutrition = """
        {"title":"Salad","calories":320,"confidence":0.8,
         "nutrients":[{"name":"Protein","amount":0,"unit":"g"},
                      {"name":"Fat","amount":18,"unit":"g"}],
         "items":[]}
    """.trimIndent()
}
