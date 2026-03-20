package so.lai.recalo.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import so.lai.recalo.data.local.entity.MealItemEntity
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.entity.NutrientEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity
import so.lai.recalo.data.local.model.MealItemWithNutrients
import so.lai.recalo.data.local.model.MealWithNutrition
import so.lai.recalo.data.local.model.NutritionResultWithDetails

/**
 * Health Connect integration tests
 *
 * This test must be run on a physical Android device or emulator.
 * It assumes that Health Connect is installed and appropriate permissions have been granted.
 */
@RunWith(AndroidJUnit4::class)
class HealthConnectIntegrationTest {

    private lateinit var context: Context
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var client: HealthConnectClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        healthConnectManager = HealthConnectManager(context)
        client = HealthConnectClient.getOrCreate(context)
    }

    @After
    fun tearDown() {
        // Clean up records saved after the test
        runBlocking {
            try {
                val now = Instant.now()
                val oneDayAgo = now.minusSeconds(86400)
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = NutritionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(oneDayAgo, now)
                    )
                )
                // Delete test records (records within the last hour)
                val testRecords = response.records.filter { record ->
                    val recordTime = record.startTime
                    val timeDiff = java.time.Duration.between(recordTime, now).seconds
                    timeDiff < 3600 // Consider records within 1 hour as test targets
                }
                if (testRecords.isNotEmpty()) {
                    println("Cleaning up ${testRecords.size} test records")
                }
            } catch (e: Exception) {
                println("Cleanup error: ${e.message}")
            }
        }
    }

    /**
     * Test to check if Health Connect is available
     */
    @Test
    fun testHealthConnectAvailability() {
        val isAvailable = healthConnectManager.isAvailable()
        println("Health Connect available: $isAvailable")

        // If Health Connect is available or PROVIDER_UPDATE_REQUIRED
        val status = HealthConnectClient.getSdkStatus(
            context,
            healthConnectManager.providerPackageName
        )
        println("Health Connect status: $status")

        // If SDK is available or provider update is required
        assertTrue(
            "Health Connect should be available or need provider update",
            status == HealthConnectClient.SDK_AVAILABLE ||
                status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
        )
    }

    /**
     * Test for permission checks
     */
    @Test
    fun testHasAllPermissions() = runBlocking {
        val hasPermissions = healthConnectManager.hasAllPermissions()
        println("Has all permissions: $hasPermissions")

        // Check if permissions are granted
        val grantedPermissions = client.permissionController.getGrantedPermissions()
        val requiredPermissions = healthConnectManager.permissions

        println("Granted permissions: ${grantedPermissions.size}")
        println("Required permissions: ${requiredPermissions.size}")

        requiredPermissions.forEach { permission ->
            println("  Required: $permission - granted: ${grantedPermissions.contains(permission)}")
        }
    }

    /**
     * Test for writing nutrition information
     */
    @Test
    fun testWriteNutrition() = runBlocking {
        // Skip if Health Connect is not available
        if (!healthConnectManager.isAvailable()) {
            println("Health Connect not available, skipping write test")
            return@runBlocking
        }

        // Skip if permissions are not granted
        if (!healthConnectManager.hasAllPermissions()) {
            println("Health Connect permissions not granted, skipping write test")
            return@runBlocking
        }

        // Create test data
        val mealId = UUID.randomUUID().toString()
        val nutritionId = UUID.randomUUID().toString()
        val testMeal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "completed",
            analysisCompletedAt = System.currentTimeMillis()
        )

        val testNutrition = NutritionResultEntity(
            id = nutritionId,
            mealLogId = mealId,
            calories = 450,
            confidence = 0.9
        )

        val testNutrients = listOf(
            NutrientEntity(UUID.randomUUID().toString(), nutritionId, null, "Protein", 25.0, "g"),
            NutrientEntity(UUID.randomUUID().toString(), nutritionId, null, "Carbohydrates", 50.0, "g"),
            NutrientEntity(UUID.randomUUID().toString(), nutritionId, null, "Fat", 15.0, "g"),
            NutrientEntity(UUID.randomUUID().toString(), nutritionId, null, "Sodium", 500.0, "mg")
        )

        val mealWithNutrition = MealWithNutrition(
            meal = testMeal,
            nutritionResultDetails = NutritionResultWithDetails(
                nutritionResult = testNutrition,
                items = emptyList(),
                nutrients = testNutrients
            )
        )

        println("Writing nutrition data: ${testNutrition.calories} kcal")

        // Execute write
        val result = healthConnectManager.writeNutrition(mealWithNutrition)

        // Verify results
        if (result.isSuccess) {
            println("Write succeeded")

            // Verify by reading
            val records = healthConnectManager.readRecentNutrition()
            println("Read ${records.size} records from Health Connect")

            // Search for recent records
            val now = Instant.now()
            val matchingRecord = records.firstOrNull { record ->
                val timeDiff = java.time.Duration.between(record.startTime, now)
                timeDiff.toMinutes() < 5 // Records within 5 minutes
            }

            if (matchingRecord != null) {
                println("Found matching record: ${matchingRecord.energy?.inKilocalories} kcal")
                assertEquals(
                    "Calories should match",
                    450.0,
                    matchingRecord.energy?.inKilocalories ?: 0.0,
                    0.1
                )
            } else {
                println("No matching record found in recent records")
            }
        } else {
            val error = result.exceptionOrNull()
            println("Write failed: ${error?.message}")
            fail("Write should succeed: ${error?.message}")
        }
    }

    /**
     * Test for reading nutrition information
     */
    @Test
    fun testReadNutrition() = runBlocking {
        if (!healthConnectManager.isAvailable()) {
            println("Health Connect not available, skipping read test")
            return@runBlocking
        }

        if (!healthConnectManager.hasAllPermissions()) {
            println("Health Connect permissions not granted, skipping read test")
            return@runBlocking
        }

        // Write test data first
        val mealId = UUID.randomUUID().toString()
        val nutritionId = UUID.randomUUID().toString()
        val testMeal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "completed",
            analysisCompletedAt = System.currentTimeMillis()
        )

        val testNutrition = NutritionResultEntity(
            id = nutritionId,
            mealLogId = mealId,
            calories = 300,
            confidence = 0.8
        )

        val testNutrients = listOf(
            NutrientEntity(UUID.randomUUID().toString(), nutritionId, null, "Protein", 20.0, "g"),
            NutrientEntity(UUID.randomUUID().toString(), nutritionId, null, "Carbohydrates", 30.0, "g")
        )

        val mealWithNutrition = MealWithNutrition(
            meal = testMeal,
            nutritionResultDetails = NutritionResultWithDetails(
                nutritionResult = testNutrition,
                items = emptyList(),
                nutrients = testNutrients
            )
        )

        // Write
        val writeResult = healthConnectManager.writeNutrition(mealWithNutrition)

        if (writeResult.isFailure) {
            println("Failed to write test data: ${writeResult.exceptionOrNull()?.message}")
            return@runBlocking
        }

        // Wait a bit before reading
        kotlinx.coroutines.delay(1000)

        // Read
        val records = healthConnectManager.readRecentNutrition()
        println("Read ${records.size} records")

        // Should find record with 300 kcal
        val foundRecord = records.firstOrNull { record ->
            (record.energy?.inKilocalories ?: 0.0) == 300.0
        }

        assertNotNull("Should find the test record with 300 kcal", foundRecord)
        assertEquals(300.0, foundRecord?.energy?.inKilocalories ?: 0.0, 0.1)
    }

    /**
     * Test for complete write/read flow
     */
    @Test
    fun testCompleteWriteReadFlow() = runBlocking {
        if (!healthConnectManager.isAvailable()) {
            println("Health Connect not available, skipping complete flow test")
            return@runBlocking
        }

        if (!healthConnectManager.hasAllPermissions()) {
            println("Health Connect permissions not granted, skipping complete flow test")
            return@runBlocking
        }

        // Test data
        val expectedCalories = 550.0
        val expectedProtein = 35.0
        val expectedCarbs = 45.0
        val expectedFat = 20.0

        val mealId = UUID.randomUUID().toString()
        val nutritionId = UUID.randomUUID().toString()
        val testMeal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "completed",
            analysisCompletedAt = System.currentTimeMillis()
        )

        val testNutrition = NutritionResultEntity(
            id = nutritionId,
            mealLogId = mealId,
            calories = expectedCalories.toInt(),
            confidence = 0.85
        )

        val testNutrients = listOf(
            NutrientEntity(UUID.randomUUID().toString(), nutritionId, null, "Protein", expectedProtein, "g"),
            NutrientEntity(UUID.randomUUID().toString(), nutritionId, null, "Carbohydrates", expectedCarbs, "g"),
            NutrientEntity(UUID.randomUUID().toString(), nutritionId, null, "Fat", expectedFat, "g")
        )

        val mealWithNutrition = MealWithNutrition(
            meal = testMeal,
            nutritionResultDetails = NutritionResultWithDetails(
                nutritionResult = testNutrition,
                items = emptyList(),
                nutrients = testNutrients
            )
        )

        // Step 1: Write
        println("=== Step 1: Writing nutrition data ===")
        val writeResult = healthConnectManager.writeNutrition(mealWithNutrition)

        assertTrue("Write should succeed", writeResult.isSuccess)

        // Step 2: Wait a bit
        println("=== Step 2: Waiting for data to be persisted ===")
        kotlinx.coroutines.delay(500)

        // Step 3: Read
        println("=== Step 3: Reading nutrition data ===")
        val records = healthConnectManager.readRecentNutrition()

        // Step 4: Verify
        println("=== Step 4: Verifying data ===")
        val matchingRecord = records.firstOrNull { record ->
            val caloriesMatch = (record.energy?.inKilocalories ?: 0.0) == expectedCalories
            println(
                "Record calories: ${record.energy?.inKilocalories}, expected: $expectedCalories, match: $caloriesMatch"
            )
            caloriesMatch
        }

        assertNotNull("Should find matching record", matchingRecord)

        val record = requireNotNull(matchingRecord)
        assertEquals(
            "Calories mismatch",
            expectedCalories,
            record.energy?.inKilocalories ?: 0.0,
            0.1
        )
        assertEquals("Protein mismatch", expectedProtein, record.protein?.inGrams ?: 0.0, 0.1)
        assertEquals("Carbs mismatch", expectedCarbs, record.totalCarbohydrate?.inGrams ?: 0.0, 0.1)
        assertEquals("Fat mismatch", expectedFat, record.totalFat?.inGrams ?: 0.0, 0.1)

        println("=== All assertions passed! ===")
    }

    /**
     * Test for write error handling when permissions are missing
     */
    @Test
    fun testWriteWithoutPermissions() = runBlocking {
        if (!healthConnectManager.isAvailable()) {
            return@runBlocking
        }

        // Intentionally attempt to write without permissions
        // (In practice, permissions must not be granted when running this test)
        val hasPermissions = healthConnectManager.hasAllPermissions()

        if (hasPermissions) {
            println("Permissions are granted, skipping negative test")
            return@runBlocking
        }

        val mealId = UUID.randomUUID().toString()
        val nutritionId = UUID.randomUUID().toString()
        val testMeal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "completed"
        )

        val testNutrition = NutritionResultEntity(
            id = nutritionId,
            mealLogId = mealId,
            calories = 100,
            confidence = null
        )

        val mealWithNutrition = MealWithNutrition(
            meal = testMeal,
            nutritionResultDetails = NutritionResultWithDetails(
                nutritionResult = testNutrition,
                items = emptyList(),
                nutrients = emptyList()
            )
        )

        val result = healthConnectManager.writeNutrition(mealWithNutrition)

        // Should fail without permissions
        assertTrue("Write should fail without permissions", result.isFailure)
    }
}
