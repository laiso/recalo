package so.lai.recalo.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import so.lai.recalo.data.local.entity.MealItemEntity
import so.lai.recalo.data.local.entity.NutrientEntity

data class MealItemWithNutrients(
    @Embedded val mealItem: MealItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "mealItemId"
    )
    val nutrients: List<NutrientEntity>
)
