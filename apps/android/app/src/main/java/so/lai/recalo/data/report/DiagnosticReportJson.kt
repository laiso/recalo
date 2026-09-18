package so.lai.recalo.data.report

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import so.lai.recalo.data.local.model.MealWithNutrition

/**
 * Builds the JSON documents that go into a diagnostic ZIP:
 * report.json, request.json, response.json and values.json.
 *
 * Everything written here has already passed through [SecretRedactor]; API
 * keys, authorization headers and full settings never reach the files.
 */
object DiagnosticReportJson {
    const val REPORT_VERSION = 1
    const val IMAGE_FILE_NAME = "image.jpg"
    private const val MAX_BODY_CHARS = 256 * 1024

    private val gson = Gson()

    fun fromSession(
        session: AnalysisDiagnosticSession,
        reportableReason: AnalysisReportReason,
        imageSource: File?,
        atReport: DiagnosticValueSnapshot?
    ): DiagnosticReportContent {
        val image = imageSource?.takeIf { it.isFile }
        return DiagnosticReportContent(
            diagnosticId = session.diagnosticId,
            mealId = session.mealId,
            createdAt = session.startedAt,
            reportJson = reportJson(session, reportableReason, image),
            requestJson = requestJson(session, image),
            responseJson = responseJson(session),
            valuesJson = valuesJson(session, atReport),
            metaJson = metaJson(
                diagnosticId = session.diagnosticId,
                mealId = session.mealId,
                createdAt = session.startedAt,
                attempt = session.attempt,
                reportableReason = reportableReason,
                requestId = session.requestId(),
                hasImage = image != null,
                legacy = false
            ),
            imageSource = image,
            legacy = false
        )
    }

    fun legacy(
        diagnosticId: String,
        meal: MealWithNutrition,
        appPackageName: String,
        appVersionName: String,
        appVersionCode: Long,
        androidRelease: String,
        androidSdkInt: Int,
        language: String,
        createdAt: Long,
        imageSource: File?
    ): DiagnosticReportContent {
        val image = imageSource?.takeIf { it.isFile }
        val atReport = DiagnosticValueSnapshot.from(
            stage = DiagnosticValueSnapshot.STAGE_AT_REPORT,
            capturedAt = createdAt,
            meal = meal
        )
        val report = JsonObject().apply {
            addProperty("reportVersion", REPORT_VERSION)
            addProperty("legacy", true)
            addProperty("diagnosticId", diagnosticId)
            addProperty("mealId", meal.meal.id)
            addProperty("createdAt", createdAt)
            addProperty("createdAtIso", isoUtc(createdAt))
            add("app", appJson(appPackageName, appVersionName, appVersionCode))
            add("device", deviceJson(androidRelease, androidSdkInt))
            addProperty("language", language)
            add(
                "analysis",
                JsonObject().apply {
                    add("requestedModel", JsonNull.INSTANCE)
                    add("actualModel", JsonNull.INSTANCE)
                    addProperty("status", meal.meal.analysisStatus)
                    addProperty("errorCode", meal.meal.analysisError)
                    add("httpStatus", JsonNull.INSTANCE)
                    add("requestId", JsonNull.INSTANCE)
                    addProperty("fallbackUsed", false)
                    addProperty("note", LEGACY_NOTE)
                }
            )
            add("image", imageJson(null, image))
            add("missingData", stringArray("no_recorded_attempt", "no_http_response"))
            addProperty("reportableReason", AnalysisReportReason.ANALYSIS_ERROR.name)
            add("notes", stringArray(LEGACY_NOTE, LEGACY_VALUES_NOTE))
        }

        val request = JsonObject().apply {
            addProperty("reportVersion", REPORT_VERSION)
            addProperty("legacy", true)
            addProperty("diagnosticId", diagnosticId)
            addProperty("available", false)
            addProperty("note", LEGACY_NOTE)
        }
        val response = JsonObject().apply {
            addProperty("reportVersion", REPORT_VERSION)
            addProperty("legacy", true)
            addProperty("diagnosticId", diagnosticId)
            addProperty("finalOutcome", "unknown")
            addProperty("note", LEGACY_RESPONSE_NOTE)
        }
        val values = JsonObject().apply {
            addProperty("reportVersion", REPORT_VERSION)
            addProperty("legacy", true)
            addProperty("diagnosticId", diagnosticId)
            addProperty("mealId", meal.meal.id)
            addProperty("portionRatioAtReport", meal.nutritionResult?.portionRatio)
            add("afterLoad", unavailableStage("not_recorded"))
            add("afterSave", unavailableStage("not_recorded"))
            add("atReport", snapshotJson(atReport))
            add("notes", stringArray(LEGACY_VALUES_NOTE))
        }

        return DiagnosticReportContent(
            diagnosticId = diagnosticId,
            mealId = meal.meal.id,
            createdAt = createdAt,
            reportJson = gson.toJson(report),
            requestJson = gson.toJson(request),
            responseJson = gson.toJson(response),
            valuesJson = gson.toJson(values),
            metaJson = metaJson(
                diagnosticId = diagnosticId,
                mealId = meal.meal.id,
                createdAt = createdAt,
                attempt = 0,
                reportableReason = AnalysisReportReason.ANALYSIS_ERROR,
                requestId = null,
                hasImage = image != null,
                legacy = true
            ),
            imageSource = image,
            legacy = true
        )
    }

    private fun reportJson(
        session: AnalysisDiagnosticSession,
        reportableReason: AnalysisReportReason,
        image: File?
    ): String {
        val primary = session.primaryResponse
        val fallback = session.fallbackResponse
        val json = JsonObject().apply {
            addProperty("reportVersion", REPORT_VERSION)
            addProperty("legacy", false)
            addProperty("diagnosticId", session.diagnosticId)
            addProperty("mealId", session.mealId)
            addProperty("attempt", session.attempt)
            addProperty("createdAt", session.startedAt)
            addProperty("createdAtIso", isoUtc(session.startedAt))
            add(
                "app",
                appJson(session.appPackageName, session.appVersionName, session.appVersionCode)
            )
            add("device", deviceJson(session.androidRelease, session.androidSdkInt))
            addProperty("language", session.language)
            add(
                "analysis",
                JsonObject().apply {
                    addProperty("requestedModel", session.requestedModel)
                    addProperty("actualModel", session.actualModel())
                    addProperty("status", session.analysisStatus)
                    addProperty("errorCode", session.errorCode)
                    add(
                        "httpStatus",
                        primary?.httpStatus?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE
                    )
                    add(
                        "requestId",
                        session.requestId()?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE
                    )
                    addProperty("fallbackUsed", fallback != null)
                    add(
                        "fallbackHttpStatus",
                        fallback?.httpStatus?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE
                    )
                    add(
                        "completedAt",
                        session.completedAt?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE
                    )
                    add(
                        "missingValueFields",
                        stringArray(
                            *(session.parsedContent?.missingFields?.toTypedArray() ?: emptyArray())
                        )
                    )
                }
            )
            add("image", imageJson(session.imageInfo, image))
            add("missingData", stringArray(*session.missingReasons().toTypedArray()))
            addProperty("reportableReason", reportableReason.name)
        }
        return gson.toJson(json)
    }

    private fun requestJson(session: AnalysisDiagnosticSession, image: File?): String {
        val info = session.requestInfo
        val json = JsonObject().apply {
            addProperty("reportVersion", REPORT_VERSION)
            addProperty("diagnosticId", session.diagnosticId)
            if (info == null) {
                addProperty("available", false)
                addProperty(
                    "note",
                    "The request could not be captured because the analysis stopped before it was built."
                )
            } else {
                addProperty("available", true)
                addProperty("model", info.model)
                addProperty("systemPrompt", info.systemPrompt)
                addProperty("userPrompt", info.userPrompt)
                add("schema", toJsonElement(info.schema))
                add("settings", toJsonElement(info.settings))
                add(
                    "image",
                    JsonObject().apply {
                        addProperty("sha256", info.imageSha256)
                        addProperty("file", if (image != null) IMAGE_FILE_NAME else null)
                        addProperty(
                            "note",
                            "Image bytes are not duplicated here; image.jpg is the exact image sent for analysis."
                        )
                    }
                )
            }
        }
        return gson.toJson(json)
    }

    private fun responseJson(session: AnalysisDiagnosticSession): String {
        val json = JsonObject().apply {
            addProperty("reportVersion", REPORT_VERSION)
            addProperty("diagnosticId", session.diagnosticId)
            addProperty("finalOutcome", outcomeOf(session))
            add("primary", responseEntry(session.primaryResponse))
            add("fallback", responseEntry(session.fallbackResponse))
            add("exception", exceptionJson(session.exceptionInfo))
            add("missing", stringArray(*session.missingReasons().toTypedArray()))
        }
        return gson.toJson(json)
    }

    private fun valuesJson(
        session: AnalysisDiagnosticSession,
        atReport: DiagnosticValueSnapshot?
    ): String {
        val json = JsonObject().apply {
            addProperty("reportVersion", REPORT_VERSION)
            addProperty("diagnosticId", session.diagnosticId)
            addProperty("mealId", session.mealId)
            addProperty("portionRatioAtReport", atReport?.portionRatio)
            add(
                "afterLoad",
                session.valuesAfterLoad?.let(::snapshotJson) ?: unavailableStage("not_captured")
            )
            add(
                "afterSave",
                session.valuesAfterSave?.let(::snapshotJson) ?: unavailableStage("not_captured")
            )
            add(
                "atReport",
                atReport?.let(::snapshotJson) ?: unavailableStage("not_available")
            )
            addProperty(
                "note",
                "portionRatio is the meal-portion multiplier recorded with the stored values."
            )
        }
        return gson.toJson(json)
    }

    private fun outcomeOf(session: AnalysisDiagnosticSession): String = when {
        session.responses.isEmpty() -> "no_http_response"
        session.fallbackResponse != null -> "fallback_response_used"
        session.analysisStatus == "completed" -> "completed"
        else -> "error"
    }

    private fun responseEntry(response: DiagnosticHttpResponse?): JsonElement {
        if (response == null) return JsonObject().apply { addProperty("available", false) }
        return JsonObject().apply {
            addProperty("available", true)
            addProperty("requestedModel", response.requestedModel)
            add("httpStatus", response.httpStatus?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
            add("requestId", response.requestId?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
            add("model", response.actualModel?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
            addProperty("receivedAt", response.receivedAt)
            addProperty("isFallback", response.isFallback)
            add("body", bodyElement(response.body))
        }
    }

    private fun bodyElement(body: String?): JsonElement {
        if (body == null) return JsonObject().apply { addProperty("available", false) }
        val truncated = body.length > MAX_BODY_CHARS
        val safeBody = if (truncated) body.substring(0, MAX_BODY_CHARS) else body
        val parsed = runCatching { gson.fromJson(safeBody, JsonElement::class.java) }.getOrNull()
        return JsonObject().apply {
            addProperty("available", true)
            addProperty("truncated", truncated)
            add("value", parsed ?: gson.toJsonTree(safeBody))
        }
    }

    private fun exceptionJson(info: DiagnosticExceptionInfo?): JsonElement {
        if (info == null) return JsonObject().apply { addProperty("available", false) }
        return JsonObject().apply {
            addProperty("available", true)
            addProperty("type", info.type)
            addProperty("message", info.message)
            add("stack", stringArray(*info.stack.toTypedArray()))
        }
    }

    internal fun snapshotJson(snapshot: DiagnosticValueSnapshot): JsonObject = JsonObject().apply {
        addProperty("available", true)
        addProperty("stage", snapshot.stage)
        addProperty("capturedAt", snapshot.capturedAt)
        add("mealId", snapshot.mealId?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
        addProperty("analysisStatus", snapshot.analysisStatus)
        addProperty("analysisError", snapshot.analysisError)
        add(
            "capturedAtMillis",
            snapshot.capturedAtMillis?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE
        )
        add(
            "analysisCompletedAt",
            snapshot.analysisCompletedAt?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE
        )
        add(
            "nutritionResultId",
            snapshot.nutritionResultId?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE
        )
        addProperty("title", snapshot.title)
        add("calories", snapshot.calories?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
        add("confidence", snapshot.confidence?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
        add("portionRatio", snapshot.portionRatio?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
        addProperty("missingReason", snapshot.missingReason)
        add(
            "items",
            JsonArray().apply {
                snapshot.items.forEach { item ->
                    add(
                        JsonObject().apply {
                            addProperty("id", item.id)
                            addProperty("name", item.name)
                            addProperty("quantity", item.quantity)
                            addProperty("calories", item.calories)
                            add(
                                "nutrients",
                                JsonArray().apply {
                                    item.nutrients.forEach { add(nutrientJson(it)) }
                                }
                            )
                        }
                    )
                }
            }
        )
        add(
            "totalNutrients",
            JsonArray().apply {
                snapshot.totalNutrients.forEach { add(nutrientJson(it)) }
            }
        )
    }

    private fun nutrientJson(nutrient: DiagnosticValueNutrient): JsonObject = JsonObject().apply {
        addProperty("name", nutrient.name)
        addProperty("amount", nutrient.amount)
        addProperty("unit", nutrient.unit)
        addProperty("scope", nutrient.scope)
    }

    private fun imageJson(info: DiagnosticImageInfo?, image: File?): JsonObject = JsonObject().apply {
        addProperty("available", image != null)
        add("sha256", info?.sha256?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
        add("bytes", info?.byteCount?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
        add("mimeType", info?.mimeType?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
        addProperty("file", if (image != null) IMAGE_FILE_NAME else null)
        add("missingReason", info?.missingReason?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
    }

    private fun unavailableStage(reason: String): JsonObject = JsonObject().apply {
        addProperty("available", false)
        addProperty("missingReason", reason)
    }

    private fun appJson(
        packageName: String?,
        versionName: String,
        versionCode: Long
    ): JsonObject = JsonObject().apply {
        add("packageName", packageName?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
        addProperty("versionName", versionName)
        addProperty("versionCode", versionCode)
    }

    private fun deviceJson(androidRelease: String, sdkInt: Int): JsonObject = JsonObject().apply {
        addProperty("androidRelease", androidRelease)
        addProperty("sdkInt", sdkInt)
    }

    private fun metaJson(
        diagnosticId: String,
        mealId: String,
        createdAt: Long,
        attempt: Int,
        reportableReason: AnalysisReportReason,
        requestId: String?,
        hasImage: Boolean,
        legacy: Boolean
    ): String {
        val json = JsonObject().apply {
            addProperty("reportVersion", REPORT_VERSION)
            addProperty("diagnosticId", diagnosticId)
            addProperty("mealId", mealId)
            addProperty("createdAt", createdAt)
            addProperty("attempt", attempt)
            addProperty("reportableReason", reportableReason.name)
            add("requestId", requestId?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
            addProperty("hasImage", hasImage)
            addProperty("legacy", legacy)
        }
        return gson.toJson(json)
    }

    private fun toJsonElement(value: Any?): JsonElement =
        when (value) {
            null -> JsonObject()
            is JsonElement -> value
            else -> gson.toJsonTree(value)
        }

    private fun stringArray(vararg values: String): JsonArray =
        JsonArray().apply { values.forEach { add(it) } }

    private fun isoUtc(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(timestamp))
    }

    const val LEGACY_NOTE =
        "This analysis ran before diagnostic recording was introduced, so the original request and " +
            "API response are not available. Only the currently stored image and database values are attached."
    const val LEGACY_RESPONSE_NOTE =
        "No API response was recorded for this analysis (it predates diagnostic recording)."
    const val LEGACY_VALUES_NOTE =
        "Only the values stored at report time are shown. Earlier stages were not recorded and are not guessed."
}

/**
 * The in-memory result of building one diagnostic report. [imageSource] is the
 * exact image that was sent for analysis, if it is still available.
 */
data class DiagnosticReportContent(
    val diagnosticId: String,
    val mealId: String,
    val createdAt: Long,
    val reportJson: String,
    val requestJson: String,
    val responseJson: String,
    val valuesJson: String,
    val metaJson: String,
    val imageSource: File?,
    val legacy: Boolean
)
