package so.lai.recalo.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import so.lai.recalo.data.image.MealImageStorage
import so.lai.recalo.data.local.CaroliDatabase
import so.lai.recalo.data.local.dao.MealDao
import so.lai.recalo.data.local.entity.MealItemEntity
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.entity.NutrientEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity
import so.lai.recalo.data.local.model.MealWithNutrition
import so.lai.recalo.data.openai.NutritionAnalyzerFactory
import so.lai.recalo.data.openai.NutritionResultData
import so.lai.recalo.data.openai.OpenAiService
import so.lai.recalo.data.report.AnalysisDiagnosticSession
import so.lai.recalo.data.report.AnalysisDiagnosticsStore
import so.lai.recalo.data.report.AnalysisReportability
import so.lai.recalo.data.report.DiagnosticReportJson
import so.lai.recalo.data.report.DiagnosticValueSnapshot
import so.lai.recalo.data.report.SecretRedactor
import so.lai.recalo.data.report.analysisReportability

class MealRepository(
    private val dao: MealDao,
    private val database: CaroliDatabase? = null,
    private val analyzerFactory: NutritionAnalyzerFactory = NutritionAnalyzerFactory { apiKey ->
        OpenAiService(apiKey = apiKey)
    },
    private val diagnosticStore: AnalysisDiagnosticsStore? = null
) {
    companion object {
        private const val TAG = "MealRepository"
        private val NUMBER_PATTERN = Regex("(\\d+\\.?\\d*)")
    }

    fun getAllMealsWithNutrition(): Flow<List<MealWithNutrition>> {
        return dao.getAllMealsWithNutrition()
    }

    suspend fun searchPreviousMeals(query: String): List<MealWithNutrition> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()

        return dao.searchMealsByFoodName(normalizedQuery)
    }

    suspend fun duplicateMealFromHistory(
        sourceMeal: MealWithNutrition,
        capturedAt: Long
    ): MealLogEntity {
        val db = database
        return if (db != null) {
            db.withTransaction {
                duplicateMealFromHistoryInternal(sourceMeal, capturedAt)
            }
        } else {
            duplicateMealFromHistoryInternal(sourceMeal, capturedAt)
        }
    }

    suspend fun uploadAndAnalyzeMeal(
        context: Context,
        imageUri: Uri,
        openAiApiKey: String,
        modelName: String = "gpt-4o-mini",
        capturedAt: Long? = null
    ): Result<MealLogEntity> {
        return try {
            val imageFile = MealImageStorage.saveCompressedJpeg(context, imageUri)
            Log.d(TAG, "Image compressed and saved to: ${imageFile.absolutePath}")

            val mealId = UUID.randomUUID().toString()
            val mealEntity = MealLogEntity(
                id = mealId,
                imageUrl = null,
                capturedAt = capturedAt ?: System.currentTimeMillis(),
                imagePath = imageFile.absolutePath,
                analysisStatus = MealLogEntity.AnalysisStatus.ANALYZING
            )

            dao.insertMeal(mealEntity)
            Log.d(TAG, "Meal entity inserted with ID: $mealId")

            analyzeMeal(mealEntity, openAiApiKey, modelName, context)
        } catch (e: Exception) {
            Log.e(TAG, "Upload and analyze failed", e)
            Result.failure(e)
        }
    }

    suspend fun retryAnalysis(
        mealId: String,
        openAiApiKey: String,
        modelName: String = "gpt-4o-mini"
    ): Result<MealLogEntity> {
        val meal = dao.getMealById(mealId)
            ?: return Result.failure(IllegalArgumentException("Meal not found"))
        val imagePath = meal.imagePath
        if (imagePath.isNullOrBlank() || !File(imagePath).isFile) {
            dao.updateMeal(
                meal.copy(
                    analysisStatus = MealLogEntity.AnalysisStatus.ERROR,
                    analysisError = AnalysisErrorCode.IMAGE_UNAVAILABLE.name
                )
            )
            return Result.failure(FileNotFoundException("Meal image is missing"))
        }

        val retryStarted = dao.beginAnalysisRetry(mealId) == 1
        if (!retryStarted) {
            return Result.failure(IllegalStateException("Meal analysis is not retryable"))
        }

        val analyzingMeal = meal.copy(
            analysisStatus = MealLogEntity.AnalysisStatus.ANALYZING,
            analysisError = null,
            analysisCompletedAt = null,
            needsModelUpdateNotice = false
        )
        return analyzeMeal(analyzingMeal, openAiApiKey, modelName, null)
    }

    private suspend fun analyzeMeal(
        mealEntity: MealLogEntity,
        openAiApiKey: String,
        modelName: String,
        context: Context?
    ): Result<MealLogEntity> {
        val store = diagnosticStore ?: context?.let { AnalysisDiagnosticsStore(it) }
        val currentLanguage = java.util.Locale.getDefault().displayLanguage
        val session = createDiagnosticSession(
            store = store,
            mealEntity = mealEntity,
            modelName = modelName,
            language = currentLanguage,
            openAiApiKey = openAiApiKey
        )
        session?.valuesAfterLoad = captureDiagnosticValues(
            mealId = mealEntity.id,
            stage = DiagnosticValueSnapshot.STAGE_AFTER_LOAD
        )

        return try {
            val analyzer = analyzerFactory.create(openAiApiKey)
            val analysisResult = analyzer.analyzeNutrition(
                imagePath = requireNotNull(mealEntity.imagePath),
                modelName = modelName,
                language = currentLanguage,
                diagnostics = session
            )

            val nutritionData = analysisResult.getOrNull()
            val result = if (analysisResult.isSuccess && nutritionData != null) {
                saveSuccessfulAnalysis(mealEntity, nutritionData)
            } else {
                val error = analysisResult.exceptionOrNull()
                    ?: Exception("Analysis succeeded but returned null data")
                Log.e(
                    TAG,
                    "Analysis failed diagnostic=${session?.diagnosticId}: " +
                        SecretRedactor.redact(error.message, openAiApiKey)
                )
                val updatedMeal = mealEntity.copy(
                    analysisStatus = MealLogEntity.AnalysisStatus.ERROR,
                    analysisError = AnalysisErrorCode.fromThrowable(error).name
                )
                dao.updateMeal(updatedMeal)
                Result.failure(error)
            }

            session?.valuesAfterSave = captureDiagnosticValues(
                mealId = mealEntity.id,
                stage = DiagnosticValueSnapshot.STAGE_AFTER_SAVE
            )
            persistDiagnosticSession(store, session, mealEntity)
            result
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Analysis failed with exception diagnostic=${session?.diagnosticId}: " +
                    SecretRedactor.redact(e.message, openAiApiKey)
            )
            val updatedMeal = mealEntity.copy(
                analysisStatus = MealLogEntity.AnalysisStatus.ERROR,
                analysisError = AnalysisErrorCode.fromThrowable(e).name
            )
            try {
                dao.updateMeal(updatedMeal)
            } catch (databaseError: Exception) {
                Log.e(TAG, "Failed to persist analysis error", databaseError)
            }
            session?.valuesAfterSave = captureDiagnosticValues(
                mealId = mealEntity.id,
                stage = DiagnosticValueSnapshot.STAGE_AFTER_SAVE
            )
            persistDiagnosticSession(store, session, mealEntity)
            Result.failure(e)
        }
    }

    private suspend fun saveSuccessfulAnalysis(
        mealEntity: MealLogEntity,
        nutritionData: NutritionResultData
    ): Result<MealLogEntity> {
        val resultId = UUID.randomUUID().toString()
        val nutritionEntity = NutritionResultEntity(
            id = resultId,
            mealLogId = mealEntity.id,
            title = nutritionData.title ?: "Untitled Meal",
            calories = nutritionData.calories.toInt(),
            confidence = nutritionData.confidence
        )

        val allNutrients = mutableListOf<NutrientEntity>()
        val mealItems = mutableListOf<MealItemEntity>()

        nutritionData.nutrients.forEach { n ->
            allNutrients.add(
                NutrientEntity(
                    id = UUID.randomUUID().toString(),
                    nutritionResultId = resultId,
                    mealItemId = null,
                    name = n.name,
                    amount = n.amount,
                    unit = n.unit
                )
            )
        }

        nutritionData.items.forEach { item ->
            val mealItemId = UUID.randomUUID().toString()
            val mealItemEntity = MealItemEntity(
                id = mealItemId,
                nutritionResultId = resultId,
                name = item.name,
                quantity = item.quantity,
                calories = item.calories.toInt()
            )
            mealItems.add(mealItemEntity)

            item.nutrients.forEach { n ->
                allNutrients.add(
                    NutrientEntity(
                        id = UUID.randomUUID().toString(),
                        nutritionResultId = null,
                        mealItemId = mealItemId,
                        name = n.name,
                        amount = n.amount,
                        unit = n.unit
                    )
                )
            }
        }

        val updatedMeal = mealEntity.copy(
            analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED,
            analysisError = null,
            analysisCompletedAt = System.currentTimeMillis(),
            needsModelUpdateNotice = nutritionData.needsModelUpdateNotice
        )
        dao.replaceAnalysisResult(
            meal = updatedMeal,
            result = nutritionEntity,
            items = mealItems,
            nutrients = allNutrients
        )
        Log.d(TAG, "Meal status updated to completed: ${mealEntity.id}")
        return Result.success(updatedMeal)
    }

    private fun createDiagnosticSession(
        store: AnalysisDiagnosticsStore?,
        mealEntity: MealLogEntity,
        modelName: String,
        language: String,
        openAiApiKey: String
    ): AnalysisDiagnosticSession? {
        if (store == null) return null
        return try {
            val environment = store.environment()
            AnalysisDiagnosticSession(
                diagnosticId = UUID.randomUUID().toString(),
                mealId = mealEntity.id,
                attempt = store.countByMealId(mealEntity.id) + 1,
                startedAt = System.currentTimeMillis(),
                requestedModel = modelName,
                language = language,
                appPackageName = environment.packageName,
                appVersionName = environment.versionName,
                appVersionCode = environment.versionCode,
                androidRelease = environment.androidRelease,
                androidSdkInt = environment.androidSdkInt,
                secrets = listOf(openAiApiKey)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start diagnostic session: ${e.message}")
            null
        }
    }

    private suspend fun captureDiagnosticValues(
        mealId: String,
        stage: String
    ): DiagnosticValueSnapshot {
        val capturedAt = System.currentTimeMillis()
        return try {
            DiagnosticValueSnapshot.from(
                stage = stage,
                capturedAt = capturedAt,
                meal = dao.getMealWithNutritionById(mealId)
            )
        } catch (e: Exception) {
            DiagnosticValueSnapshot.from(
                stage = stage,
                capturedAt = capturedAt,
                meal = null,
                missingReason = "values_read_failed"
            )
        }
    }

    private suspend fun persistDiagnosticSession(
        store: AnalysisDiagnosticsStore?,
        session: AnalysisDiagnosticSession?,
        mealEntity: MealLogEntity
    ) {
        if (store == null || session == null) return
        try {
            val meal = dao.getMealWithNutritionById(mealEntity.id)
                ?: return
            session.analysisStatus = meal.meal.analysisStatus
            session.errorCode = meal.meal.analysisError
            session.completedAt = System.currentTimeMillis()

            val reportability = meal.analysisReportability()
            if (reportability !is AnalysisReportability.Reportable) {
                return
            }

            val imageSource = mealEntity.imagePath
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it) }
                ?.takeIf { it.isFile }
            val content = DiagnosticReportJson.fromSession(
                session = session,
                reportableReason = reportability.reason,
                imageSource = imageSource,
                atReport = null
            )
            if (store.save(content) == null) {
                Log.w(TAG, "Diagnostic record was not stored for ${session.diagnosticId}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Diagnostic recording failed: ${e.message}")
        }
    }

    private suspend fun duplicateMealFromHistoryInternal(
        sourceMeal: MealWithNutrition,
        capturedAt: Long
    ): MealLogEntity {
        val sourceNutrition = sourceMeal.nutritionResult
            ?: throw IllegalArgumentException("Source meal has no nutrition result")
        val now = System.currentTimeMillis()
        val newMealId = UUID.randomUUID().toString()
        val newNutritionResultId = UUID.randomUUID().toString()

        val newMeal = sourceMeal.meal.copy(
            id = newMealId,
            capturedAt = capturedAt,
            analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED,
            analysisError = null,
            analysisCompletedAt = now,
            needsModelUpdateNotice = false,
            createdAt = now
        )

        dao.insertMeal(newMeal)

        dao.insertNutritionResult(
            sourceNutrition.copy(
                id = newNutritionResultId,
                mealLogId = newMealId,
                createdAt = now
            )
        )

        val itemIdMap = mutableMapOf<String, String>()
        sourceMeal.items.orEmpty().forEach { itemWithNutrients ->
            val sourceItem = itemWithNutrients.mealItem
            val newItemId = UUID.randomUUID().toString()
            itemIdMap[sourceItem.id] = newItemId
            dao.insertMealItem(
                sourceItem.copy(
                    id = newItemId,
                    nutritionResultId = newNutritionResultId
                )
            )
        }

        val copiedTotalNutrients = sourceMeal.nutrients.orEmpty().map { nutrient ->
            nutrient.copy(
                id = UUID.randomUUID().toString(),
                nutritionResultId = newNutritionResultId,
                mealItemId = null
            )
        }

        val copiedItemNutrients = sourceMeal.items.orEmpty().flatMap { itemWithNutrients ->
            val newItemId = itemIdMap[itemWithNutrients.mealItem.id]
                ?: return@flatMap emptyList()
            itemWithNutrients.nutrients.map { nutrient ->
                nutrient.copy(
                    id = UUID.randomUUID().toString(),
                    nutritionResultId = null,
                    mealItemId = newItemId
                )
            }
        }

        val allNutrients = copiedTotalNutrients + copiedItemNutrients
        if (allNutrients.isNotEmpty()) {
            dao.insertNutrients(allNutrients)
        }

        Log.d(TAG, "Duplicated meal from history: ${sourceMeal.meal.id} -> $newMealId")
        return newMeal
    }

    suspend fun deleteMeal(mealId: String) {
        val meal = dao.getMealById(mealId)
        dao.deleteMealById(mealId)
        try {
            diagnosticStore?.deleteByMealId(mealId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete diagnostic records: ${e.message}")
        }
        meal?.imagePath?.takeIf { it.isNotBlank() }?.also { path ->
            try {
                if (dao.countMealsByImagePath(path) > 0) {
                    Log.d(TAG, "Image file retained because it is still referenced: $path")
                    return@also
                }

                val file = File(path)
                if (file.exists()) {
                    val deleted = file.delete()
                    val status = if (deleted) "succeeded" else "failed"
                    Log.d(TAG, "Image file deletion $status: $path")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete image file: $path", e)
            }
        }
    }

    suspend fun getMealById(mealId: String): MealLogEntity? {
        return dao.getMealById(mealId)
    }

    suspend fun getMealItemById(itemId: String): MealItemEntity? {
        return dao.getMealItemById(itemId)
    }

    suspend fun getMealWithNutritionById(mealId: String): MealWithNutrition? {
        return dao.getMealWithNutritionById(mealId)
    }

    suspend fun updatePortionRatio(nutritionResultId: String, newRatio: Double) {
        Log.d(
            TAG,
            "updatePortionRatio called: nutritionResultId=$nutritionResultId, ratio=$newRatio"
        )

        val db = database
        if (db != null) {
            db.withTransaction { updatePortionRatioInternal(nutritionResultId, newRatio) }
        } else {
            updatePortionRatioInternal(nutritionResultId, newRatio)
        }

        Log.d(TAG, "updatePortionRatio completed successfully")
    }

    private suspend fun updatePortionRatioInternal(nutritionResultId: String, newRatio: Double) {
        val currentResult = dao.getNutritionResultById(nutritionResultId)
        val currentRatio = currentResult?.portionRatio ?: 1.0
        // Use scale factor to avoid rounding error accumulation
        val scaleFactor = if (currentRatio > 0) newRatio / currentRatio else newRatio
        Log.d(TAG, "Current ratio: $currentRatio, New ratio: $newRatio, Scale: $scaleFactor")

        dao.updateNutritionResultPortionRatio(nutritionResultId, newRatio)

        val items = dao.getMealItemsByNutritionResultId(nutritionResultId)
        Log.d(TAG, "Updating ${items.size} meal items")

        items.forEach { item ->
            val newCalories = (item.calories * scaleFactor).toInt()

            val match = NUMBER_PATTERN.find(item.quantity)
            val newQuantity = if (match != null) {
                val matchedString = match.groupValues[1]
                val currentValue = matchedString.toDoubleOrNull() ?: 1.0
                val newValue = currentValue * scaleFactor
                val formattedValue = if (newValue == newValue.toInt().toDouble()) {
                    newValue.toInt().toString()
                } else {
                    String.format("%.1f", newValue).removeSuffix(".0")
                }
                item.quantity.replace(matchedString, formattedValue)
            } else {
                item.quantity
            }

            dao.updateMealItem(item.copy(quantity = newQuantity, calories = newCalories))

            val nutrients = dao.getNutrientsByMealItemId(item.id)
            nutrients.forEach { nutrient ->
                dao.updateNutrient(nutrient.copy(amount = nutrient.amount * scaleFactor))
            }
        }

        val allItems = dao.getMealItemsByNutritionResultId(nutritionResultId)
        val totalCalories = allItems.sumOf { it.calories }
        dao.updateNutritionResultCalories(nutritionResultId, totalCalories)
        Log.d(TAG, "Total calories updated to: $totalCalories")
    }
}
