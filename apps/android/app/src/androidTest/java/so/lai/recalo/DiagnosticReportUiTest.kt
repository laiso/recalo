package so.lai.recalo

import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import so.lai.recalo.data.local.CaroliDatabase
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity
import so.lai.recalo.data.report.DiagnosticReportZipBuilder

/** Runs against a real Android share sheet; no API key or email account needed. */
@RunWith(AndroidJUnit4::class)
class DiagnosticReportUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun failedMealCanShareDiagnosticAttachment() = verifyReportFlow(completed = false)

    @Test
    fun allZeroMealCanShareDiagnosticAttachment() = verifyReportFlow(completed = true)

    private fun verifyReportFlow(completed: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dao = CaroliDatabase.getDatabase(context).mealDao()
        val id = "report-ui-${UUID.randomUUID()}"
        val image = File(context.filesDir, "$id.jpg")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle()
        val archiveDir = DiagnosticReportZipBuilder(context).cacheDir()
        val previousArchives = archiveDir.listFiles().orEmpty().map { it.name }.toSet()
        try {
            runBlocking {
                dao.insertMeal(
                    MealLogEntity(
                        id = id, imageUrl = null, capturedAt = System.currentTimeMillis(),
                        imagePath = image.absolutePath,
                        analysisStatus = if (completed) "completed" else "error",
                        analysisError = if (completed) null else "SERVICE_UNAVAILABLE"
                    )
                )
                if (completed) {
                    dao.insertNutritionResult(
                        NutritionResultEntity(
                            id = "$id-result", mealLogId = id, title = "Report UI test",
                            calories = 0, confidence = 0.0
                        )
                    )
                }
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("analysis_report_button").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithTag("analysis_report_button").onFirst().performClick()
            compose.waitUntil(15_000) {
                containsArchiveName(instrumentation.uiAutomation.rootInActiveWindow)
            }
            val archive = archiveDir.listFiles().orEmpty().single {
                it.extension == "zip" && it.name !in previousArchives
            }
            ZipFile(archive).use { zip ->
                listOf("report.json", "request.json", "response.json", "values.json", "image.jpg")
                    .forEach { assertNotNull(it, zip.getEntry(it)) }
                assertArrayEquals(image.readBytes(), zip.getInputStream(zip.getEntry("image.jpg")).readBytes())
                val values = zip.getInputStream(zip.getEntry("values.json")).bufferedReader().use { it.readText() }
                assertTrue(values.contains(id))
            }
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(context.cacheDir, "report-ui-${if (completed) "zero" else "error"}.png")
                .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        } finally {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            runBlocking { dao.deleteMealById(id) }
            image.delete()
            archiveDir.listFiles().orEmpty().filter {
                it.extension == "zip" && it.name !in previousArchives
            }.forEach { it.delete() }
        }
    }

    private fun containsArchiveName(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.text?.contains("Recalo-diagnostic-") == true) return true
        return (0 until node.childCount).any { containsArchiveName(node.getChild(it)) }
    }
}
