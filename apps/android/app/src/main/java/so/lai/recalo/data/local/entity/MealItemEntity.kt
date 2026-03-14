package so.lai.recalo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 個別の食品品目エンティティ
 * 画像から解析された各食品の詳細情報
 */
@Entity(
    tableName = "meal_items",
    foreignKeys = [
        ForeignKey(
            entity = NutritionResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["nutritionResultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("nutritionResultId")]
)
data class MealItemEntity(
    @PrimaryKey val id: String,
    val nutritionResultId: String,
    val name: String,
    val quantity: String,
    val calories: Int
)
