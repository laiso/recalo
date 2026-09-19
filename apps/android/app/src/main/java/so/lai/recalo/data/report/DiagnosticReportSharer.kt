package so.lai.recalo.data.report

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands the diagnostic ZIP to the Android share sheet with the mail fields the
 * user asked for pre-filled. Sending is always an explicit user action; this
 * class never reports a report as sent.
 */
class DiagnosticReportSharer(
    private val uriProvider: (Context, File) -> Uri = { context, file ->
        FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", file)
    }
) {
    fun share(
        context: Context,
        archive: File,
        recipient: String,
        subject: String,
        body: String
    ): Result<Unit> {
        return try {
            val intent = buildShareIntent(context, archive, recipient, subject, body)
            val chooser = Intent.createChooser(intent, subject).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app available to share the diagnostic report")
            Result.failure(ReportShareException(SHARE_UNAVAILABLE_MESSAGE))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open a share target: ${e.message}")
            Result.failure(ReportShareException(SHARE_FAILED_MESSAGE))
        }
    }

    fun buildShareIntent(
        context: Context,
        archive: File,
        recipient: String,
        subject: String,
        body: String
    ): Intent {
        val uri = uriProvider(context, archive)
        return Intent(Intent.ACTION_SEND).apply {
            type = ARCHIVE_MIME_TYPE
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        private const val TAG = "ReportSharer"
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"
        const val ARCHIVE_MIME_TYPE = "application/zip"
        const val RECIPIENT = "support@lai.so"
        const val SUBJECT = "Recalo meal analysis problem report"
        const val SHARE_UNAVAILABLE_MESSAGE =
            "No app is available to share the report."
        const val SHARE_FAILED_MESSAGE =
            "Could not open the sharing app. Please try again later."
    }
}

class ReportShareException(message: String) : Exception(message)
