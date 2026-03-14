package so.lai.recalo.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity

data class MealWithNutrition(
    @Embedded val meal: MealLogEntity,

    @Relation(
        entity = NutritionResultEntity::class,
        parentColumn = "id",
        entityColumn = "mealLogId"
    )
    val nutritionResultDetails: NutritionResultWithDetails?
) {
    val nutritionResult: NutritionResultEntity?
        get() = nutritionResultDetails?.nutritionResult

    val items: List<MealItemWithNutrients>?
        get() = nutritionResultDetails?.items

    val nutrients: List<so.lai.recalo.data.local.entity.NutrientEntity>?
        get() = nutritionResultDetails?.nutrients
}
