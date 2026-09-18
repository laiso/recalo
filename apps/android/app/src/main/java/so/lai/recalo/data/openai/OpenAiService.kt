package so.lai.recalo.data.openai

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import so.lai.recalo.data.api.AiConfig
import so.lai.recalo.data.report.AnalysisDiagnosticsRecorder
import so.lai.recalo.data.report.DiagnosticHttpResponse
import so.lai.recalo.data.report.DiagnosticImageInfo
import so.lai.recalo.data.report.DiagnosticParsedContent
import so.lai.recalo.data.report.DiagnosticRequestInfo
import so.lai.recalo.data.report.SecretRedactor

/**
 * Nutrition analysis service using OpenAI Responses API
 * https://platform.openai.com/docs/api-reference/responses
 */
class OpenAiService(
    private val apiKey: String,
    private val timeoutSeconds: Long = 60,
    private val baseUrl: String = OPENAI_API_URL
) : NutritionAnalyzer {
    private val trimmedApiKey = apiKey.trim()
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "OpenAiService"
        private const val OPENAI_API_URL = "https://api.openai.com/v1/responses"
        private val REQUIRED_TOP_LEVEL_FIELDS = listOf(
            "title",
            "calories",
            "confidence",
            "nutrients",
            "items"
        )
    }

    private fun getSystemPrompt(language: String) = """
        You are a nutrition analyst.
        Analyze the meal image and provide:
        1. A title for the meal in $language (e.g., "Grilled Salmon Set", "Fried Rice" etc.)
        2. A list of individual food items in $language (name, quantity, calories, and nutrients for each).
        3. Total calories and total nutrients for the entire meal.
        
        CRITICAL: For nutrient names, use EXACTLY these English strings: "Protein", "Fat", "Carbohydrates", "Fiber".
        DO NOT use "Total Protein" or "Total Fat".

        Return realistic values in grams or milligrams using the schema provided.
        Use confidence between 0 and 1.
    """.trimIndent()

    private val userPrompt = "Estimate nutrition for this meal image."

    override suspend fun analyzeNutrition(
        imagePath: String,
        modelName: String,
        language: String,
        diagnostics: AnalysisDiagnosticsRecorder?
    ): Result<NutritionResultData> = withContext(Dispatchers.IO) {
        val recorder = diagnostics ?: AnalysisDiagnosticsRecorder.NoOp
        try {
            val imageBytes = readImageBytes(imagePath, recorder)
            val imageSha256 = sha256(imageBytes)
            recorder.onImageLoaded(
                DiagnosticImageInfo(
                    sha256 = imageSha256,
                    byteCount = imageBytes.size.toLong(),
                    mimeType = "image/jpeg",
                    missingReason = null
                )
            )

            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            recorder.onRequestPrepared(
                DiagnosticRequestInfo(
                    model = modelName,
                    systemPrompt = getSystemPrompt(language),
                    userPrompt = userPrompt,
                    schema = TextConfig(),
                    settings = mapOf(
                        "endpoint" to "responses",
                        "baseUrl" to baseUrl,
                        "timeoutSeconds" to timeoutSeconds
                    ),
                    imageSha256 = imageSha256
                )
            )

            Log.d(
                TAG,
                "Sending analysis request diagnostic=${recorder.analysisDiagnosticId} model=$modelName"
            )

            val request = createRequest(base64Image, modelName, language)
            val response = client.newCall(request).execute()
            response.use { primaryResponse ->
                val primaryBody = primaryResponse.body?.string()
                recorder.onHttpResponse(
                    primaryResponse.toDiagnostic(
                        requestedModel = modelName,
                        body = primaryBody,
                        isFallback = false
                    )
                )

                if (!primaryResponse.isSuccessful) {
                    if (primaryResponse.code == 403 || primaryResponse.code == 404) {
                        Log.w(
                            TAG,
                            "Model $modelName not accessible; falling back diagnostic=${recorder.analysisDiagnosticId}"
                        )
                        return@withContext analyzeWithFallback(
                            base64Image = base64Image,
                            modelName = modelName,
                            language = language,
                            primaryStatus = primaryResponse.code,
                            recorder = recorder
                        )
                    }
                    Log.e(
                        TAG,
                        "Analysis failed diagnostic=${recorder.analysisDiagnosticId} status=${primaryResponse.code}"
                    )
                    return@withContext Result.failure(OpenAiHttpException(primaryResponse.code))
                }

                if (primaryBody == null) {
                    Log.e(TAG, "Empty response diagnostic=${recorder.analysisDiagnosticId}")
                    return@withContext Result.failure(Exception("Empty response"))
                }

                return@withContext parseContent(primaryBody, recorder)
            }
        } catch (e: Exception) {
            recorder.onAnalysisException(e)
            Log.e(
                TAG,
                "Analysis error diagnostic=${recorder.analysisDiagnosticId} type=${e.javaClass.simpleName}"
            )
            Result.failure(e)
        }
    }

    private fun okhttp3.Response.toDiagnostic(
        requestedModel: String,
        body: String?,
        isFallback: Boolean
    ): DiagnosticHttpResponse = DiagnosticHttpResponse(
        requestedModel = requestedModel,
        httpStatus = code,
        requestId = header("x-request-id"),
        actualModel = parseActualModel(body),
        body = body,
        receivedAt = System.currentTimeMillis(),
        isFallback = isFallback
    )

    private fun analyzeWithFallback(
        base64Image: String,
        modelName: String,
        language: String,
        primaryStatus: Int,
        recorder: AnalysisDiagnosticsRecorder
    ): Result<NutritionResultData> {
        val fallbackRequest = createRequest(base64Image, AiConfig.MODEL_FALLBACK, language)
        return client.newCall(fallbackRequest).execute().use { fallbackResponse ->
            val fallbackBody = fallbackResponse.body?.string()
            recorder.onHttpResponse(
                fallbackResponse.toDiagnostic(
                    requestedModel = AiConfig.MODEL_FALLBACK,
                    body = fallbackBody,
                    isFallback = true
                )
            )

            if (fallbackResponse.isSuccessful && fallbackBody != null) {
                val fallbackResult = parseContent(fallbackBody, recorder)
                if (fallbackResult.isSuccess) {
                    Log.d(TAG, "Fallback successful diagnostic=${recorder.analysisDiagnosticId}")
                    return fallbackResult.map { it.copy(needsModelUpdateNotice = true) }
                }
            }

            Log.e(
                TAG,
                "Fallback also failed diagnostic=${recorder.analysisDiagnosticId} status=${fallbackResponse.code}"
            )
            Result.failure(
                ModelAccessDeniedException(modelName, primaryStatus, fallbackResponse.code)
            )
        }
    }

    private fun parseContent(
        body: String,
        recorder: AnalysisDiagnosticsRecorder
    ): Result<NutritionResultData> {
        val openAiResponse = gson.fromJson(body, ResponsesApiResponse::class.java)

        val content = openAiResponse.output
            ?.firstOrNull { it.type == "message" }
            ?.content
            ?.firstOrNull { it.type == "output_text" }
            ?.text

        if (content == null) {
            recorder.onContentParsed(
                DiagnosticParsedContent(
                    responseId = openAiResponse.id,
                    actualModel = openAiResponse.model,
                    missingFields = emptyList(),
                    hasContent = false,
                    parsedCalories = null,
                    parsedConfidence = null
                )
            )
            Log.e(TAG, "No content in response diagnostic=${recorder.analysisDiagnosticId}")
            return Result.failure(Exception("No content in response"))
        }

        val missingFields = findMissingNumericFields(content)
        val nutritionData = try {
            gson.fromJson(content, NutritionResultData::class.java)
        } catch (e: Exception) {
            recorder.onContentParsed(
                DiagnosticParsedContent(
                    responseId = openAiResponse.id,
                    actualModel = openAiResponse.model,
                    missingFields = missingFields,
                    hasContent = true,
                    parsedCalories = null,
                    parsedConfidence = null
                )
            )
            throw e
        }

        recorder.onNutritionParsed(nutritionData)
        recorder.onContentParsed(
            DiagnosticParsedContent(
                responseId = openAiResponse.id,
                actualModel = openAiResponse.model,
                missingFields = missingFields,
                hasContent = true,
                parsedCalories = nutritionData.calories,
                parsedConfidence = nutritionData.confidence
            )
        )
        return Result.success(nutritionData)
    }

    private fun readImageBytes(
        imagePath: String,
        recorder: AnalysisDiagnosticsRecorder
    ): ByteArray {
        return try {
            File(imagePath).readBytes()
        } catch (e: Exception) {
            recorder.onImageLoaded(
                DiagnosticImageInfo(
                    sha256 = null,
                    byteCount = null,
                    mimeType = null,
                    missingReason = "image_read_failed: " +
                        SecretRedactor.redact(e.message, trimmedApiKey)
                )
            )
            throw e
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun parseActualModel(body: String?): String? {
        if (body == null) return null
        return runCatching {
            gson.fromJson(body, ResponsesApiResponse::class.java)?.model
        }.getOrNull()
    }

    /**
     * Reports required numeric fields that the model omitted. Gson silently
     * fills the missing primitives with zero, so this must be checked against
     * the raw JSON before parsing.
     */
    private fun findMissingNumericFields(content: String): List<String> {
        val missing = mutableListOf<String>()
        val root = runCatching { gson.fromJson(content, JsonObject::class.java) }.getOrNull() ?: return missing
        REQUIRED_TOP_LEVEL_FIELDS.forEach { field ->
            if (!root.has(field) || root.get(field).isJsonNull) {
                missing.add(field)
            }
        }
        collectMissingNutrientAmounts(root, "nutrients", missing)
        val items = root.getAsJsonArray("items")
        items?.forEachIndexed { index, element ->
            val item = runCatching { element.asJsonObject }.getOrNull() ?: return@forEachIndexed
            if (!item.has("calories") || item.get("calories").isJsonNull) {
                missing.add("items[$index].calories")
            }
            if (!item.has("name")) missing.add("items[$index].name")
            collectMissingNutrientAmounts(item, "nutrients", missing, prefix = "items[$index]")
        }
        return missing.distinct()
    }

    private fun collectMissingNutrientAmounts(
        parent: JsonObject,
        field: String,
        missing: MutableList<String>,
        prefix: String = ""
    ) {
        val nutrients = parent.getAsJsonArray(field) ?: run {
            missing.add(if (prefix.isEmpty()) field else "$prefix.$field")
            return
        }
        nutrients.forEachIndexed { index, element ->
            val nutrient = runCatching { element.asJsonObject }.getOrNull() ?: return@forEachIndexed
            if (!nutrient.has("amount") || nutrient.get("amount").isJsonNull) {
                val base = if (prefix.isEmpty()) "nutrients" else "$prefix.nutrients"
                missing.add("$base[$index].amount")
            }
        }
    }

    private fun createRequest(base64Image: String, modelName: String, language: String): Request {
        val requestBody = ResponsesApiRequest(
            model = modelName,
            input = listOf(
                InputMessage(
                    role = "system",
                    content = listOf(
                        ContentItem(type = "input_text", text = getSystemPrompt(language))
                    )
                ),
                InputMessage(
                    role = "user",
                    content = listOf(
                        ContentItem(type = "input_text", text = userPrompt),
                        ContentItem(
                            type = "input_image",
                            imageUrl = "data:image/jpeg;base64,$base64Image"
                        )
                    )
                )
            ),
            text = TextConfig()
        )

        val jsonBody = gson.toJson(requestBody)

        return Request.Builder()
            .url(baseUrl)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $trimmedApiKey")
            .addHeader("Content-Type", "application/json")
            .build()
    }
}

class OpenAiHttpException(
    val statusCode: Int
) : Exception("OpenAI API error: $statusCode")

class ModelAccessDeniedException(
    val requestedModel: String,
    val originalErrorCode: Int,
    val fallbackErrorCode: Int? = null
) : Exception(
    if (fallbackErrorCode != null) {
        "Model access denied: $requestedModel (HTTP $originalErrorCode), fallback also failed (HTTP $fallbackErrorCode)"
    } else {
        "Model access denied: $requestedModel (HTTP $originalErrorCode)"
    }
)
