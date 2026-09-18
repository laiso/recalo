package so.lai.recalo.data.report

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Stores diagnostic records in a device-local, non-backed-up directory.
 *
 * Only attempts that failed or are reportable are written. Retention is seven
 * days and at most twenty attempts; the oldest records are removed first.
 * Every filesystem failure is swallowed and reported as "not stored" so the
 * diagnostic feature can never break a normal analysis.
 */
class AnalysisDiagnosticsStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retentionMillis: Long = DEFAULT_RETENTION_MILLIS,
    private val maxRecords: Int = DEFAULT_MAX_RECORDS
) {
    private val appContext: Context = context.applicationContext

    fun rootDir(): File = File(appContext.noBackupFilesDir, DIRECTORY_NAME)

    fun environment(): DiagnosticEnvironment = DiagnosticEnvironment.from(appContext)

    /**
     * Writes one diagnostic record. Returns the record directory, or null when
     * the record could not be stored (for example because storage is full).
     */
    fun save(content: DiagnosticReportContent): File? {
        var dir: File? = null
        return try {
            prune()
            dir = File(rootDir(), directoryName(content))
            DiagnosticReportFiles.writeTo(dir, content)
            prune()
            dir
        } catch (e: Exception) {
            Log.w(TAG, "Failed to store diagnostic record: ${e.message}")
            dir?.deleteRecursively()
            null
        }
    }

    /**
     * Adds the report-time database snapshot to an already stored record.
     * Returns false when the record or its values file is unavailable.
     */
    fun updateAtReport(diagnosticId: String, snapshot: DiagnosticValueSnapshot): Boolean {
        return try {
            val dir = findDirByDiagnosticId(diagnosticId) ?: return false
            val valuesFile = File(dir, DiagnosticReportFiles.VALUES_FILE)
            if (!valuesFile.isFile) return false
            val root = gson.fromJson(valuesFile.readText(), JsonObject::class.java)
            root.add("atReport", DiagnosticReportJson.snapshotJson(snapshot))
            root.addProperty("portionRatioAtReport", snapshot.portionRatio)
            File(dir, DiagnosticReportFiles.VALUES_FILE).writeText(
                SecretRedactor.redact(gson.toJson(root)).orEmpty(),
                StandardCharsets.UTF_8
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update diagnostic values: ${e.message}")
            false
        }
    }

    fun findDirByMealId(mealId: String): File? =
        recordDirectories()
            .filter { metaFor(it)?.get("mealId")?.asString == mealId }
            .maxByOrNull { createdAtOf(it) }

    fun findDirByDiagnosticId(diagnosticId: String): File? =
        recordDirectories().firstOrNull { metaFor(it)?.get("diagnosticId")?.asString == diagnosticId }

    fun count(): Int = recordDirectories().size

    fun countByMealId(mealId: String): Int =
        recordDirectories().count { metaFor(it)?.get("mealId")?.asString == mealId }

    fun listDiagnosticIds(): List<String> =
        recordDirectories().mapNotNull { metaFor(it)?.get("diagnosticId")?.asString }

    fun deleteByMealId(mealId: String) {
        recordDirectories().forEach { dir ->
            if (metaFor(dir)?.get("mealId")?.asString == mealId) {
                if (!dir.deleteRecursively()) {
                    Log.w(TAG, "Failed to delete diagnostic record: ${dir.name}")
                }
            }
        }
    }

    fun deleteAll() {
        rootDir().deleteRecursively()
    }

    /** Removes expired and surplus records, oldest first. */
    fun prune() {
        try {
            val now = clock()
            recordDirectories().forEach { dir ->
                val createdAt = createdAtOf(dir)
                val expired = createdAt > 0 && now - createdAt > retentionMillis
                if (expired && !dir.deleteRecursively()) {
                    Log.w(TAG, "Failed to delete expired diagnostic record: ${dir.name}")
                }
            }
            val remaining = recordDirectories().sortedBy { createdAtOf(it) }
            val surplus = remaining.size - maxRecords
            if (surplus > 0) {
                remaining.take(surplus).forEach {
                    if (!it.deleteRecursively()) {
                        Log.w(TAG, "Failed to delete surplus diagnostic record: ${it.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prune diagnostic records: ${e.message}")
        }
    }

    private fun recordDirectories(): List<File> {
        val root = rootDir()
        if (!root.isDirectory) return emptyList()
        return root.listFiles().orEmpty().filter { it.isDirectory }
    }

    private fun metaFor(dir: File): JsonObject? = try {
        val metaFile = File(dir, DiagnosticReportFiles.META_FILE)
        if (metaFile.isFile) {
            gson.fromJson(metaFile.readText(), JsonObject::class.java)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read diagnostic meta: ${e.message}")
        null
    }

    private fun createdAtOf(dir: File): Long =
        metaFor(dir)?.get("createdAt")?.asLong
            ?: dir.name.substringBefore('_').toLongOrNull()
            ?: 0L

    private fun directoryName(content: DiagnosticReportContent): String =
        "${content.createdAt}_${content.diagnosticId}"

    companion object {
        private const val TAG = "DiagnosticsStore"
        const val DIRECTORY_NAME = "analysis_diagnostics"
        const val DEFAULT_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1000L
        const val DEFAULT_MAX_RECORDS = 20

        private val gson = Gson()
    }
}
