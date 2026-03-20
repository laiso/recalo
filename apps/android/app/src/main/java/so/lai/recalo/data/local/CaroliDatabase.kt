package so.lai.recalo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import so.lai.recalo.data.local.dao.MealDao
import so.lai.recalo.data.local.entity.MealItemEntity
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.entity.NutrientEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity

@Database(
    entities = [
        MealLogEntity::class, NutritionResultEntity::class,
        MealItemEntity::class,
        NutrientEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class CaroliDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao

    companion object {
        @Volatile
        private var INSTANCE: CaroliDatabase? = null

        fun getDatabase(context: Context): CaroliDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CaroliDatabase::class.java,
                    "caroli_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemoryDatabase(context: Context): CaroliDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                CaroliDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
