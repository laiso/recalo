package so.lai.recalo.data.openai

import so.lai.recalo.data.report.AnalysisDiagnosticsRecorder

interface NutritionAnalyzer {
    suspend fun analyzeNutrition(
        imagePath: String,
        modelName: String = "gpt-5.4-nano",
        language: String = "English",
        diagnostics: AnalysisDiagnosticsRecorder? = null
    ): Result<NutritionResultData>
}

fun interface NutritionAnalyzerFactory {
    fun create(apiKey: String): NutritionAnalyzer
}
