package so.lai.recalo.data.openai

import com.google.gson.annotations.SerializedName

data class OpenAiNutritionRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<Message>,
    val temperature: Double = 0.2,
    @SerializedName("response_format")
    val responseFormat: ResponseFormat = ResponseFormat()
)

data class Message(
    val role: String,
    val content: List<Content>
)

data class Content(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String
)

data class ResponseFormat(
    val type: String = "json_schema",
    val json_schema: JsonSchema = JsonSchema()
)

data class JsonSchema(
    val name: String = "nutrition",
    val strict: Boolean = true,
    val schema: Schema = Schema()
)

data class Schema(
    val type: String = "object",
    val properties: Properties = Properties(),
    val required: List<String> = listOf("calories", "confidence", "nutrients", "items"),
    val additionalProperties: Boolean = false
)

data class Properties(
    val calories: Property = Property(type = "number"),
    val confidence: Property = Property(type = "number"),
    val nutrients: NutrientsProperty = NutrientsProperty(),
    val items: MealItemsProperty = MealItemsProperty()
)

data class Property(
    val type: String
)

data class NutrientsProperty(
    val type: String = "array",
    val items: Items = Items()
)

data class Items(
    val type: String = "object",
    val properties: NutrientProperties = NutrientProperties(),
    val required: List<String> = listOf("name", "amount", "unit"),
    val additionalProperties: Boolean = false
)

data class NutrientProperties(
    val name: Property = Property(type = "string"),
    val amount: Property = Property(type = "number"),
    val unit: Property = Property(type = "string")
)

data class MealItemsProperty(
    val type: String = "array",
    val items: MealItemProperties = MealItemProperties()
)

data class MealItemProperties(
    val type: String = "object",
    val properties: MealItemFields = MealItemFields(),
    val required: List<String> = listOf("name", "quantity", "calories", "nutrients"),
    val additionalProperties: Boolean = false
)

data class MealItemFields(
    val name: Property = Property(type = "string"),
    val quantity: Property = Property(type = "string"),
    val calories: Property = Property(type = "number"),
    val nutrients: NutrientsProperty = NutrientsProperty()
)
