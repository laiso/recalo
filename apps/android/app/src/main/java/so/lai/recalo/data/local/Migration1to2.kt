package so.lai.recalo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add portion_ratio column to nutrition_results table
        db.execSQL(
            "ALTER TABLE nutrition_results ADD COLUMN portionRatio REAL NOT NULL DEFAULT 1.0"
        )

        // Create meal_items table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `meal_items` (
                `id` TEXT NOT NULL,
                `nutritionResultId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `quantity` TEXT NOT NULL,
                `calories` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """
        )

        // Create index for meal_items
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_meal_items_nutritionResultId` ON `meal_items` (`nutritionResultId`)"
        )

        // Create nutrients table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `nutrients` (
                `id` TEXT NOT NULL,
                `nutritionResultId` TEXT,
                `mealItemId` TEXT,
                `name` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `unit` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`nutritionResultId`) REFERENCES `nutrition_results`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`mealItemId`) REFERENCES `meal_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """
        )

        // Create indices for nutrients
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_nutrients_nutritionResultId` ON `nutrients` (`nutritionResultId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_nutrients_mealItemId` ON `nutrients` (`mealItemId`)"
        )
    }
}
