package so.lai.recalo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Dual FK: total nutrients have nutritionResultId set (mealItemId=null),
// per-item nutrients have mealItemId set (nutritionResultId=null).
// CASCADE on both FKs ensures cleanup when either parent is deleted.
@Entity(
    tableName = "nutrients",
    foreignKeys = [
        ForeignKey(
            entity = NutritionResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["nutritionResultId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MealItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("nutritionResultId"), Index("mealItemId")]
)
data class NutrientEntity(
    @PrimaryKey val id: String,
    val nutritionResultId: String?,
    val mealItemId: String?,
    val name: String,
    val amount: Double,
    val unit: String
)
