package so.lai.recalo.data.report

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.test.runTest
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
import org.robolectric.Shadows.shadowOf
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.entity.NutrientEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity
import so.lai.recalo.data.local.model.MealWithNutrition
import so.lai.recalo.data.local.model.NutritionResultWithDetails

@RunWith(RobolectricTestRunner::class)
class AnalysisReportServiceTest {
    private lateinit var context: Context
    private lateinit var store: AnalysisDiagnosticsStore
    private lateinit var zipBuilder: DiagnosticReportZipBuilder
    private lateinit var service: AnalysisReportService
    private lateinit var imageFile: File
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = AnalysisDiagnosticsStore(context, clock = { now })
        store.deleteAll()
        zipBuilder = DiagnosticReportZipBuilder(context, clock = { now })
        zipBuilder.cacheDir().deleteRecursively()
        service = AnalysisReportService(context, store = store, zipBuilder = zipBuilder)
        imageFile = File(context.filesDir, "report_meal.jpg").apply {
            writeBytes(
                ByteArray(32) { 3 }
            )
        }
    }

    @After
    fun tearDown() {
        store.deleteAll()
        zipBuilder.cacheDir().deleteRecursively()
        imageFile.delete()
    }

    @Test
    fun `report after retention expiry uses current values without the old response`() = runTest {
        val saved = requireNotNull(store.save(storedContent("expired", "old-meal")))
        File(saved, DiagnosticReportFiles.RESPONSE_FILE).writeText("{\"oldResponse\":true}")
        now += AnalysisDiagnosticsStore.DEFAULT_RETENTION_MILLIS + 1

        val archive = service.createReportZip(meal("old-meal", imageFile.absolutePath)).getOrThrow()

        ZipFile(archive).use { zip ->
            val report = zip.getInputStream(zip.getEntry(DiagnosticReportFiles.REPORT_FILE))
                .bufferedReader().use { Gson().fromJson(it, JsonObject::class.java) }
            assertTrue(report.get("legacy").asBoolean)
            val response = zip.getInputStream(zip.getEntry(DiagnosticReportFiles.RESPONSE_FILE))
                .bufferedReader().use { it.readText() }
            assertFalse(response.contains("oldResponse"))
        }
        assertFalse(saved.exists())
        assertEquals(0, store.count())
    }

    @Test
    fun `legacy meal without a stored attempt produces a self-contained archive`() = runTest {
        val meal = meal(mealId = "legacy-meal", imagePath = imageFile.absolutePath)

        val archive = service.createReportZip(meal).getOrThrow()

        ZipFile(archive).use { zip ->
            assertNotNull(zip.getEntry(DiagnosticReportJson.IMAGE_FILE_NAME))
            val report = zip.readJsonEntry(DiagnosticReportFiles.REPORT_FILE)
            assertTrue(report.get("legacy").asBoolean)
            val request = zip.readJsonEntry(DiagnosticReportFiles.REQUEST_FILE)
            assertFalse(request.get("available").asBoolean)
            val response = zip.readJsonEntry(DiagnosticReportFiles.RESPONSE_FILE)
            assertTrue(response.get("note").asString.contains("No API response"))
        }
    }

    @Test
    fun `missing image still produces a report and states the image is unavailable`() = runTest {
        val meal = meal(mealId = "no-image-meal", imagePath = null)

        val archive = service.createReportZip(meal).getOrThrow()

        ZipFile(archive).use { zip ->
            assertNull(zip.getEntry(DiagnosticReportJson.IMAGE_FILE_NAME))
            val report = zip.readJsonEntry(DiagnosticReportFiles.REPORT_FILE)
            assertFalse(report.getAsJsonObject("image").get("available").asBoolean)
        }
    }

    @Test
    fun `retained diagnostic record is used and gets the report-time values`() = runTest {
        val mealId = "stored-meal"
        store.save(storedContent(diagnosticId = "diag-stored", mealId = mealId))
        val meal = meal(mealId = mealId, imagePath = imageFile.absolutePath, calories = 0)

        val archive = service.createReportZip(meal).getOrThrow()

        ZipFile(archive).use { zip ->
            val values = zip.readJsonEntry(DiagnosticReportFiles.VALUES_FILE)
            val atReport = values.getAsJsonObject("atReport")
            assertTrue(atReport.get("available").asBoolean)
            assertEquals(0, atReport.get("calories").asInt)
            assertTrue(zip.getEntry(DiagnosticReportFiles.RESPONSE_FILE) != null)
        }
        assertEquals(1, store.countByMealId(mealId))
    }

    @Test
    fun `share intent carries recipient subject body and the archive`() {
        val archive = File(context.cacheDir, "sample-diagnostic.zip").apply {
            writeBytes(
                byteArrayOf(1, 2, 3)
            )
        }
        val sharer = DiagnosticReportSharer(uriProvider = { _, file -> Uri.fromFile(file) })

        val intent = sharer.buildShareIntent(
            context = context,
            archive = archive,
            recipient = DiagnosticReportSharer.RECIPIENT,
            subject = DiagnosticReportSharer.SUBJECT,
            body = "diagnostic body"
        )

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/zip", intent.type)
        assertEquals("support@lai.so", intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.single())
        assertEquals("Recalo meal analysis problem report", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("diagnostic body", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(Uri.fromFile(archive), intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `sharing opens the chooser without claiming the report was sent`() {
        val archive = File(context.cacheDir, "sample-diagnostic.zip").apply {
            writeBytes(
                byteArrayOf(1)
            )
        }
        val sharer = DiagnosticReportSharer(uriProvider = { _, file -> Uri.fromFile(file) })

        val result = sharer.share(
            context = context,
            archive = archive,
            recipient = DiagnosticReportSharer.RECIPIENT,
            subject = DiagnosticReportSharer.SUBJECT,
            body = "body"
        )

        assertTrue(result.isSuccess)
        val started = shadowOf(context as Application).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        val inner = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals("application/zip", inner?.type)
    }

    @Test
    fun `the report archive is exposed through the app file provider`() {
        val archive = File(zipBuilder.cacheDir(), "provider-diagnostic.zip").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9))
        }

        val intent = DiagnosticReportSharer().buildShareIntent(
            context = context,
            archive = archive,
            recipient = DiagnosticReportSharer.RECIPIENT,
            subject = DiagnosticReportSharer.SUBJECT,
            body = "body"
        )

        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull(uri)
        assertEquals("content", uri?.scheme)
        assertEquals("${context.packageName}.fileprovider", uri?.authority)
        assertTrue(uri?.path?.endsWith("provider-diagnostic.zip") == true)
    }

    @Test
    fun `sharing reports an error when no mail app can handle the intent`() {
        val archive = File(context.cacheDir, "sample-diagnostic.zip").apply {
            writeBytes(
                byteArrayOf(1)
            )
        }
        val throwingContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException("no app")
            }
        }

        val result = DiagnosticReportSharer(uriProvider = { _, file -> Uri.fromFile(file) }).share(
            context = throwingContext,
            archive = archive,
            recipient = DiagnosticReportSharer.RECIPIENT,
            subject = DiagnosticReportSharer.SUBJECT,
            body = "body"
        )

        assertTrue(result.isFailure)
        assertEquals(
            DiagnosticReportSharer.SHARE_UNAVAILABLE_MESSAGE,
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun `mail body identifies the diagnostic and the meal`() {
        val body = service.buildMailBody(
            diagnosticId = "diag-body",
            mealId = "meal-body",
            createdAt = now
        )

        assertTrue(body.contains("diag-body"))
        assertTrue(body.contains("meal-body"))
        assertTrue(body.contains("Diagnostic ID"))
    }

    private fun storedContent(diagnosticId: String, mealId: String): DiagnosticReportContent =
        DiagnosticReportContent(
            diagnosticId = diagnosticId,
            mealId = mealId,
            createdAt = now,
            reportJson = JsonObject().apply {
                addProperty("diagnosticId", diagnosticId)
                addProperty("mealId", mealId)
            }.toString(),
            requestJson = "{}",
            responseJson = "{}",
            valuesJson = JsonObject().apply {
                add("atReport", JsonObject().apply { addProperty("available", false) })
            }.toString(),
            metaJson = JsonObject().apply {
                addProperty("diagnosticId", diagnosticId)
                addProperty("mealId", mealId)
                addProperty("createdAt", now)
            }.toString(),
            imageSource = imageFile,
            legacy = false
        )

    private fun meal(
        mealId: String,
        imagePath: String?,
        calories: Int = 0
    ): MealWithNutrition {
        val nutritionResult = NutritionResultEntity(
            id = "result-$mealId",
            mealLogId = mealId,
            title = "Meal",
            calories = calories,
            confidence = 0.4
        )
        return MealWithNutrition(
            meal = MealLogEntity(
                id = mealId,
                imageUrl = null,
                capturedAt = now,
                imagePath = imagePath,
                analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED,
                analysisCompletedAt = now
            ),
            nutritionResultDetails = NutritionResultWithDetails(
                nutritionResult = nutritionResult,
                nutrients = listOf(
                    NutrientEntity(
                        id = "nutrient-$mealId",
                        nutritionResultId = nutritionResult.id,
                        mealItemId = null,
                        name = "Protein",
                        amount = 0.0,
                        unit = "g"
                    )
                ),
                items = emptyList()
            )
        )
    }

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
