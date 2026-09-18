package so.lai.recalo.data.report

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
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

@RunWith(RobolectricTestRunner::class)
class AnalysisDiagnosticsStoreTest {
    private lateinit var context: Context
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AnalysisDiagnosticsStore(context).deleteAll()
    }

    @After
    fun tearDown() {
        AnalysisDiagnosticsStore(context).deleteAll()
    }

    @Test
    fun `saved record is found by meal and diagnostic id`() {
        val store = store()

        val dir = store.save(content(id = "diag-1", mealId = "meal-1", createdAt = now))

        assertNotNull(dir)
        assertEquals(1, store.count())
        assertEquals(1, store.countByMealId("meal-1"))
        assertEquals("diag-1", store.listDiagnosticIds().single())
        assertEquals(dir?.absolutePath, store.findDirByMealId("meal-1")?.absolutePath)
        assertTrue(File(dir, DiagnosticReportFiles.REPORT_FILE).isFile)
        assertTrue(File(dir, DiagnosticReportFiles.VALUES_FILE).isFile)
        assertTrue(File(dir, DiagnosticReportFiles.META_FILE).isFile)
    }

    @Test
    fun `only the newest twenty records are kept`() {
        val store = store()
        repeat(21) { index ->
            store.save(
                content(
                    id = "diag-$index",
                    mealId = "meal-$index",
                    createdAt = now - (21 - index)
                )
            )
        }

        assertEquals(20, store.count())
        assertFalse(store.listDiagnosticIds().contains("diag-0"))
        assertTrue(store.listDiagnosticIds().contains("diag-20"))
    }

    @Test
    fun `records older than seven days are pruned`() {
        val store = store()
        store.save(content(id = "diag-old", mealId = "meal-old", createdAt = now))
        assertEquals(1, store.count())

        now += AnalysisDiagnosticsStore.DEFAULT_RETENTION_MILLIS + 1
        store.prune()

        assertEquals(0, store.count())
        assertNull(store.findDirByMealId("meal-old"))
    }

    @Test
    fun `deleting a meal removes its diagnostic records only`() {
        val store = store()
        store.save(content(id = "diag-a", mealId = "meal-a", createdAt = now))
        store.save(content(id = "diag-b", mealId = "meal-b", createdAt = now))

        store.deleteByMealId("meal-a")

        assertEquals(1, store.count())
        assertNull(store.findDirByMealId("meal-a"))
        assertNotNull(store.findDirByMealId("meal-b"))
    }

    @Test
    fun `report-time values are added to a stored record`() {
        val store = store()
        store.save(content(id = "diag-values", mealId = "meal-values", createdAt = now))
        val snapshot = DiagnosticValueSnapshot(
            stage = DiagnosticValueSnapshot.STAGE_AT_REPORT,
            capturedAt = now + 5,
            mealId = "meal-values",
            analysisStatus = "completed",
            analysisError = null,
            capturedAtMillis = now,
            analysisCompletedAt = now,
            nutritionResultId = "result-1",
            title = "Meal",
            calories = 0,
            confidence = 0.0,
            portionRatio = 1.5,
            items = emptyList(),
            totalNutrients = emptyList(),
            missingReason = null
        )

        val updated = store.updateAtReport("diag-values", snapshot)

        assertTrue(updated)
        val dir = requireNotNull(store.findDirByMealId("meal-values"))
        val values = Gson().fromJson(
            File(dir, DiagnosticReportFiles.VALUES_FILE).readText(),
            JsonObject::class.java
        )
        val atReport = values.getAsJsonObject("atReport")
        assertEquals(0, atReport.get("calories").asInt)
        assertEquals(1.5, atReport.get("portionRatio").asDouble, 0.0)
    }

    @Test
    fun `storage failure does not throw and reports no record`() {
        val store = store()
        val root = store.rootDir()
        root.deleteRecursively()
        assertTrue(root.createNewFile())

        val saved = store.save(content(id = "diag-full", mealId = "meal-full", createdAt = now))

        assertNull(saved)
        assertEquals(0, store.count())
    }

    private fun store() = AnalysisDiagnosticsStore(context, clock = { now })

    private fun content(id: String, mealId: String, createdAt: Long): DiagnosticReportContent =
        DiagnosticReportContent(
            diagnosticId = id,
            mealId = mealId,
            createdAt = createdAt,
            reportJson = JsonObject().apply {
                addProperty("diagnosticId", id)
            }.toString(),
            requestJson = JsonObject().apply {
                addProperty("diagnosticId", id)
            }.toString(),
            responseJson = JsonObject().apply {
                addProperty("diagnosticId", id)
            }.toString(),
            valuesJson = JsonObject().apply {
                addProperty("diagnosticId", id)
                add("atReport", JsonObject().apply { addProperty("available", false) })
            }.toString(),
            metaJson = JsonObject().apply {
                addProperty("diagnosticId", id)
                addProperty("mealId", mealId)
                addProperty("createdAt", createdAt)
            }.toString(),
            imageSource = null,
            legacy = false
        )
}
