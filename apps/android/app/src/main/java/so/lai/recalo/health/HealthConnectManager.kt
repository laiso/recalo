package so.lai.recalo.health

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneId
import so.lai.recalo.data.local.entity.NutrientEntity
import so.lai.recalo.data.local.model.MealWithNutrition

class HealthConnectManager(private val context: Context) {
    private val logTag = "HealthConnect"
    val providerPackageName: String = resolveProviderPackageName()
        ?: "com.google.android.apps.healthdata"

    val permissions = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class)
    )

    var pendingMeal: MealWithNutrition? = null

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    private fun resolveProviderPackageName(): String? {
        return try {
            val intent = Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS")
            val activities = context.packageManager.queryIntentActivities(intent, 0)
            activities.firstOrNull()?.activityInfo?.packageName
        } catch (e: Exception) {
            Log.w(logTag, "resolveProviderPackageName failed", e)
            null
        }
    }

    fun isAvailable(): Boolean {
        return sdkStatus() == HealthConnectClient.SDK_AVAILABLE
    }

    fun sdkStatus(): Int {
        return HealthConnectClient.getSdkStatus(context, providerPackageName)
    }

    fun statusMessage(): String {
        return when (sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> "Health Connect is available."
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                "Health Connect needs an update."
            HealthConnectClient.SDK_UNAVAILABLE ->
                "Health Connect is not installed or unavailable."
            else -> "Health Connect is unavailable on this device."
        }
    }

    fun getSettingsIntent(): Intent? {
        val intent = Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS")
        val activities = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        if (activities.isEmpty()) {
            return null
        }
        return intent
    }

    fun getManagePermissionsIntent(): Intent? {
        val intent = Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        }
        val activities = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        if (activities.isEmpty()) {
            return null
        }
        return intent
    }

    fun getHealthConnectAppIntent(): Intent? {
        val intent = Intent("android.health.connect.action.BROWSE").apply {
            setPackage(providerPackageName)
        }
        val activities = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        if (activities.isEmpty()) {
            return null
        }
        return intent
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    suspend fun writeNutrition(mealWithNutrition: MealWithNutrition): Result<Unit> {
        val meal = mealWithNutrition.meal
        val nutritionResult = mealWithNutrition.nutritionResult ?: return Result.failure(
            IllegalStateException("No nutrition result")
        )
        val nutrients = mealWithNutrition.nutrients
        Log.d(logTag, "writeNutrition called for meal: ${meal.id}")
        Log.d(logTag, "  calories: ${nutritionResult.calories}")
        Log.d(logTag, "  nutrients: ${nutrients?.size} items")

        return writeNutritionInternal(
            mealId = meal.id,
            calories = nutritionResult.calories?.toDouble(),
            nutrients = nutrients,
            capturedAt = meal.capturedAt,
            analysisCompletedAt = meal.analysisCompletedAt
        )
    }

    private suspend fun writeNutritionInternal(
        mealId: String,
        calories: Double?,
        nutrients: List<*>?,
        capturedAt: Long?,
        analysisCompletedAt: Long?
    ): Result<Unit> {
        val status = sdkStatus()
        Log.d(logTag, "writeNutritionInternal: sdkStatus=$status")
        if (!isAvailable()) {
            Log.e(logTag, "Health Connect not available")
            return Result.failure(IllegalStateException(statusMessage()))
        }

        if (calories == null) {
            Log.e(logTag, "Missing calories")
            return Result.failure(IllegalStateException("Missing calories in nutrition result."))
        }

        Log.d(
            logTag,
            "Parsing timestamp: capturedAt=$capturedAt, analysisCompletedAt=$analysisCompletedAt"
        )
        val instant = parseInstant(capturedAt)
            ?: parseInstant(analysisCompletedAt)
            ?: Instant.now()
        Log.d(logTag, "Using instant: $instant")

        val zoneOffset = ZoneId.systemDefault().rules.getOffset(instant)
        val endTime = instant.plusSeconds(1)

        @Suppress("UNCHECKED_CAST")
        val nutrientList = when (nutrients) {
            is List<*> -> nutrients
            else -> emptyList<Any>()
        }

        Log.d(logTag, "Finding nutrients in list of size: ${nutrientList.size}")
        val protein = nutrientList.findNutrient("protein", "タンパク質")
        val carbohydrates = nutrientList.findNutrient("carbohydrates", "carbs", "炭水化物")
        val fat = nutrientList.findNutrient("fat", "脂質")
        val fiber = nutrientList.findNutrient("fiber", "dietary fiber", "食物繊維")
        val sugar = nutrientList.findNutrient("sugar", "sugars", "糖質")
        val sodium = nutrientList.findNutrient("sodium", "ナトリウム")

        Log.d(logTag, "Parsed nutrients - P:$protein g, C:$carbohydrates g, F:$fat g")

        val record = NutritionRecord(
            metadata = Metadata.manualEntry(
                clientRecordId = mealId,
                clientRecordVersion = 1
            ),
            startTime = instant,
            endTime = endTime,
            startZoneOffset = zoneOffset,
            endZoneOffset = zoneOffset,
            energy = Energy.kilocalories(calories),
            protein = protein?.let { Mass.grams(it) },
            totalCarbohydrate = carbohydrates?.let { Mass.grams(it) },
            totalFat = fat?.let { Mass.grams(it) },
            dietaryFiber = fiber?.let { Mass.grams(it) },
            sugar = sugar?.let { Mass.grams(it) },
            sodium = sodium?.let { Mass.milligrams(it) }
        )

        Log.d(logTag, "Inserting record with $calories kcal")
        client.insertRecords(listOf(record))

        // デバッグログ：保存された栄養素
        val nutrientLog = buildString {
            append("Nutrition saved: calories=$calories kcal")
            protein?.let { append(", protein=${it}g") }
            carbohydrates?.let { append(", carbs=${it}g") }
            fat?.let { append(", fat=${it}g") }
            fiber?.let { append(", fiber=${it}g") }
            sugar?.let { append(", sugar=${it}g") }
            sodium?.let { append(", sodium=${it}mg") }
        }
        Log.d(logTag, nutrientLog)

        return Result.success(Unit)
    }

    suspend fun deleteNutrition(mealId: String): Result<Unit> {
        if (!isAvailable()) {
            Log.e(logTag, "Health Connect not available")
            return Result.failure(IllegalStateException(statusMessage()))
        }

        return try {
            client.deleteRecords(
                recordType = NutritionRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = listOf(mealId)
            )
            Log.d(logTag, "Deleted Health Connect record for meal: $mealId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(logTag, "Failed to delete from Health Connect", e)
            Result.failure(e)
        }
    }

    suspend fun readRecentNutrition(): List<NutritionRecord> {
        if (!isAvailable()) {
            Log.w(logTag, "Health Connect not available")
            return emptyList()
        }

        try {
            val now = Instant.now()
            val oneDayAgo = now.minusSeconds(86400)

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = NutritionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(oneDayAgo, now)
                )
            )

            val records = response.records
            Log.d(logTag, "Read ${records.size} nutrition records from Health Connect")

            records.forEach { record ->
                Log.d(
                    logTag,
                    "NutritionRecord: energy=${record.energy}, protein=${record.protein}, " +
                        "carbs=${record.totalCarbohydrate}, fat=${record.totalFat}"
                )
            }

            return records
        } catch (e: Exception) {
            Log.e(logTag, "Failed to read nutrition records", e)
            return emptyList()
        }
    }

    private fun parseInstant(value: Long?): Instant? {
        return value?.let(Instant::ofEpochMilli)
    }

    private fun List<*>.findNutrient(vararg names: String): Double? {
        return this.firstNotNullOfOrNull { item ->
            when (item) {
                is NutrientEntity -> {
                    if (names.any { item.name.equals(it, ignoreCase = true) }) item.amount else null
                }
                is Map<*, *> -> {
                    val name = item["name"] as? String ?: return@firstNotNullOfOrNull null
                    val amount = item["amount"] as? Number ?: return@firstNotNullOfOrNull null
                    if (names.any { name.equals(it, ignoreCase = true) }) amount.toDouble() else null
                }
                else -> null
            }
        }
    }
}
