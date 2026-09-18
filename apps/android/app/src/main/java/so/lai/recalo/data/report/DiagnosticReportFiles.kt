package so.lai.recalo.data.report

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Writes a [DiagnosticReportContent] to a directory using the file names that
 * also become ZIP entries. Used by the store for retained attempts and by the
 * on-demand legacy report path.
 */
object DiagnosticReportFiles {
    const val REPORT_FILE = "report.json"
    const val REQUEST_FILE = "request.json"
    const val RESPONSE_FILE = "response.json"
    const val VALUES_FILE = "values.json"
    const val META_FILE = "meta.json"

    /** Files that are attached to the report mail, in ZIP order. */
    val ZIP_ENTRIES = listOf(REPORT_FILE, REQUEST_FILE, RESPONSE_FILE, VALUES_FILE)

    fun writeTo(dir: File, content: DiagnosticReportContent): File {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Failed to create diagnostic directory: ${dir.absolutePath}")
        }
        writeText(dir, REPORT_FILE, content.reportJson)
        writeText(dir, REQUEST_FILE, content.requestJson)
        writeText(dir, RESPONSE_FILE, content.responseJson)
        writeText(dir, VALUES_FILE, content.valuesJson)
        writeText(dir, META_FILE, content.metaJson)
        content.imageSource?.let { image ->
            if (image.isFile && image.length() > 0) {
                image.copyTo(File(dir, DiagnosticReportJson.IMAGE_FILE_NAME), overwrite = true)
            }
        }
        return dir
    }

    fun imageFile(dir: File): File? = File(dir, DiagnosticReportJson.IMAGE_FILE_NAME).takeIf { it.isFile }

    private fun writeText(dir: File, name: String, value: String) {
        val safe = SecretRedactor.redact(value).orEmpty()
        File(dir, name).writeText(safe, StandardCharsets.UTF_8)
    }
}
