package so.lai.recalo.data.openai

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import so.lai.recalo.data.api.AiConfig

/**
 * Nutrition analysis service using OpenAI Responses API
 * https://platform.openai.com/docs/api-reference/responses
 */
class OpenAiService(
    private val apiKey: String,
    private val timeoutSeconds: Long = 60,
    private val baseUrl: String = OPENAI_API_URL
) {
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

    suspend fun analyzeNutrition(
        imagePath: String,
        modelName: String = "gpt-5.4-nano",
        language: String = "English"
    ): Result<NutritionResultData> = withContext(Dispatchers.IO) {
        try {
            val base64Image = encodeImageToBase64(imagePath)
            val request = createRequest(base64Image, modelName, language)

            Log.d(TAG, "Sending request to OpenAI Responses API using model: $modelName")

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "OpenAI API error: ${response.code} $errorBody")
                
                when (response.code) {
                    403, 404 -> {
                        Log.w(TAG, "Model $modelName not accessible, falling back to ${AiConfig.MODEL_FALLBACK}")
                        val fallbackRequest = createRequest(base64Image, AiConfig.MODEL_FALLBACK, language)

                        client.newCall(fallbackRequest).execute().use { fallbackResponse ->
                            if (fallbackResponse.isSuccessful) {
                                val fallbackBody = fallbackResponse.body?.string()
                                Log.d(TAG, "Fallback successful with ${AiConfig.MODEL_FALLBACK}")
                                val fallbackParsed = gson.fromJson(fallbackBody, ResponsesApiResponse::class.java)
                                val fallbackContent = fallbackParsed.output
                                    ?.firstOrNull { it.type == "message" }
                                    ?.content
                                    ?.firstOrNull { it.type == "output_text" }
                                    ?.text

                                if (fallbackContent != null) {
                                    val fallbackNutrition = gson.fromJson(fallbackContent, NutritionResultData::class.java)
                                    return@withContext Result.success(fallbackNutrition.copy(needsModelUpdateNotice = true))
                                }
                            }

                            val fallbackErrorBody = fallbackResponse.body?.string()
                            Log.e(TAG, "Fallback also failed: ${fallbackResponse.code} $fallbackErrorBody")
                            return@withContext Result.failure(
                                ModelAccessDeniedException(modelName, response.code, fallbackResponse.code)
                            )
                        }
                    }
                    else -> return@withContext Result.failure(Exception("OpenAI API error: ${response.code}"))
                }
            }

            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "Empty response from OpenAI")
                return@withContext Result.failure(Exception("Empty response"))
            }

            Log.d(TAG, "Received response: $responseBody")

            val openAiResponse = gson.fromJson(responseBody, ResponsesApiResponse::class.java)

            val content = openAiResponse.output
                ?.firstOrNull { it.type == "message" }
                ?.content
                ?.firstOrNull { it.type == "output_text" }
                ?.text

            if (content == null) {
                Log.e(TAG, "No content in OpenAI response")
                return@withContext Result.failure(Exception("No content in response"))
            }

            val nutritionData = gson.fromJson(content, NutritionResultData::class.java)
            Log.d(TAG, "Parsed nutrition data: $nutritionData")

            Result.success(nutritionData)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing nutrition", e)
            Result.failure(e)
        }
    }

    private fun encodeImageToBase64(imagePath: String): String {
        val bytes = File(imagePath).readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
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
