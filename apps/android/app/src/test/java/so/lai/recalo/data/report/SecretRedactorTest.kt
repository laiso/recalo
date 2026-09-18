package so.lai.recalo.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {
    private val apiKey = "sk-test-secret-abcdef1234567890"

    @Test
    fun `explicit secret is removed from arbitrary text`() {
        val text = "request failed with key $apiKey while calling the API"

        val redacted = SecretRedactor.redact(text, listOf(apiKey))

        assertFalse(redacted!!.contains(apiKey))
        assertTrue(redacted.contains(SecretRedactor.REDACTED))
    }

    @Test
    fun `openai key shape is removed even when the secret was not provided`() {
        val text = "{\"error\":\"invalid api key sk-abcdefghijklmnop\"}"

        val redacted = SecretRedactor.redact(text)

        assertFalse(redacted!!.contains("sk-abcdefghijklmnop"))
        assertTrue(redacted.contains(SecretRedactor.REDACTED))
    }

    @Test
    fun `authorization bearer header is removed`() {
        val text = "headers: Authorization: Bearer abcDEF123456token"

        val redacted = SecretRedactor.redact(text)!!

        assertFalse(redacted.contains("abcDEF123456token"))
        assertTrue(redacted.contains(SecretRedactor.REDACTED))
    }

    @Test
    fun `json credential fields are removed`() {
        val text = "{\"api_key\":\"$apiKey\",\"model\":\"gpt-5.4\"}"

        val redacted = SecretRedactor.redact(text, listOf(apiKey))!!

        assertFalse(redacted.contains(apiKey))
        assertTrue(redacted.contains("\"model\""))
    }

    @Test
    fun `null input stays null`() {
        assertNull(SecretRedactor.redact(null, listOf(apiKey)))
    }

    @Test
    fun `ordinary text is left unchanged`() {
        val text = "{\"calories\":450,\"title\":\"Grilled Salmon\"}"

        assertEquals(text, SecretRedactor.redact(text, listOf(apiKey)))
    }
}
