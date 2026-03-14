package so.lai.recalo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meal_logs",
    indices = [Index(value = ["capturedAt"])]
)
data class MealLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "imageUrl")
    val imageUrl: String?,
    @ColumnInfo(name = "capturedAt")
    val capturedAt: Long?,
    @ColumnInfo(name = "imagePath")
    val imagePath: String?,
    @ColumnInfo(name = "analysisStatus")
    val analysisStatus: String = AnalysisStatus.PENDING,
    @ColumnInfo(name = "analysisError")
    val analysisError: String? = null,
    @ColumnInfo(name = "analysisCompletedAt")
    val analysisCompletedAt: Long? = null,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object AnalysisStatus {
        const val PENDING = "pending"
        const val ANALYZING = "analyzing"
        const val COMPLETED = "completed"
        const val ERROR = "error"
    }
}
