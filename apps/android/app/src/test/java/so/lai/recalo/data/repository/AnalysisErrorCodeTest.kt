package so.lai.recalo.data.repository

import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Test
import so.lai.recalo.data.openai.ModelAccessDeniedException

class AnalysisErrorCodeTest {
    @Test
    fun `fallback HTTP status determines the user-facing error`() {
        assertFallbackCode(401, AnalysisErrorCode.AUTH_INVALID)
        assertFallbackCode(429, AnalysisErrorCode.RATE_LIMITED)
        assertFallbackCode(503, AnalysisErrorCode.SERVICE_UNAVAILABLE)
        assertFallbackCode(404, AnalysisErrorCode.MODEL_UNAVAILABLE)
    }

    @Test
    fun `missing local image is not classified as a network failure`() {
        assertEquals(
            AnalysisErrorCode.IMAGE_UNAVAILABLE,
            AnalysisErrorCode.fromThrowable(FileNotFoundException("missing image"))
        )
    }

    private fun assertFallbackCode(statusCode: Int, expected: AnalysisErrorCode) {
        val error = ModelAccessDeniedException(
            requestedModel = "requested-model",
            originalErrorCode = 404,
            fallbackErrorCode = statusCode
        )

        assertEquals(expected, AnalysisErrorCode.fromThrowable(error))
    }
}
