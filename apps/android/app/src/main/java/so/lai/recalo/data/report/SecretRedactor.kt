package so.lai.recalo.data.report

/**
 * Removes credentials from diagnostic text before it is written to a report or
 * a log. Explicit secrets (the OpenAI key currently in use) are always removed,
 * and well-known credential shapes are removed defensively so that a key that
 * leaks into an API response or an exception never reaches the ZIP.
 */
object SecretRedactor {
    const val REDACTED = "[REDACTED]"

    private val BEARER_TOKEN = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{8,}")
    private val OPENAI_KEY = Regex("sk-[A-Za-z0-9_-]{8,}")
    private val JSON_CREDENTIAL_FIELD = Regex(
        "(?i)\"(api[_-]?key|authorization|access[_-]?token|refresh[_-]?token|token|password)\"" +
            "\\s*:\\s*\"[^\"]*\""
    )
    private val HEADER_CREDENTIAL = Regex(
        "(?i)(api[_-]?key|authorization|access[_-]?token|refresh[_-]?token|password)\\s*[:=]\\s*\\S+"
    )

    fun redact(value: String?, secrets: List<String> = emptyList()): String? {
        val nonNullValue: String = value ?: return null
        var output: String = nonNullValue
        for (secret in secrets) {
            val trimmed = secret.trim()
            if (trimmed.length >= 6) {
                output = output.replace(trimmed, REDACTED)
            }
        }
        output = BEARER_TOKEN.replace(output, REDACTED)
        output = OPENAI_KEY.replace(output, REDACTED)
        output = JSON_CREDENTIAL_FIELD.replace(output) { match ->
            val key = match.value.substringBefore(':').trim()
            "$key: \"$REDACTED\""
        }
        output = HEADER_CREDENTIAL.replace(output) { match ->
            val key = match.value.substringBefore(':').substringBefore('=').trim()
            "$key: $REDACTED"
        }
        return output
    }

    fun redact(value: String?, vararg secrets: String): String? = redact(value, secrets.toList())
}
