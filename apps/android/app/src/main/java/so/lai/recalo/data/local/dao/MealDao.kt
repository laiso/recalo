package so.lai.recalo.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import so.lai.recalo.data.local.entity.MealItemEntity
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.entity.NutrientEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity
import so.lai.recalo.data.local.model.MealWithNutrition

@Dao
interface MealDao {
    @Transaction
    @Query("SELECT * FROM meal_logs ORDER BY COALESCE(capturedAt, 0) DESC")
    fun getAllMealsWithNutrition(): Flow<List<MealWithNutrition>>

    @Transaction
    @Query("SELECT * FROM meal_logs ORDER BY COALESCE(capturedAt, 0) DESC LIMIT 1")
    suspend fun getLatestMealWithNutrition(): MealWithNutrition?

    @Transaction
    @Query("SELECT * FROM meal_logs WHERE id = :mealId")
    suspend fun getMealWithNutritionById(mealId: String): MealWithNutrition?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMeal(meal: MealLogEntity)

    @Update
    suspend fun updateMeal(meal: MealLogEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNutritionResult(result: NutritionResultEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMealItem(item: MealItemEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNutrients(nutrients: List<NutrientEntity>)

    @Delete
    suspend fun deleteMeal(meal: MealLogEntity)

    @Query("DELETE FROM meal_logs WHERE id = :mealId")
    suspend fun deleteMealById(mealId: String)

    @Query("SELECT * FROM meal_logs WHERE id = :mealId")
    suspend fun getMealById(mealId: String): MealLogEntity?

    @Update
    suspend fun updateMealItem(item: MealItemEntity)

    @Query("SELECT * FROM meal_items WHERE id = :itemId")
    suspend fun getMealItemById(itemId: String): MealItemEntity?

    @Query("SELECT * FROM nutrients WHERE mealItemId = :itemId")
    suspend fun getNutrientsByMealItemId(itemId: String): List<NutrientEntity>

    @Update
    suspend fun updateNutrient(nutrient: NutrientEntity)

    @Query("UPDATE nutrition_results SET calories = :calories WHERE id = :id")
    suspend fun updateNutritionResultCalories(id: String, calories: Int)

    @Query("SELECT * FROM meal_items WHERE nutritionResultId = :nutritionResultId")
    suspend fun getMealItemsByNutritionResultId(nutritionResultId: String): List<MealItemEntity>

    @Query("UPDATE nutrition_results SET portionRatio = :ratio WHERE id = :id")
    suspend fun updateNutritionResultPortionRatio(id: String, ratio: Double)

    @Query("SELECT * FROM nutrition_results WHERE id = :id")
    suspend fun getNutritionResultById(id: String): NutritionResultEntity?

    @Update
    suspend fun updateNutritionResult(result: NutritionResultEntity)
}
