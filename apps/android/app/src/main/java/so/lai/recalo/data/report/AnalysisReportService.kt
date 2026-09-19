package so.lai.recalo.data.report

import android.content.Context
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import so.lai.recalo.data.local.model.MealWithNutrition

/**
 * Turns a reportable meal into a diagnostic ZIP and opens the share sheet.
 *
 * A retained diagnostic record is used when one exists; meals analysed before
 * diagnostics existed are reported from the currently stored image and database
 * values only, with a clear note that the original API response is unavailable.
 */
class AnalysisReportService(
    context: Context,
    private val store: AnalysisDiagnosticsStore = AnalysisDiagnosticsStore(context),
    private val zipBuilder: DiagnosticReportZipBuilder = DiagnosticReportZipBuilder(context),
    private val sharer: DiagnosticReportSharer = DiagnosticReportSharer(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val appContext: Context = context.applicationContext
    private val environment: DiagnosticEnvironment = DiagnosticEnvironment.from(appContext)

    suspend fun createReportZip(meal: MealWithNutrition): Result<File> = withContext(Dispatchers.IO) {
        try {
            val source = prepareSourceDirectory(meal)
            zipBuilder.zipDirectory(source.dir, source.diagnosticId)
        } catch (e: Exception) {
            Result.failure(ReportShareException(BUILD_FAILED_MESSAGE))
        }
    }

    suspend fun prepareAndShare(meal: MealWithNutrition): Result<Unit> {
        val source = withContext(Dispatchers.IO) {
            runCatching { prepareSourceDirectory(meal) }.getOrNull()
        } ?: return Result.failure(ReportShareException(BUILD_FAILED_MESSAGE))

        val zipResult = withContext(Dispatchers.IO) {
            zipBuilder.zipDirectory(source.dir, source.diagnosticId)
        }
        val archive = zipResult.getOrElse { return Result.failure(it) }
        // The share sheet must be opened from the caller's (main) thread.
        return sharer.share(
            context = appContext,
            archive = archive,
            recipient = DiagnosticReportSharer.RECIPIENT,
            subject = DiagnosticReportSharer.SUBJECT,
            body = buildMailBody(
                diagnosticId = source.diagnosticId,
                mealId = meal.meal.id,
                createdAt = meal.meal.analysisCompletedAt ?: meal.meal.createdAt
            )
        )
    }

    private fun prepareSourceDirectory(meal: MealWithNutrition): ReportSource {
        val storedDir = store.findDirByMealId(meal.meal.id)
        if (storedDir != null) {
            val diagnosticId = storedDir.name.substringAfter('_', storedDir.name)
            val snapshot = DiagnosticValueSnapshot.from(
                stage = DiagnosticValueSnapshot.STAGE_AT_REPORT,
                capturedAt = clock(),
                meal = meal
            )
            store.updateAtReport(diagnosticId, snapshot)
            return ReportSource(storedDir, diagnosticId)
        }
        return writeLegacyReport(meal)
    }

    private fun writeLegacyReport(meal: MealWithNutrition): ReportSource {
        cleanupWorkDirectories()
        val createdAt = clock()
        val diagnosticId = UUID.randomUUID().toString()
        val workDir = File(
            File(zipBuilder.cacheDir(), WORK_DIRECTORY),
            "${createdAt}_$diagnosticId"
        )
        val content = DiagnosticReportJson.legacy(
            diagnosticId = diagnosticId,
            meal = meal,
            appPackageName = environment.packageName,
            appVersionName = environment.versionName,
            appVersionCode = environment.versionCode,
            androidRelease = environment.androidRelease,
            androidSdkInt = environment.androidSdkInt,
            language = Locale.getDefault().displayLanguage,
            createdAt = createdAt,
            imageSource = meal.meal.imagePath
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isFile }
        )
        return ReportSource(DiagnosticReportFiles.writeTo(workDir, content), diagnosticId)
    }

    private fun cleanupWorkDirectories() {
        val workRoot = File(zipBuilder.cacheDir(), WORK_DIRECTORY)
        if (!workRoot.isDirectory) return
        val now = clock()
        workRoot.listFiles().orEmpty().forEach { file ->
            if (now - file.lastModified() > WORK_DIRECTORY_MAX_AGE_MILLIS) {
                file.deleteRecursively()
            }
        }
    }

    fun buildMailBody(diagnosticId: String, mealId: String, createdAt: Long): String = """
        A problem occurred with meal analysis in Recalo. The following diagnostic data is attached for investigation.

        Diagnostic ID: $diagnosticId
        Meal ID: $mealId
        Analysis time: ${isoLocal(createdAt)}

        The attached ZIP contains the photo used for analysis, the request, API responses, and stored values.
        Please review the contents before sending.
    """.trimIndent()

    private fun isoLocal(timestamp: Long): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        format.timeZone = java.util.TimeZone.getDefault()
        return format.format(java.util.Date(timestamp))
    }

    companion object {
        const val WORK_DIRECTORY = "work"
        const val WORK_DIRECTORY_MAX_AGE_MILLIS = 60L * 60L * 1000L
        const val BUILD_FAILED_MESSAGE =
            "Could not prepare the diagnostic report. Please try again later."
    }
}

private data class ReportSource(
    val dir: File,
    val diagnosticId: String
)
