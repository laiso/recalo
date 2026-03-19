package so.lai.recalo.data.openai

import com.google.gson.annotations.SerializedName

data class OpenAiNutritionResponse(
    val id: String?,
    val choices: List<Choice>?,
    val model: String?,
    val usage: Usage?
)

data class Choice(
    val message: ResponseMessage?,
    val finish_reason: String?
)

data class ResponseMessage(
    val role: String?,
    val content: String?,
    @SerializedName("refusal")
    val refusal: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int?,
    @SerializedName("completion_tokens")
    val completionTokens: Int?,
    @SerializedName("total_tokens")
    val totalTokens: Int?
)

data class NutritionResultData(
    val title: String? = null,
    val calories: Double,
    val confidence: Double,
    val nutrients: List<NutrientData>,
    val items: List<MealItemData>,
    val needsModelUpdateNotice: Boolean = false
)

data class MealItemData(
    val name: String,
    val quantity: String,
    val calories: Double,
    val nutrients: List<NutrientData>
)

data class NutrientData(
    val name: String,
    val amount: Double,
    val unit: String
)
