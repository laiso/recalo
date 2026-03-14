package so.lai.recalo.data.openai

import com.google.gson.annotations.SerializedName

data class ResponsesApiRequest(
    val input: List<InputMessage>,
    val model: String = "gpt-4o-mini",
    val text: TextConfig? = null
)

data class InputMessage(
    val role: String,
    val content: List<ContentItem>
)

data class ContentItem(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: String? = null
)

data class TextConfig(
    val format: TextFormat = TextFormat()
)

data class TextFormat(
    val type: String = "json_schema",
    val name: String = "nutrition",
    val schema: ResponseSchema = ResponseSchema()
)

data class ResponseSchema(
    val type: String = "object",
    val properties: SchemaProperties = SchemaProperties(),
    val required: List<String> = listOf("title", "calories", "confidence", "nutrients", "items"),
    val additionalProperties: Boolean = false
)

data class SchemaProperties(
    val title: SchemaProperty = SchemaProperty(
        type = "string",
        description = "A descriptive title for the meal (e.g., 'Grilled Salmon Set', 'Chicken Teriyaki Bowl')"
    ),
    val calories: SchemaProperty = SchemaProperty(type = "number"),
    val confidence: SchemaProperty = SchemaProperty(type = "number"),
    val nutrients: NutrientsSchemaProperty = NutrientsSchemaProperty(),
    val items: MealItemsSchemaProperty = MealItemsSchemaProperty()
)

data class SchemaProperty(
    val type: String,
    val description: String? = null
)

data class NutrientsSchemaProperty(
    val type: String = "array",
    val description: String = "List of nutrients in the meal",
    val items: NutrientItemSchema = NutrientItemSchema()
)

data class NutrientItemSchema(
    val type: String = "object",
    val properties: NutrientPropertySchema = NutrientPropertySchema(),
    val required: List<String> = listOf("name", "amount", "unit"),
    val additionalProperties: Boolean = false
)

data class NutrientPropertySchema(
    val name: SchemaProperty = SchemaProperty(type = "string", description = "Name of the nutrient"),
    val amount: SchemaProperty = SchemaProperty(
        type = "number",
        description = "Amount of the nutrient"
    ),
    val unit: SchemaProperty = SchemaProperty(type = "string", description = "Unit of measurement")
)

data class MealItemsSchemaProperty(
    val type: String = "array",
    val description: String = "List of individual food items in the meal",
    val items: MealItemSchema = MealItemSchema()
)

data class MealItemSchema(
    val type: String = "object",
    val properties: MealItemPropertySchema = MealItemPropertySchema(),
    val required: List<String> = listOf("name", "quantity", "calories", "nutrients"),
    val additionalProperties: Boolean = false
)

data class MealItemPropertySchema(
    val name: SchemaProperty = SchemaProperty(
        type = "string",
        description = "Name of the food item"
    ),
    val quantity: SchemaProperty = SchemaProperty(
        type = "string",
        description = "Quantity of the food item"
    ),
    val calories: SchemaProperty = SchemaProperty(
        type = "number",
        description = "Calories of the food item"
    ),
    val nutrients: NutrientsSchemaProperty = NutrientsSchemaProperty()
)

data class ResponsesApiResponse(
    val id: String?,
    val output: List<OutputItem>?,
    val model: String?,
    val usage: UsageInfo?,
    @SerializedName("created_at")
    val createdAt: Long?
)

data class OutputItem(
    val type: String?,
    val id: String?,
    val status: String?,
    @SerializedName("created_at")
    val createdAt: Long?,
    val content: List<ContentOutput>?
)

data class ContentOutput(
    val type: String?,
    val text: String?
)

data class UsageInfo(
    @SerializedName("input_tokens")
    val inputTokens: Int?,
    @SerializedName("output_tokens")
    val outputTokens: Int?,
    @SerializedName("total_tokens")
    val totalTokens: Int?
)
