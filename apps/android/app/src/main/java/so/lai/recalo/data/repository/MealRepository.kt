package so.lai.recalo.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import so.lai.recalo.data.local.CaroliDatabase
import so.lai.recalo.data.local.dao.MealDao
import so.lai.recalo.data.local.entity.MealItemEntity
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.entity.NutrientEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity
import so.lai.recalo.data.local.model.MealWithNutrition
import so.lai.recalo.data.openai.ModelAccessDeniedException
import so.lai.recalo.data.openai.OpenAiService

class MealRepository(
    private val dao: MealDao,
    private val database: CaroliDatabase? = null
) {
    companion object {
        private const val TAG = "MealRepository"
        private val NUMBER_PATTERN = Regex("(\\d+\\.?\\d*)")
    }

    fun getAllMealsWithNutrition(): Flow<List<MealWithNutrition>> {
        return dao.getAllMealsWithNutrition()
    }

    suspend fun uploadAndAnalyzeMeal(
        context: Context,
        imageUri: Uri,
        openAiApiKey: String,
        modelName: String = "gpt-4o-mini",
        capturedAt: Long? = null
    ): Result<MealLogEntity> {
        return try {
            val imageFile = copyImageToInternalStorage(context, imageUri)
            Log.d(TAG, "Image copied to: ${imageFile.absolutePath}")

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

            val openAiService = OpenAiService(apiKey = openAiApiKey)
            val currentLanguage = java.util.Locale.getDefault().displayLanguage
            val analysisResult = openAiService.analyzeNutrition(
                imagePath = imageFile.absolutePath,
                modelName = modelName,
                language = currentLanguage
            )

            if (analysisResult.isSuccess) {
                val nutritionData = analysisResult.getOrNull()
                    ?: return Result.failure(Exception("Analysis succeeded but returned null data"))
                val resultId = UUID.randomUUID().toString()
                val nutritionEntity = NutritionResultEntity(
                    id = resultId,
                    mealLogId = mealId,
                    title = nutritionData.title ?: "Untitled Meal",
                    calories = nutritionData.calories.toInt(),
                    confidence = nutritionData.confidence
                )

                dao.insertNutritionResult(nutritionEntity)
                Log.d(TAG, "Nutrition result inserted for meal: $mealId")

                val allNutrients = mutableListOf<NutrientEntity>()

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
                    dao.insertMealItem(mealItemEntity)

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

                if (allNutrients.isNotEmpty()) {
                    dao.insertNutrients(allNutrients)
                }

                val updatedMeal = mealEntity.copy(
                    analysisStatus = MealLogEntity.AnalysisStatus.COMPLETED,
                    analysisCompletedAt = System.currentTimeMillis(),
                    needsModelUpdateNotice = nutritionData.needsModelUpdateNotice
                )
                dao.updateMeal(updatedMeal)
                Log.d(TAG, "Meal status updated to completed: $mealId")

                Result.success(updatedMeal)
            } else {
                val error = analysisResult.exceptionOrNull()
                Log.e(TAG, "Analysis failed: ${error?.message}")

                val updatedMeal = mealEntity.copy(
                    analysisStatus = MealLogEntity.AnalysisStatus.ERROR,
                    analysisError = when (error) {
                        is ModelAccessDeniedException -> "Model access denied: ${error.requestedModel}"
                        else -> error?.message ?: "Unknown error"
                    }
                )
                dao.updateMeal(updatedMeal)

                Result.failure(error ?: Exception("Analysis failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload and analyze failed", e)
            Result.failure(e)
        }
    }

    private fun copyImageToInternalStorage(context: Context, uri: Uri): File {
        val fileName = "meal_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, "images/$fileName")
        file.parentFile?.mkdirs()

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }

        return file
    }

    suspend fun deleteMeal(mealId: String) {
        val meal = dao.getMealById(mealId)
        dao.deleteMealById(mealId)
        meal?.imagePath?.also { path ->
            try {
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
        Log.d(TAG, "updatePortionRatio called: nutritionResultId=$nutritionResultId, ratio=$newRatio")

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
