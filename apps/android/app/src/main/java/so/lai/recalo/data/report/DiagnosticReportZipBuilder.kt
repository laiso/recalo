package so.lai.recalo.data.report

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packs a diagnostic directory into a ZIP that is handed to the share sheet.
 *
 * The ZIP lives in the app cache (exposed through the existing FileProvider)
 * and is only cleaned up on a later report, never immediately after sharing.
 */
class DiagnosticReportZipBuilder(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val appContext: Context = context.applicationContext

    fun cacheDir(): File = File(appContext.cacheDir, CACHE_DIRECTORY)

    fun zipDirectory(sourceDir: File, diagnosticId: String): Result<File> {
        return try {
            if (!sourceDir.isDirectory) {
                throw IOException("Diagnostic directory is missing")
            }
            val payload = DiagnosticReportFiles.ZIP_ENTRIES
                .map { File(sourceDir, it) }
                .filter { it.isFile }
            if (payload.isEmpty()) {
                throw IOException("No diagnostic files were available to attach")
            }

            cleanupOldArchives()

            val directory = cacheDir()
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Failed to create report cache directory")
            }
            val archive = File(directory, archiveName(diagnosticId))
            if (archive.exists() && !archive.delete()) {
                throw IOException("Failed to replace the previous report archive")
            }

            ZipOutputStream(archive.outputStream().buffered()).use { output ->
                payload.forEach { file ->
                    output.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input -> input.copyTo(output) }
                    output.closeEntry()
                }
                DiagnosticReportFiles.imageFile(sourceDir)?.let { image ->
                    output.putNextEntry(ZipEntry(DiagnosticReportJson.IMAGE_FILE_NAME))
                    image.inputStream().use { input -> input.copyTo(output) }
                    output.closeEntry()
                }
            }
            Result.success(archive)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build diagnostic archive: ${e.message}")
            Result.failure(e)
        }
    }

    /** Removes archives from earlier reports that are no longer needed. */
    fun cleanupOldArchives() {
        try {
            val directory = cacheDir()
            if (!directory.isDirectory) return
            val now = clock()
            directory.listFiles().orEmpty().forEach { file ->
                if (!file.isFile || !file.name.endsWith(".zip")) return@forEach
                if (now - file.lastModified() > ARCHIVE_MAX_AGE_MILLIS && !file.delete()) {
                    Log.w(TAG, "Failed to delete old report archive: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean old report archives: ${e.message}")
        }
    }

    private fun archiveName(diagnosticId: String): String {
        val safeId = diagnosticId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "report" }
        return "Recalo-diagnostic-$safeId.zip"
    }

    companion object {
        private const val TAG = "ReportZipBuilder"
        const val CACHE_DIRECTORY = "analysis_reports"
        const val ARCHIVE_MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
    }
}
