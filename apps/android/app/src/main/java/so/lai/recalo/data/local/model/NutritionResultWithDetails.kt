package so.lai.recalo.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import so.lai.recalo.data.local.entity.MealItemEntity
import so.lai.recalo.data.local.entity.NutrientEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity

data class NutritionResultWithDetails(
    @Embedded val nutritionResult: NutritionResultEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "nutritionResultId"
    )
    val nutrients: List<NutrientEntity>,

    @Relation(
        entity = MealItemEntity::class,
        parentColumn = "id",
        entityColumn = "nutritionResultId"
    )
    val items: List<MealItemWithNutrients>
)
