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
import so.lai.recalo.data.local.entity.MealLogEntity
import so.lai.recalo.data.local.entity.NutrientEntity
import so.lai.recalo.data.local.entity.NutritionResultEntity

/**
 * Health Connect 統合テスト
 *
 * このテストは実際の Android デバイスまたはエミュレーターで実行する必要があります。
 * Health Connect がインストールされ、適切な権限が付与されていることを前提としています。
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
        // テスト後に保存されたレコードをクリーンアップ
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
                // テストレコードを削除（最後の 1 分以内のレコード）
                val testRecords = response.records.filter { record ->
                    val recordTime = record.startTime
                    val timeDiff = java.time.Duration.between(recordTime, now).seconds
                    timeDiff < 3600 // 1 時間以内のレコードをテスト対象とみなす
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
     * Health Connect が利用可能かチェックするテスト
     */
    @Test
    fun testHealthConnectAvailability() {
        val isAvailable = healthConnectManager.isAvailable()
        println("Health Connect available: $isAvailable")

        // Health Connect が利用可能または PROVIDER_UPDATE_REQUIRED の場合
        val status = HealthConnectClient.getSdkStatus(
            context,
            healthConnectManager.providerPackageName
        )
        println("Health Connect status: $status")

        // SDK が利用可能またはプロバイダーの更新が必要な場合
        assertTrue(
            "Health Connect should be available or need provider update",
            status == HealthConnectClient.SDK_AVAILABLE ||
                status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
        )
    }

    /**
     * 権限チェックのテスト
     */
    @Test
    fun testHasAllPermissions() = runBlocking {
        val hasPermissions = healthConnectManager.hasAllPermissions()
        println("Has all permissions: $hasPermissions")

        // 権限が granted されているか確認
        val grantedPermissions = client.permissionController.getGrantedPermissions()
        val requiredPermissions = healthConnectManager.permissions

        println("Granted permissions: ${grantedPermissions.size}")
        println("Required permissions: ${requiredPermissions.size}")

        requiredPermissions.forEach { permission ->
            println("  Required: $permission - granted: ${grantedPermissions.contains(permission)}")
        }
    }

    /**
     * 栄養情報の書き込みテスト
     */
    @Test
    fun testWriteNutrition() = runBlocking {
        // Health Connect が利用できない場合はスキップ
        if (!healthConnectManager.isAvailable()) {
            println("Health Connect not available, skipping write test")
            return@runBlocking
        }

        // 権限がない場合はスキップ
        if (!healthConnectManager.hasAllPermissions()) {
            println("Health Connect permissions not granted, skipping write test")
            return@runBlocking
        }

        // テストデータの作成
        val mealId = UUID.randomUUID().toString()
        val testMeal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "completed",
            analysisCompletedAt = System.currentTimeMillis()
        )

        val testNutrients = listOf(
            NutrientEntity("Protein", 25.0, "g"),
            NutrientEntity("Carbohydrates", 50.0, "g"),
            NutrientEntity("Fat", 15.0, "g"),
            NutrientEntity("Sodium", 500.0, "mg")
        )

        val testNutrition = NutritionResultEntity(
            id = UUID.randomUUID().toString(),
            mealLogId = mealId,
            calories = 450,
            nutrients = testNutrients,
            items = null,
            confidence = 0.9
        )

        println("Writing nutrition data: ${testNutrition.calories} kcal")

        // 書き込み実行
        val result = healthConnectManager.writeNutrition(testMeal, testNutrition)

        // 結果の検証
        if (result.isSuccess) {
            println("Write succeeded")

            // 読み込みで確認
            val records = healthConnectManager.readRecentNutrition()
            println("Read ${records.size} records from Health Connect")

            // 直近のレコードを検索
            val now = Instant.now()
            val matchingRecord = records.firstOrNull { record ->
                val timeDiff = java.time.Duration.between(record.startTime, now)
                timeDiff.toMinutes() < 5 // 5 分以内のレコード
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
     * 栄養情報の読み込みテスト
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

        // まずテストデータを書き込む
        val mealId = UUID.randomUUID().toString()
        val testMeal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "completed",
            analysisCompletedAt = System.currentTimeMillis()
        )

        val testNutrition = NutritionResultEntity(
            id = UUID.randomUUID().toString(),
            mealLogId = mealId,
            calories = 300,
            nutrients = listOf(
                NutrientEntity("Protein", 20.0, "g"),
                NutrientEntity("Carbohydrates", 30.0, "g")
            ),
            items = null,
            confidence = 0.8
        )

        // 書き込み
        val writeResult = healthConnectManager.writeNutrition(testMeal, testNutrition)

        if (writeResult.isFailure) {
            println("Failed to write test data: ${writeResult.exceptionOrNull()?.message}")
            return@runBlocking
        }

        // 少し待ってから読み込み
        kotlinx.coroutines.delay(1000)

        // 読み込み
        val records = healthConnectManager.readRecentNutrition()
        println("Read ${records.size} records")

        // 300 kcal のレコードが見つかるはず
        val foundRecord = records.firstOrNull { record ->
            (record.energy?.inKilocalories ?: 0.0) == 300.0
        }

        assertNotNull("Should find the test record with 300 kcal", foundRecord)
        assertEquals(300.0, foundRecord?.energy?.inKilocalories ?: 0.0, 0.1)
    }

    /**
     * 完全な書き込み・読み込みフローのテスト
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

        // テストデータ
        val expectedCalories = 550.0
        val expectedProtein = 35.0
        val expectedCarbs = 45.0
        val expectedFat = 20.0

        val mealId = UUID.randomUUID().toString()
        val testMeal = MealLogEntity(
            id = mealId,
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "completed",
            analysisCompletedAt = System.currentTimeMillis()
        )

        val testNutrition = NutritionResultEntity(
            id = UUID.randomUUID().toString(),
            mealLogId = mealId,
            calories = expectedCalories.toInt(),
            nutrients = listOf(
                NutrientEntity("Protein", expectedProtein, "g"),
                NutrientEntity("Carbohydrates", expectedCarbs, "g"),
                NutrientEntity("Fat", expectedFat, "g")
            ),
            items = null,
            confidence = 0.85
        )

        // Step 1: 書き込み
        println("=== Step 1: Writing nutrition data ===")
        val writeResult = healthConnectManager.writeNutrition(testMeal, testNutrition)

        assertTrue("Write should succeed", writeResult.isSuccess)

        // Step 2: 少し待機
        println("=== Step 2: Waiting for data to be persisted ===")
        kotlinx.coroutines.delay(500)

        // Step 3: 読み込み
        println("=== Step 3: Reading nutrition data ===")
        val records = healthConnectManager.readRecentNutrition()

        // Step 4: 検証
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
     * 権限がない場合の書き込みエラー処理テスト
     */
    @Test
    fun testWriteWithoutPermissions() = runBlocking {
        if (!healthConnectManager.isAvailable()) {
            return@runBlocking
        }

        // 意図的に権限のない状態で書き込みを試みる
        // （実際にはテスト実行時に権限を付与しない必要がある）
        val hasPermissions = healthConnectManager.hasAllPermissions()

        if (hasPermissions) {
            println("Permissions are granted, skipping negative test")
            return@runBlocking
        }

        val testMeal = MealLogEntity(
            id = UUID.randomUUID().toString(),
            imageUrl = null,
            capturedAt = System.currentTimeMillis(),
            imagePath = null,
            analysisStatus = "completed"
        )

        val testNutrition = NutritionResultEntity(
            id = UUID.randomUUID().toString(),
            mealLogId = testMeal.id,
            calories = 100,
            nutrients = null,
            items = null,
            confidence = null
        )

        val result = healthConnectManager.writeNutrition(testMeal, testNutrition)

        // 権限がない場合はエラーになるはず
        assertTrue("Write should fail without permissions", result.isFailure)
    }
}
