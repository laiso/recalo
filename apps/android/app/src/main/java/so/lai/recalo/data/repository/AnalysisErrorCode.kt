package so.lai.recalo.data.repository

import com.google.gson.JsonParseException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import so.lai.recalo.data.openai.ModelAccessDeniedException
import so.lai.recalo.data.openai.OpenAiHttpException

enum class AnalysisErrorCode(
    val userMessage: String,
    val retryable: Boolean
) {
    AUTH_INVALID("OpenAI API key is invalid. Check it in Settings.", false),
    RATE_LIMITED("The analysis limit was reached. Please try again later.", true),
    MODEL_UNAVAILABLE("The selected AI model is unavailable.", true),
    SERVICE_UNAVAILABLE("The AI service is temporarily unavailable.", true),
    TIMEOUT("The analysis took too long. Please try again.", true),
    NETWORK_UNAVAILABLE("Check your internet connection and try again.", true),
    IMAGE_UNAVAILABLE("The saved meal image is no longer available.", false),
    INVALID_RESPONSE("The analysis result could not be read. Please try again.", true),
    UNKNOWN("The meal could not be analyzed. Please try again.", true);

    companion object {
        fun fromStoredValue(value: String?): AnalysisErrorCode =
            entries.firstOrNull { it.name == value } ?: UNKNOWN

        fun fromThrowable(error: Throwable): AnalysisErrorCode = when (error) {
            is ModelAccessDeniedException ->
                error.fallbackErrorCode
                    ?.let(::fromHttpStatus)
                    ?: MODEL_UNAVAILABLE
            is SocketTimeoutException -> TIMEOUT
            is UnknownHostException, is ConnectException -> NETWORK_UNAVAILABLE
            is JsonParseException -> INVALID_RESPONSE
            is FileNotFoundException -> IMAGE_UNAVAILABLE
            is OpenAiHttpException -> fromHttpStatus(error.statusCode)
            is IOException -> NETWORK_UNAVAILABLE
            else -> UNKNOWN
        }

        private fun fromHttpStatus(statusCode: Int): AnalysisErrorCode =
            when (statusCode) {
                401 -> AUTH_INVALID
                403, 404 -> MODEL_UNAVAILABLE
                408 -> TIMEOUT
                429 -> RATE_LIMITED
                in 500..599 -> SERVICE_UNAVAILABLE
                else -> UNKNOWN
            }
    }
}
