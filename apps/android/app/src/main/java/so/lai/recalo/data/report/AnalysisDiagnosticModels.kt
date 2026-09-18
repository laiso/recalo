package so.lai.recalo.data.report

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import so.lai.recalo.data.local.entity.NutrientEntity
import so.lai.recalo.data.local.model.MealWithNutrition
import so.lai.recalo.data.openai.NutritionResultData

/**
 * Optional diagnostic callback handed to the analysis service.
 *
 * Implementations collect what actually happened during one analysis attempt
 * (image that was sent, request that was built, raw HTTP responses, parse
 * outcome, exception). The service's return value is unaffected and test
 * implementations default to [NoOp].
 */
interface AnalysisDiagnosticsRecorder {
    /** Diagnostic id of the current attempt, or null when diagnostics are off. */
    val analysisDiagnosticId: String?

    fun onImageLoaded(info: DiagnosticImageInfo)

    fun onRequestPrepared(info: DiagnosticRequestInfo)

    fun onHttpResponse(info: DiagnosticHttpResponse)

    fun onContentParsed(info: DiagnosticParsedContent)

    fun onNutritionParsed(nutrition: NutritionResultData) = Unit

    fun onAnalysisException(error: Throwable)

    companion object {
        val NoOp: AnalysisDiagnosticsRecorder = object : AnalysisDiagnosticsRecorder {
            override val analysisDiagnosticId: String? = null
            override fun onImageLoaded(info: DiagnosticImageInfo) = Unit
            override fun onRequestPrepared(info: DiagnosticRequestInfo) = Unit
            override fun onHttpResponse(info: DiagnosticHttpResponse) = Unit
            override fun onContentParsed(info: DiagnosticParsedContent) = Unit
            override fun onAnalysisException(error: Throwable) = Unit
        }
    }
}

data class DiagnosticImageInfo(
    val sha256: String?,
    val byteCount: Long?,
    val mimeType: String?,
    val missingReason: String?
)

data class DiagnosticRequestInfo(
    val model: String,
    val systemPrompt: String,
    val userPrompt: String,
    val schema: Any?,
    val settings: Map<String, Any?>,
    val imageSha256: String?
)

data class DiagnosticHttpResponse(
    val requestedModel: String,
    val httpStatus: Int?,
    val requestId: String?,
    val actualModel: String?,
    val body: String?,
    val receivedAt: Long,
    val isFallback: Boolean
)

data class DiagnosticParsedContent(
    val responseId: String?,
    val actualModel: String?,
    val missingFields: List<String>,
    val hasContent: Boolean,
    val parsedCalories: Double?,
    val parsedConfidence: Double?
)

data class DiagnosticValueItem(
    val id: String,
    val name: String,
    val quantity: String,
    val calories: Int,
    val nutrients: List<DiagnosticValueNutrient>
)

data class DiagnosticValueNutrient(
    val name: String,
    val amount: Double,
    val unit: String,
    val scope: String
)

/**
 * A snapshot of the database values at one point in the analysis lifecycle.
 * Missing values are represented by nulls plus [missingReason]; nothing is
 * guessed or back-filled.
 */
data class DiagnosticValueSnapshot(
    val stage: String,
    val capturedAt: Long,
    val mealId: String?,
    val analysisStatus: String?,
    val analysisError: String?,
    val capturedAtMillis: Long?,
    val analysisCompletedAt: Long?,
    val nutritionResultId: String?,
    val title: String?,
    val calories: Int?,
    val confidence: Double?,
    val portionRatio: Double?,
    val items: List<DiagnosticValueItem>,
    val totalNutrients: List<DiagnosticValueNutrient>,
    val missingReason: String?
) {
    companion object {
        const val STAGE_AFTER_LOAD = "afterLoad"
        const val STAGE_AFTER_SAVE = "afterSave"
        const val STAGE_AT_REPORT = "atReport"

        fun from(
            stage: String,
            capturedAt: Long,
            meal: MealWithNutrition?,
            missingReason: String? = null
        ): DiagnosticValueSnapshot {
            if (meal == null) {
                return DiagnosticValueSnapshot(
                    stage = stage,
                    capturedAt = capturedAt,
                    mealId = null,
                    analysisStatus = null,
                    analysisError = null,
                    capturedAtMillis = null,
                    analysisCompletedAt = null,
                    nutritionResultId = null,
                    title = null,
                    calories = null,
                    confidence = null,
                    portionRatio = null,
                    items = emptyList(),
                    totalNutrients = emptyList(),
                    missingReason = missingReason ?: "meal_not_found"
                )
            }

            val mealItems = meal.items.orEmpty().map { item ->
                DiagnosticValueItem(
                    id = item.mealItem.id,
                    name = item.mealItem.name,
                    quantity = item.mealItem.quantity,
                    calories = item.mealItem.calories,
                    nutrients = item.nutrients.map { it.toValueNutrient("item") }
                )
            }

            return DiagnosticValueSnapshot(
                stage = stage,
                capturedAt = capturedAt,
                mealId = meal.meal.id,
                analysisStatus = meal.meal.analysisStatus,
                analysisError = meal.meal.analysisError,
                capturedAtMillis = meal.meal.capturedAt,
                analysisCompletedAt = meal.meal.analysisCompletedAt,
                nutritionResultId = meal.nutritionResult?.id,
                title = meal.nutritionResult?.title,
                calories = meal.nutritionResult?.calories,
                confidence = meal.nutritionResult?.confidence,
                portionRatio = meal.nutritionResult?.portionRatio,
                items = mealItems,
                totalNutrients = meal.nutrients.orEmpty().map { it.toValueNutrient("total") },
                missingReason = if (meal.nutritionResult == null) {
                    missingReason ?: "no_nutrition_result_stored"
                } else {
                    missingReason
                }
            )
        }

        private fun NutrientEntity.toValueNutrient(scope: String) = DiagnosticValueNutrient(
            name = name,
            amount = amount,
            unit = unit,
            scope = scope
        )
    }
}

data class DiagnosticExceptionInfo(
    val type: String?,
    val message: String?,
    val stack: List<String>
)

/**
 * Mutable collector for a single analysis attempt. The repository decides
 * afterwards whether the attempt is worth keeping on disk.
 */
class AnalysisDiagnosticSession(
    val diagnosticId: String,
    val mealId: String,
    val attempt: Int,
    val startedAt: Long,
    val requestedModel: String,
    val language: String,
    val appPackageName: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val androidRelease: String,
    val androidSdkInt: Int,
    val secrets: List<String>
) : AnalysisDiagnosticsRecorder {
    override val analysisDiagnosticId: String?
        get() = diagnosticId

    var imageInfo: DiagnosticImageInfo? = null
        private set
    var requestInfo: DiagnosticRequestInfo? = null
        private set
    var parsedContent: DiagnosticParsedContent? = null
        private set
    var parsedNutrition: JsonElement? = null
        private set
    var exceptionInfo: DiagnosticExceptionInfo? = null
        private set
    var valuesAfterLoad: DiagnosticValueSnapshot? = null
    var valuesAfterSave: DiagnosticValueSnapshot? = null
    var analysisStatus: String? = null
    var errorCode: String? = null
    var completedAt: Long? = null

    private val collectedResponses = mutableListOf<DiagnosticHttpResponse>()
    val responses: List<DiagnosticHttpResponse>
        get() = collectedResponses.toList()

    val primaryResponse: DiagnosticHttpResponse?
        get() = collectedResponses.firstOrNull { !it.isFallback }

    val fallbackResponse: DiagnosticHttpResponse?
        get() = collectedResponses.firstOrNull { it.isFallback }

    override fun onImageLoaded(info: DiagnosticImageInfo) {
        imageInfo = info
    }

    override fun onRequestPrepared(info: DiagnosticRequestInfo) {
        requestInfo = info
    }

    override fun onHttpResponse(info: DiagnosticHttpResponse) {
        collectedResponses.add(
            info.copy(body = SecretRedactor.redact(info.body, secrets))
        )
    }

    override fun onContentParsed(info: DiagnosticParsedContent) {
        parsedContent = info
    }

    override fun onNutritionParsed(nutrition: NutritionResultData) {
        // Capture an immutable, redacted snapshot before repository conversion or writes.
        // Diagnostic serialization must not turn a successful analysis into a failure.
        parsedNutrition = runCatching {
            val gson = GsonBuilder().serializeNulls().create()
            val json = gson.toJson(nutrition)
            gson.fromJson(SecretRedactor.redact(json, secrets), JsonElement::class.java)
        }.getOrNull()
    }

    override fun onAnalysisException(error: Throwable) {
        exceptionInfo = DiagnosticExceptionInfo(
            type = error.javaClass.name,
            message = SecretRedactor.redact(error.message, secrets),
            stack = error.stackTrace.take(30).map { SecretRedactor.redact(it.toString(), secrets).orEmpty() }
        )
    }

    fun missingReasons(): List<String> {
        val reasons = mutableListOf<String>()
        if (collectedResponses.isEmpty()) reasons.add("no_http_response")
        if (imageInfo == null || imageInfo?.missingReason != null) reasons.add("image_unavailable")
        if (requestInfo == null) reasons.add("request_not_captured")
        if (valuesAfterLoad == null) reasons.add("values_after_load_not_captured")
        if (valuesAfterSave == null) reasons.add("values_after_save_not_captured")
        return reasons
    }

    fun actualModel(): String? =
        parsedContent?.actualModel
            ?: responses.lastOrNull()?.actualModel

    fun requestId(): String? =
        responses.lastOrNull()?.requestId
            ?: parsedContent?.responseId
}
