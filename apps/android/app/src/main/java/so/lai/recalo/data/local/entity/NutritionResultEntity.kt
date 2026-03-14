package so.lai.recalo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nutrition_results",
    foreignKeys = [
        ForeignKey(
            entity = MealLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealLogId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mealLogId"], unique = true)]
)
data class NutritionResultEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "mealLogId")
    val mealLogId: String,
    @ColumnInfo(name = "title")
    val title: String? = null,
    @ColumnInfo(name = "calories")
    val calories: Int?,
    @ColumnInfo(name = "confidence")
    val confidence: Double?,
    @ColumnInfo(name = "portionRatio")
    val portionRatio: Double = 1.0,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
)
