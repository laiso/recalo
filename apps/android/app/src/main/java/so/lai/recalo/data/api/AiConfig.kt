package so.lai.recalo.data.api

object AiConfig {
    const val LEVEL_LOW = "low"
    const val LEVEL_MEDIUM = "medium"
    const val LEVEL_HIGH = "high"

    const val MODEL_LOW = "gpt-4o-mini"
    const val MODEL_MEDIUM = "gpt-5-mini"
    const val MODEL_HIGH = "gpt-5.4"

    data class ModelOption(
        val level: String,
        val label: String,
        val description: String,
        val modelId: String
    )

    val options = listOf(
        ModelOption(
            level = LEVEL_LOW,
            label = "Low (gpt-4o-mini)",
            description = "~$0.0004 / image",
            modelId = MODEL_LOW
        ),
        ModelOption(
            level = LEVEL_MEDIUM,
            label = "Medium (gpt-5-mini)",
            description = "~$0.001 / image",
            modelId = MODEL_MEDIUM
        ),
        ModelOption(
            level = LEVEL_HIGH,
            label = "High (gpt-5.4)",
            description = "~$0.02 / image",
            modelId = MODEL_HIGH
        )
    )

    fun getModelId(level: String): String {
        return options.find { it.level == level }?.modelId ?: MODEL_LOW
    }
}
