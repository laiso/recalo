package so.lai.recalo.data.report

import android.content.Context
import android.os.Build
import so.lai.recalo.BuildConfig

/** App and device versions attached to every diagnostic report. */
data class DiagnosticEnvironment(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val androidRelease: String,
    val androidSdkInt: Int
) {
    companion object {
        fun from(context: Context): DiagnosticEnvironment = DiagnosticEnvironment(
            packageName = context.packageName,
            versionName = runCatching { BuildConfig.VERSION_NAME }.getOrDefault("unknown"),
            versionCode = runCatching { BuildConfig.VERSION_CODE.toLong() }.getOrDefault(0L),
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            androidSdkInt = Build.VERSION.SDK_INT
        )
    }
}
