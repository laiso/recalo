package so.lai.recalo.data.report

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.model.MealWithNutrition

@RunWith(RobolectricTestRunner::class)
class DiagnosticReportZipBuilderTest {
    private lateinit var context: Context
    private lateinit var zipBuilder: DiagnosticReportZipBuilder
    private lateinit var workDir: File
    private lateinit var imageFile: File
    private val now = 1_700_000_000_000L
    private val apiKey = "sk-test-secret-abcdef1234567890"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        zipBuilder = DiagnosticReportZipBuilder(context, clock = { now })
        zipBuilder.cacheDir().deleteRecursively()
        workDir = File(context.cacheDir, "report-test-work").apply { deleteRecursively() }
        imageFile = File(context.cacheDir, "sent-image.jpg").apply {
            writeBytes(ByteArray(64) { 7 })
        }
    }

    @After
    fun tearDown() {
        zipBuilder.cacheDir().deleteRecursively()
        workDir.deleteRecursively()
        imageFile.delete()
    }

    @Test
    fun `archive contains every required diagnostic file`() {
        val source = writeSessionReport()

        val archive = zipBuilder.zipDirectory(source, "diag-1").getOrThrow()

        assertTrue(archive.isFile)
        assertEquals(DiagnosticReportZipBuilder.CACHE_DIRECTORY, archive.parentFile?.name)
        ZipFile(archive).use { zip ->
            listOf(
                DiagnosticReportFiles.REPORT_FILE,
                DiagnosticReportFiles.REQUEST_FILE,
                DiagnosticReportFiles.RESPONSE_FILE,
                DiagnosticReportFiles.VALUES_FILE,
                DiagnosticReportJson.IMAGE_FILE_NAME
            ).forEach { name ->
                assertNotNull("missing $name", zip.getEntry(name))
            }
            assertTrue(entries(zip).none { it == DiagnosticReportFiles.META_FILE })
        }
    }

    @Test
    fun `report json records identifiers, versions and request details`() {
        val source = writeSessionReport()

        val report = zipBuilder.zipDirectory(source, "diag-1").getOrThrow().readJsonEntry(
            DiagnosticReportFiles.REPORT_FILE
        )

        assertEquals("diag-1", report.get("diagnosticId").asString)
        assertEquals("meal-1", report.get("mealId").asString)
        assertEquals("gpt-5.4", report.getAsJsonObject("analysis").get("requestedModel").asString)
        assertEquals(
            "gpt-5.4-2026-03-05",
            report.getAsJsonObject("analysis").get("actualModel").asString
        )
        assertEquals(500, report.getAsJsonObject("analysis").get("httpStatus").asInt)
        assertEquals("req_primary", report.getAsJsonObject("analysis").get("requestId").asString)
        assertEquals("日本語", report.get("language").asString)
        assertEquals("1.3.0-dev", report.getAsJsonObject("app").get("versionName").asString)
        assertEquals(34, report.getAsJsonObject("device").get("sdkInt").asInt)
        assertEquals(0, report.getAsJsonArray("missingData").size())
    }

    @Test
    fun `non-zero api response and zero stored values are both visible in the archive`() {
        val source = writeSessionReport(
            responseBody = successBody(calories = 450.0),
            afterSaveCalories = 0
        )

        val archive = zipBuilder.zipDirectory(source, "diag-1").getOrThrow()
        val response = archive.readJsonEntry(DiagnosticReportFiles.RESPONSE_FILE)
        val values = archive.readJsonEntry(DiagnosticReportFiles.VALUES_FILE)

        val body = response.getAsJsonObject("primary").getAsJsonObject("body").getAsJsonObject(
            "value"
        )
        assertEquals(450.0, body.get("calories").asDouble, 0.0)
        assertEquals(0, values.getAsJsonObject("afterSave").get("calories").asInt)
    }

    @Test
    fun `api keys present in responses or exceptions never reach the archive`() {
        val source = writeSessionReport(
            responseBody = "{\"error\":\"invalid key $apiKey\"}",
            exceptionMessage = "org.example.Failure: rejected $apiKey"
        )

        val archive = zipBuilder.zipDirectory(source, "diag-1").getOrThrow()

        ZipFile(archive).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                val text = zip.getInputStream(entry).readBytes().toString(Charsets.ISO_8859_1)
                assertFalse("$entry leaked the api key", text.contains(apiKey))
            }
        }
    }

    @Test
    fun `legacy report explains that the original response was not recorded`() {
        val meal = legacyMeal()
        val content = DiagnosticReportJson.legacy(
            diagnosticId = "legacy-1",
            meal = meal,
            appPackageName = "so.lai.recalo.dev",
            appVersionName = "1.3.0-dev",
            appVersionCode = 4,
            androidRelease = "14",
            androidSdkInt = 34,
            language = "English",
            createdAt = now,
            imageSource = null
        )
        val source = DiagnosticReportFiles.writeTo(workDir, content)

        val archive = zipBuilder.zipDirectory(source, "legacy-1").getOrThrow()

        ZipFile(archive).use { zip ->
            assertNotNull(zip.getEntry(DiagnosticReportFiles.REPORT_FILE))
            assertNotNull(zip.getEntry(DiagnosticReportFiles.RESPONSE_FILE))
            assertTrue(zip.getEntry(DiagnosticReportJson.IMAGE_FILE_NAME) == null)
            val report = zip.readJsonEntry(DiagnosticReportFiles.REPORT_FILE)
            assertTrue(report.get("legacy").asBoolean)
            val response = zip.readJsonEntry(DiagnosticReportFiles.RESPONSE_FILE)
            assertTrue(response.get("note").asString.contains("No API response"))
            val values = zip.readJsonEntry(DiagnosticReportFiles.VALUES_FILE)
            assertFalse(values.getAsJsonObject("afterLoad").get("available").asBoolean)
            assertTrue(values.getAsJsonObject("atReport").get("available").asBoolean)
        }
    }

    @Test
    fun `failing to build an archive returns a failure instead of throwing`() {
        val result = zipBuilder.zipDirectory(File(context.cacheDir, "missing-dir"), "diag-x")

        assertTrue(result.isFailure)
    }

    @Test
    fun `old archives are cleaned up on the next report`() {
        val directory = zipBuilder.cacheDir().apply { mkdirs() }
        val oldArchive = File(directory, "old.zip").apply { writeBytes(byteArrayOf(1)) }
        oldArchive.setLastModified(now - DiagnosticReportZipBuilder.ARCHIVE_MAX_AGE_MILLIS - 1)
        val source = writeSessionReport()

        zipBuilder.zipDirectory(source, "diag-1").getOrThrow()

        assertFalse(oldArchive.exists())
    }

    private fun writeSessionReport(
        responseBody: String = "{\"error\":\"boom\"}",
        afterSaveCalories: Int = 0,
        exceptionMessage: String? = null
    ): File {
        val session = AnalysisDiagnosticSession(
            diagnosticId = "diag-1",
            mealId = "meal-1",
            attempt = 2,
            startedAt = now,
            requestedModel = "gpt-5.4",
            language = "日本語",
            appPackageName = "so.lai.recalo.dev",
            appVersionName = "1.3.0-dev",
            appVersionCode = 4,
            androidRelease = "14",
            androidSdkInt = 34,
            secrets = listOf(apiKey)
        )
        session.onImageLoaded(DiagnosticImageInfo("sha256-sent-image", 64, "image/jpeg", null))
        session.onRequestPrepared(
            DiagnosticRequestInfo(
                model = "gpt-5.4",
                systemPrompt = "system prompt",
                userPrompt = "Estimate nutrition for this meal image.",
                schema = mapOf("type" to "json_schema"),
                settings = mapOf("timeoutSeconds" to 60),
                imageSha256 = "sha256-sent-image"
            )
        )
        session.onHttpResponse(
            DiagnosticHttpResponse(
                requestedModel = "gpt-5.4",
                httpStatus = 500,
                requestId = "req_primary",
                actualModel = null,
                body = responseBody,
                receivedAt = now,
                isFallback = false
            )
        )
        session.onContentParsed(
            DiagnosticParsedContent(
                responseId = "resp_primary",
                actualModel = "gpt-5.4-2026-03-05",
                missingFields = listOf("calories"),
                hasContent = true,
                parsedCalories = 450.0,
                parsedConfidence = 0.9
            )
        )
        exceptionMessage?.let { session.onAnalysisException(IllegalStateException(it)) }
        session.valuesAfterLoad = snapshot(
            stage = DiagnosticValueSnapshot.STAGE_AFTER_LOAD,
            calories = null,
            missingReason = "no_nutrition_result_stored"
        )
        session.valuesAfterSave = snapshot(
            stage = DiagnosticValueSnapshot.STAGE_AFTER_SAVE,
            calories = afterSaveCalories,
            missingReason = null
        )
        session.analysisStatus = "error"
        session.errorCode = "SERVICE_UNAVAILABLE"
        session.completedAt = now

        val content = DiagnosticReportJson.fromSession(
            session = session,
            reportableReason = AnalysisReportReason.ANALYSIS_ERROR,
            imageSource = imageFile,
            atReport = snapshot(
                stage = DiagnosticValueSnapshot.STAGE_AT_REPORT,
                calories = afterSaveCalories,
                missingReason = null
            )
        )
        return DiagnosticReportFiles.writeTo(workDir, content)
    }

    private fun snapshot(
        stage: String,
        calories: Int?,
        missingReason: String?
    ) = DiagnosticValueSnapshot(
        stage = stage,
        capturedAt = now,
        mealId = "meal-1",
        analysisStatus = "error",
        analysisError = "SERVICE_UNAVAILABLE",
        capturedAtMillis = now,
        analysisCompletedAt = null,
        nutritionResultId = null,
        title = null,
        calories = calories,
        confidence = null,
        portionRatio = 1.0,
        items = emptyList(),
        totalNutrients = emptyList(),
        missingReason = missingReason
    )

    private fun successBody(calories: Double): String = Gson().toJson(
        mapOf(
            "calories" to calories,
            "title" to "Meal"
        )
    )

    private fun legacyMeal() = MealWithNutrition(
        meal = MealLogEntity(
            id = "meal-legacy",
            imageUrl = null,
            capturedAt = now,
            imagePath = null,
            analysisStatus = MealLogEntity.AnalysisStatus.ERROR,
            analysisError = "SERVICE_UNAVAILABLE"
        ),
        nutritionResultDetails = null
    )

    private fun entries(zip: ZipFile): List<String> =
        zip.entries().asSequence().map(ZipEntry::getName).toList()

    private fun File.readJsonEntry(name: String): JsonObject =
        ZipFile(this).use { zip -> zip.readJsonEntry(name) }

    private fun ZipFile.readJsonEntry(name: String): JsonObject {
        val entry = getEntry(name) ?: error("missing $name")
        return Gson().fromJson(
            getInputStream(entry).readBytes().toString(Charsets.UTF_8),
            JsonObject::class.java
        )
    }
}
