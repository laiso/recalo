package so.lai.recalo.data.openai

interface NutritionAnalyzer {
    suspend fun analyzeNutrition(
        imagePath: String,
        modelName: String = "gpt-5.4-nano",
        language: String = "English"
    ): Result<NutritionResultData>
}

fun interface NutritionAnalyzerFactory {
    fun create(apiKey: String): NutritionAnalyzer
}
