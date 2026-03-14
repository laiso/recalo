package so.lai.recalo.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import so.lai.recalo.data.api.AiConfig
import so.lai.recalo.data.api.SessionManager
import so.lai.recalo.data.local.CaroliDatabase
import so.lai.recalo.data.local.entity.NutritionResultEntity
import so.lai.recalo.data.local.model.MealItemWithNutrients
import so.lai.recalo.data.local.model.MealWithNutrition
import so.lai.recalo.data.repository.MealRepository
import so.lai.recalo.health.HealthConnectManager
import so.lai.recalo.ui.components.EditRatioDialog

private val ProteinColor = Color(0xFFF09133)
private val FatColor = Color(0xFFF4BF21)
private val CarbsColor = Color(0xFF8CC63F)
private val CalorieColor = Color(0xFF4CAF50)

enum class ScreenState {
    IDLE,
    ANALYZING,
    RESULT,
    DETAIL
}

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    object Success : SaveState()
    data class Error(val message: String) : SaveState()
}

class HomeViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {
    var currentScreenState by mutableStateOf(ScreenState.IDLE)
    var selectedImageUri by mutableStateOf<Uri?>(null)
    var currentAnalysisResult by mutableStateOf<MealWithNutrition?>(null)
    var selectedMeal by mutableStateOf<MealWithNutrition?>(null)
    var latestMeal by mutableStateOf<MealWithNutrition?>(null)
    var meals by mutableStateOf<List<MealWithNutrition>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isUploading by mutableStateOf(false)
    var isHealthConnectSyncing by mutableStateOf(false)
    var healthConnectMessage by mutableStateOf<String?>(null)
    var authError by mutableStateOf(false)
    var saveState by mutableStateOf<SaveState>(SaveState.Idle)
    var pendingHealthConnectData by mutableStateOf<MealWithNutrition?>(null)
    var hasHealthConnectPermissions by mutableStateOf<Boolean?>(null)
        private set
    private val logTag = "HomeViewModel"
    private lateinit var mealRepository: MealRepository
    private var _healthConnectManager: HealthConnectManager? = null

    val healthConnectManager: HealthConnectManager?
        get() = _healthConnectManager

    val healthConnectPermissions: Set<String>
        get() = _healthConnectManager?.permissions ?: emptySet()

    fun initRepository(context: android.content.Context) {
        if (!this::mealRepository.isInitialized) {
            val database = CaroliDatabase.getDatabase(context)
            mealRepository = MealRepository(dao = database.mealDao(), database = database)
            observeMeals()
        }
    }

    fun initHealthConnect(context: android.content.Context) {
        if (_healthConnectManager == null) {
            _healthConnectManager = HealthConnectManager(context)
            viewModelScope.launch {
                hasHealthConnectPermissions = _healthConnectManager?.hasAllPermissions()
            }
        }
    }

    fun refreshHealthConnectPermissions() {
        viewModelScope.launch {
            hasHealthConnectPermissions = _healthConnectManager?.hasAllPermissions()
        }
    }

    @androidx.annotation.VisibleForTesting
    fun observeMeals() {
        viewModelScope.launch {
            mealRepository.getAllMealsWithNutrition().collect { mealsWithNutrition ->
                meals = mealsWithNutrition
                latestMeal = mealsWithNutrition.firstOrNull()
                Log.d(logTag, "Meals updated: count=${meals.size}")
            }
        }
    }

    fun startAnalysis(uri: Uri) {
        selectedImageUri = uri
        currentScreenState = ScreenState.ANALYZING
    }

    fun uploadImage(context: android.content.Context, uri: Uri, capturedAt: Long? = null) {
        val openaiKey = sessionManager.getOpenAIKey()
        if (openaiKey.isNullOrEmpty()) {
            Log.w(logTag, "Missing OpenAI API key; aborting upload.")
            return
        }

        viewModelScope.launch {
            isUploading = true
            try {
                Log.d(logTag, "Uploading image for analysis")

                val modelName = sessionManager.getModelName()
                val result = mealRepository.uploadAndAnalyzeMeal(
                    context,
                    uri,
                    openaiKey,
                    modelName,
                    capturedAt
                )

                if (result.isSuccess) {
                    Log.d(logTag, "Upload and analysis succeeded")
                    val meal = result.getOrNull()
                    if (meal != null) {
                        val mealWithNutrition = mealRepository.getMealWithNutritionById(meal.id)
                        currentAnalysisResult = mealWithNutrition
                        currentScreenState = ScreenState.RESULT
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.w(logTag, "Upload failed: ${error?.message}")
                    healthConnectMessage = "Analysis failed: ${error?.message}"
                    currentScreenState = ScreenState.IDLE
                }
            } catch (e: Exception) {
                Log.e(logTag, "Upload failed with exception.", e)
                healthConnectMessage = "Error: ${e.message}"
                currentScreenState = ScreenState.IDLE
            } finally {
                isUploading = false
            }
        }
    }

    fun resetToIdle() {
        currentScreenState = ScreenState.IDLE
        selectedImageUri = null
        currentAnalysisResult = null
        selectedMeal = null
        healthConnectMessage = null
        saveState = SaveState.Idle
        pendingHealthConnectData = null
    }

    fun resetSaveState() {
        saveState = SaveState.Idle
    }

    fun resolveImageUrl(imagePath: String?): String? {
        if (imagePath.isNullOrBlank()) return null
        return if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            imagePath
        } else {
            "file://$imagePath"
        }
    }

    fun getNutritionForMeal(mealId: String): NutritionResultEntity? {
        return meals.find { it.meal.id == mealId }?.nutritionResult
    }

    fun logout() {
        sessionManager.clear()
    }

    fun loadMeals() {
        isLoading = true
        viewModelScope.launch {
            isLoading = false
        }
    }

    fun deleteMeal(mealId: String, manager: HealthConnectManager?): Job {
        return viewModelScope.launch {
            try {
                // First delete from local database
                mealRepository.deleteMeal(mealId)
                Log.d(logTag, "Meal deleted locally: $mealId")

                // Then try to delete from Health Connect
                manager?.let {
                    val result = it.deleteNutrition(mealId)
                    if (result.isSuccess) {
                        Log.d(logTag, "Meal deleted from Health Connect: $mealId")
                    } else {
                        Log.w(logTag, "Failed to delete meal from Health Connect: $mealId")
                    }
                }
            } catch (e: Exception) {
                Log.e(logTag, "Delete failed with exception.", e)
            }
        }
    }

    fun editItemQuantity(itemId: String, newRatio: Double): Job {
        return viewModelScope.launch {
            try {
                Log.d(logTag, "editItemQuantity called: itemId=$itemId, ratio=$newRatio")

                // Get the item to find its nutrition result
                val item = mealRepository.getMealItemById(itemId)
                if (item == null) {
                    Log.e(logTag, "Item not found: $itemId")
                    return@launch
                }

                // Update portion ratio for the entire meal
                mealRepository.updatePortionRatio(item.nutritionResultId, newRatio)
                Log.d(logTag, "Portion ratio updated in DB")

                // Force reload of all meals (this will update via Flow)
                loadMeals()

                // Also update current analysis result and selected meal explicitly
                currentAnalysisResult?.meal?.id?.let { mealId ->
                    val updatedMeal = mealRepository.getMealWithNutritionById(mealId)
                    if (updatedMeal != null) {
                        currentAnalysisResult = updatedMeal
                        Log.d(logTag, "currentAnalysisResult refreshed")
                    }
                }

                selectedMeal?.meal?.id?.let { mealId ->
                    val updatedMeal = mealRepository.getMealWithNutritionById(mealId)
                    if (updatedMeal != null) {
                        selectedMeal = updatedMeal
                        Log.d(logTag, "selectedMeal refreshed")
                    }
                }

                Log.d(logTag, "editItemQuantity completed successfully")
            } catch (e: Exception) {
                Log.e(logTag, "editItemQuantity failed", e)
            }
        }
    }

    fun writeToHealthConnect(
        manager: HealthConnectManager,
        mealWithNutrition: MealWithNutrition
    ) {
        viewModelScope.launch {
            isHealthConnectSyncing = true
            saveState = SaveState.Saving
            healthConnectMessage = null

            val meal = mealWithNutrition.meal
            val nutritionResult = mealWithNutrition.nutritionResult ?: return@launch
            val nutrients = mealWithNutrition.nutrients

            Log.d(logTag, "=== Starting Health Connect write ===")
            Log.d(logTag, "Meal ID: ${meal.id}")
            Log.d(logTag, "Meal analysisStatus: ${meal.analysisStatus}")
            Log.d(logTag, "Nutrition calories: ${nutritionResult.calories}")
            Log.d(logTag, "Nutrition confidence: ${nutritionResult.confidence}")
            Log.d(logTag, "Nutrition nutrients count: ${nutrients?.size}")
            nutrients?.forEach { nutrient ->
                Log.d(logTag, "  Nutrient: ${nutrient.name} = ${nutrient.amount} ${nutrient.unit}")
            }

            try {
                val result = manager.writeNutrition(mealWithNutrition)
                if (result.isSuccess) {
                    saveState = SaveState.Success
                    healthConnectMessage = "Saved to Health Connect."
                    Log.d(logTag, "=== Health Connect write SUCCESS ===")

                    // Verify write after saving
                    Log.d(logTag, "Verifying write by reading recent data...")
                    val records = manager.readRecentNutrition()
                    Log.d(logTag, "Found ${records.size} records in Health Connect")
                    records.firstOrNull()?.let { record ->
                        Log.d(logTag, "Latest record: ${record.energy?.inKilocalories} kcal")
                    }

                    // Clear pending data on success
                    pendingHealthConnectData = null
                } else {
                    val message = result.exceptionOrNull()?.message ?: "Failed to save."
                    saveState = SaveState.Error(message)
                    healthConnectMessage = message
                    Log.e(logTag, "=== Health Connect write FAILED: $message ===")
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Health Connect Error."
                saveState = SaveState.Error(errorMsg)
                healthConnectMessage = errorMsg
                Log.e(logTag, "Health Connect write failed with exception.", e)
            } finally {
                isHealthConnectSyncing = false
            }
        }
    }

    fun retryHealthConnectWrite(manager: HealthConnectManager) {
        pendingHealthConnectData?.let { mealWithNutrition ->
            writeToHealthConnect(manager, mealWithNutrition)
        }
    }

    fun prepareHealthConnectData(mealWithNutrition: MealWithNutrition) {
        pendingHealthConnectData = mealWithNutrition
    }

    fun readHealthConnectData(manager: HealthConnectManager) {
        viewModelScope.launch {
            val records = manager.readRecentNutrition()
            healthConnectMessage = if (records.isEmpty()) {
                "No nutrition records found in Health Connect."
            } else {
                val latest = records.first()
                val energy = latest.energy?.inKilocalories
                val protein = latest.protein?.inGrams
                val carbs = latest.totalCarbohydrate?.inGrams
                val fat = latest.totalFat?.inGrams
                val fiber = latest.dietaryFiber?.inGrams
                val sugar = latest.sugar?.inGrams
                val sodium = latest.sodium?.inMilligrams

                Log.d(
                    logTag,
                    "Latest record - E:${energy}kcal, P:${protein}g, C:${carbs}g, F:${fat}g, fiber:${fiber}g, sugar:${sugar}g, Na:${sodium}mg"
                )

                "Found ${records.size} records. Latest: " +
                    "${energy?.toInt()} kcal, " +
                    "protein=${protein?.toInt()}g, " +
                    "carbs=${carbs?.toInt()}g, " +
                    "fat=${fat?.toInt()}g, " +
                    "fiber=${fiber?.toInt()}g"
            }
        }
    }
}

@Composable
fun HomeScreen(
    sessionManager: SessionManager
) {
    val viewModel = remember(sessionManager) { HomeViewModel(sessionManager) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSourceSelection by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var showEditDialog by remember { mutableStateOf<String?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            showSourceSelection = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initRepository(context)
        viewModel.initHealthConnect(context)
        viewModel.loadMeals()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(viewModel.healthConnectPermissions)) {
            viewModel.refreshHealthConnectPermissions()
        }
    }

    var currentDayStartHour by remember { mutableStateOf(sessionManager.getDayStartHour()) }
    var selectedDateOffset by remember { mutableIntStateOf(0) }

    val baseToday = remember(currentDayStartHour) {
        java.time.ZonedDateTime.now().minusHours(currentDayStartHour.toLong()).toLocalDate()
    }
    val targetDate = baseToday.plusDays(selectedDateOffset.toLong())

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            if (sessionManager.getOpenAIKey().isNullOrBlank()) {
                showSettingsDialog = true
            } else {
                val capturedAt = targetDate.atTime(currentDayStartHour, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                viewModel.startAnalysis(it)
                viewModel.uploadImage(context, it, capturedAt)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                if (sessionManager.getOpenAIKey().isNullOrBlank()) {
                    showSettingsDialog = true
                } else {
                    val capturedAt = targetDate.atTime(currentDayStartHour, 0)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    viewModel.startAnalysis(uri)
                    viewModel.uploadImage(context, uri, capturedAt)
                }
            }
        }
    }

    val targetMealsAndSummary by remember(viewModel.meals, targetDate, currentDayStartHour) {
        derivedStateOf {
            val startOfTargetDate = targetDate.atTime(currentDayStartHour, 0).atZone(
                ZoneId.systemDefault()
            ).toInstant().toEpochMilli()
            val endOfTargetDate = startOfTargetDate + 24 * 60 * 60 * 1000L

            val meals = viewModel.meals
                .filter { (it.meal.capturedAt ?: 0L) in startOfTargetDate until endOfTargetDate }

            val calories = meals.sumOf { it.nutritionResult?.calories ?: 0 }
            val protein = meals.sumOf {
                it.nutrients?.find { n ->
                    n.name.contains(
                        "Protein",
                        ignoreCase = true
                    )
                }?.amount ?: 0.0
            }
            val fat = meals.sumOf {
                it.nutrients?.find { n ->
                    n.name.contains(
                        "Fat",
                        ignoreCase = true
                    )
                }?.amount ?: 0.0
            }
            val carbs = meals.sumOf {
                it.nutrients?.find { n ->
                    n.name.contains(
                        "Carbohydrate",
                        ignoreCase = true
                    )
                }?.amount ?: 0.0
            }

            Pair(meals, NutritionSummary(calories, protein, fat, carbs))
        }
    }

    val targetMeals = targetMealsAndSummary.first
    val todaySummary = targetMealsAndSummary.second

    if (showSettingsDialog) {
        SettingsDialog(
            currentKey = sessionManager.getOpenAIKey() ?: "",
            currentLevel = sessionManager.getModelLevel(),
            currentDayStartHour = currentDayStartHour,
            onDismiss = { showSettingsDialog = false },
            onSave = { newKey, newLevel, newStartHour ->
                sessionManager.saveOpenAIKey(newKey)
                sessionManager.saveModelLevel(newLevel)
                sessionManager.saveDayStartHour(newStartHour)
                currentDayStartHour = newStartHour
                showSettingsDialog = false
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            if (viewModel.currentScreenState == ScreenState.IDLE) {
                FloatingActionButton(
                    onClick = {
                        if (sessionManager.getOpenAIKey().isNullOrBlank()) {
                            showSettingsDialog = true
                        } else {
                            if (hasCameraPermission) {
                                showSourceSelection = true
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Meal")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column {
                if (sessionManager.getOpenAIKey().isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().clickable { showSettingsDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "OpenAI API key is not configured. Tap to set up.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = viewModel.currentScreenState,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "ScreenState"
                    ) { state ->
                        when (state) {
                            ScreenState.IDLE -> IdleScreen(
                                summary = todaySummary,
                                targetMeals = targetMeals,
                                targetDate = targetDate,
                                isToday = selectedDateOffset == 0,
                                onPreviousDayClick = { selectedDateOffset-- },
                                onNextDayClick = { selectedDateOffset++ },
                                onMealClick = { meal ->
                                    viewModel.selectedMeal = meal
                                    viewModel.currentScreenState = ScreenState.DETAIL
                                },
                                onLogoutClick = { showSettingsDialog = true },
                                isLoading = viewModel.isLoading
                            )

                            ScreenState.DETAIL -> DetailScreen(
                                mealWithNutrition = viewModel.selectedMeal,
                                onBackClick = { viewModel.resetToIdle() },
                                onEditClick = { // Pass nutrition result ID (meal ID) to edit dialog
                                    viewModel.selectedMeal?.nutritionResultDetails?.nutritionResult?.id?.let { _ ->
                                        // Find first item ID for this nutrition result
                                        viewModel.selectedMeal?.items?.firstOrNull()?.mealItem?.id?.let { itemId ->
                                            showEditDialog = itemId
                                        }
                                    }
                                },
                                onDeleteClick = { mealId ->
                                    scope.launch {
                                        val deleteJob = if (viewModel.hasHealthConnectPermissions == true) {
                                            viewModel.deleteMeal(
                                                mealId,
                                                viewModel.healthConnectManager
                                            )
                                        } else {
                                            viewModel.deleteMeal(mealId, null)
                                        }
                                        // Wait for deletion to complete before resetting to idle
                                        deleteJob.join()
                                        viewModel.resetToIdle()
                                    }
                                }
                            )

                            ScreenState.ANALYZING -> AnalyzingScreen(
                                imageUri = viewModel.selectedImageUri
                            )

                            ScreenState.RESULT -> ResultScreen(
                                mealWithNutrition = viewModel.currentAnalysisResult,
                                hasHealthConnectPermissions = viewModel.hasHealthConnectPermissions,
                                onEditClick = { // Find the first item's ID for this nutrition result
                                    viewModel.currentAnalysisResult?.items?.firstOrNull()?.mealItem?.id?.let { itemId ->
                                        showEditDialog = itemId
                                    }
                                },
                                onSaveClick = {
                                    viewModel.currentAnalysisResult?.let { result ->
                                        val nutrition = result.nutritionResult
                                        if (nutrition != null) {
                                            if (viewModel.hasHealthConnectPermissions == true) {
                                                viewModel.healthConnectManager?.let { manager ->
                                                    viewModel.writeToHealthConnect(
                                                        manager,
                                                        result
                                                    )
                                                }
                                            } else {
                                                permissionLauncher.launch(
                                                    viewModel.healthConnectPermissions
                                                )
                                            }
                                        }
                                    }
                                    viewModel.resetToIdle()
                                },
                                onCancelClick = { // Cancel: delete the analyzed meal and reset to idle
                                    viewModel.currentAnalysisResult?.meal?.id?.let { mealId ->
                                        scope.launch {
                                            val deleteJob = if (viewModel.hasHealthConnectPermissions == true) {
                                                viewModel.deleteMeal(
                                                    mealId,
                                                    viewModel.healthConnectManager
                                                )
                                            } else {
                                                viewModel.deleteMeal(mealId, null)
                                            }
                                            deleteJob.join()
                                            viewModel.resetToIdle()
                                        }
                                    } ?: viewModel.resetToIdle()
                                }
                            )
                        }
                    }
                }
            }

            if (showSourceSelection) {
                AlertDialog(
                    onDismissRequest = { showSourceSelection = false },
                    confirmButton = {
                        TextButton(onClick = { showSourceSelection = false }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    title = {
                        Text(
                            text = "Add Meal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    showSourceSelection = false
                                    val dir = File(context.cacheDir, "images")
                                    dir.mkdirs()
                                    val file = File(
                                        dir,
                                        "capture_${System.currentTimeMillis()}.jpg"
                                    )
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    cameraImageUri = uri
                                    cameraLauncher.launch(uri)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = "Camera",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Take Photo", style = MaterialTheme.typography.titleMedium)
                            }

                            OutlinedButton(
                                onClick = {
                                    showSourceSelection = false
                                    imageLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.PhotoLibrary,
                                    contentDescription = "Gallery",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Choose from Gallery",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                )
            }

            if (showEditDialog != null) {
                // Get current ratio from selected meal or current analysis result
                val currentRatio = viewModel.selectedMeal?.nutritionResultDetails?.nutritionResult?.portionRatio
                    ?: viewModel.currentAnalysisResult?.nutritionResultDetails?.nutritionResult?.portionRatio
                    ?: 1.0

                EditRatioDialog(
                    itemId = showEditDialog!!,
                    currentRatio = currentRatio,
                    onDismiss = { showEditDialog = null },
                    onSave = { itemId, ratio ->
                        viewModel.editItemQuantity(itemId, ratio)
                        showEditDialog = null
                    }
                )
            }
        }
    }
}

data class NutritionSummary(
    val totalCalories: Int,
    val totalProtein: Double,
    val totalFat: Double,
    val totalCarbs: Double
)

@Composable
private fun IdleScreen(
    summary: NutritionSummary,
    targetMeals: List<MealWithNutrition>,
    targetDate: LocalDate,
    isToday: Boolean,
    onPreviousDayClick: () -> Unit,
    onNextDayClick: () -> Unit,
    onMealClick: (MealWithNutrition) -> Unit,
    onLogoutClick: () -> Unit,
    isLoading: Boolean
) {
    val dateText = remember(targetDate, isToday) {
        val formatter = java.time.format.DateTimeFormatter.ofPattern(
            "MMM d (E)",
            java.util.Locale.getDefault()
        )
        formatter.format(targetDate)
    }

    var offsetX by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 50) {
                            onPreviousDayClick()
                        } else if (offsetX < -50 && !isToday) {
                            onNextDayClick()
                        }
                        offsetX = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount
                }
            }
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1B8A5D)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onLogoutClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Settings, contentDescription = "Settings", tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier.padding(
                        top = 24.dp,
                        bottom = 24.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onPreviousDayClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowLeft, contentDescription = "Previous", tint = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        Box(
                            modifier = Modifier.width(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }

                        IconButton(
                            onClick = onNextDayClick, enabled = !isToday,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowRight, contentDescription = "Next",
                                tint = if (isToday) {
                                    Color.White.copy(alpha = 0.3f)
                                } else {
                                    Color.White.copy(
                                        alpha = 0.8f
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (targetMeals.isEmpty()) "-" else "${summary.totalCalories}",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 56.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "kcal",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val totalPFC = summary.totalProtein + summary.totalFat + summary.totalCarbs
                    val (pPct, fPct, cPct) = if (totalPFC > 0) {
                        val p = (summary.totalProtein / totalPFC * 100).toInt()
                        val f = (summary.totalFat / totalPFC * 100).toInt()
                        val c = 100 - p - f
                        Triple("$p%", "$f%", "$c%")
                    } else {
                        Triple("-", "-", "-")
                    }

                    val pVal = if (targetMeals.isEmpty()) "-" else "${summary.totalProtein.toInt()}g"
                    val fVal = if (targetMeals.isEmpty()) "-" else "${summary.totalFat.toInt()}g"
                    val cVal = if (targetMeals.isEmpty()) "-" else "${summary.totalCarbs.toInt()}g"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SummaryNutrientItem(
                            color = ProteinColor,
                            label = "P",
                            value = pVal,
                            percentageStr = pPct
                        )
                        SummaryNutrientItem(
                            color = FatColor,
                            label = "F",
                            value = fVal,
                            percentageStr = fPct
                        )
                        SummaryNutrientItem(
                            color = CarbsColor,
                            label = "C",
                            value = cVal,
                            percentageStr = cPct
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (targetMeals.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(56.dp)) {
                            drawCircle(
                                color = iconColor,
                                style = Stroke(width = 6.dp.toPx())
                            )
                            drawCircle(
                                color = iconColor.copy(alpha = 0.2f),
                                radius = size.minDimension * 0.35f,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (isToday) "No Meals Yet" else "No Data Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isToday) {
                            "Tap the + button to capture your food\nand let AI do the rest."
                        } else {
                            "There are no meal records\nfor this specific date."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        lineHeight = 22.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(targetMeals) { mealWithNutrition ->
                    MealCard(
                        mealWithNutrition = mealWithNutrition,
                        onClick = { onMealClick(mealWithNutrition) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NutrientBadgeIcon(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = (size.value * 0.55).sp,
            fontWeight = FontWeight.Bold,
            lineHeight = (size.value * 0.6).sp
        )
    }
}

@Composable
private fun NutrientBadge(
    color: Color,
    label: String,
    value: String,
    size: Dp = 20.dp
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NutrientBadgeIcon(color = color, label = label, size = size)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun PFCBadges(
    protein: Int,
    fat: Int,
    carbs: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NutrientBadge(color = ProteinColor, label = "P", value = "${protein}g")
        NutrientBadge(color = FatColor, label = "F", value = "${fat}g")
        NutrientBadge(color = CarbsColor, label = "C", value = "${carbs}g")
    }
}

@Composable
private fun SummaryNutrientItem(color: Color, label: String, value: String, percentageStr: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NutrientBadgeIcon(color = color, label = label, size = 22.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = percentageStr,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun MealCard(
    mealWithNutrition: MealWithNutrition,
    onClick: () -> Unit
) {
    val meal = mealWithNutrition.meal
    val nutrition = mealWithNutrition.nutritionResult

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            meal.imagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = "Meal thumbnail",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nutrition?.title ?: "Unknown Meal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // Portion ratio label (simple gray text)
                    nutrition?.let { result ->
                        if (result.portionRatio != 1.0) {
                            Text(
                                text = "${result.portionRatio} x",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${nutrition?.calories ?: 0} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalorieColor,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                mealWithNutrition.nutrients?.let { nutrients ->
                    val p = nutrients.find { it.name.contains("Protein", ignoreCase = true) }?.amount?.toInt() ?: 0
                    val f = nutrients.find { it.name.contains("Fat", ignoreCase = true) }?.amount?.toInt() ?: 0
                    val c = nutrients.find { it.name.contains("Carbohydrate", ignoreCase = true) }?.amount?.toInt() ?: 0

                    PFCBadges(protein = p, fat = f, carbs = c)
                }
            }
        }
    }
}

@Composable
private fun MealNutrientItem(color: Color, label: String, value: String, textColor: Color = color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NutrientBadgeIcon(color = color, label = label, size = 18.dp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DetailScreen(
    mealWithNutrition: MealWithNutrition?,
    onBackClick: () -> Unit,
    onEditClick: ((String) -> Unit)? = null,
    onDeleteClick: (String) -> Unit
) {
    if (mealWithNutrition == null) return

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val meal = mealWithNutrition.meal
    val nutrition = mealWithNutrition.nutritionResult

    val dateTimeText = remember(meal.capturedAt) {
        meal.capturedAt?.let { timestamp ->
            val instant = java.time.Instant.ofEpochMilli(timestamp)
            val dateTime = java.time.LocalDateTime.ofInstant(
                instant,
                java.time.ZoneId.systemDefault()
            )
            val formatter = java.time.format.DateTimeFormatter.ofPattern(
                "MMM d, h:mm a",
                java.util.Locale.getDefault()
            )
            formatter.format(dateTime)
        } ?: "Unknown Date"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = dateTimeText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nutrition?.title ?: "Unknown Meal",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // Portion ratio selector
                    nutrition?.let { result ->
                        AssistChip(
                            onClick = {
                                onEditClick?.invoke(result.mealLogId)
                            },
                            label = { Text("${result.portionRatio} x") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Change portion",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    meal.imagePath?.let { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Meal thumbnail",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${nutrition?.calories ?: 0}",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "kcal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        mealWithNutrition.nutrients?.let { nutrients ->
                            val p = nutrients.find { it.name.contains("Protein", ignoreCase = true) }?.amount?.toInt() ?: 0
                            val f = nutrients.find { it.name.contains("Fat", ignoreCase = true) }?.amount?.toInt() ?: 0
                            val c = nutrients.find {
                                it.name.contains(
                                    "Carbohydrate",
                                    ignoreCase = true
                                )
                            }?.amount?.toInt() ?: 0

                            PFCBadges(protein = p, fat = f, carbs = c)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Detected Items (${mealWithNutrition.items?.size ?: 0})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            mealWithNutrition.items?.let { mealItems ->
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mealItems) { item ->
                        DetectedItemCard(item = item, onEditClick = null)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Meal") },
            text = {
                Text(
                    "Are you sure you want to delete this meal? It will also be removed from Health Connect."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteClick(meal.id)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailNutrientBadge(
    color: Color,
    label: String,
    value: String,
    sublabel: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            NutrientBadgeIcon(color = color, label = label, size = 18.dp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Text(
            text = sublabel,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun DetectedItemCard(
    item: MealItemWithNutrients,
    onEditClick: ((String) -> Unit)? = null
) {
    MealItemCard(item = item, editable = true, onEditClick = onEditClick)
}

@Composable
private fun AnalyzingScreen(
    imageUri: Uri?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        imageUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = "Analyzing meal",
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f)
                            )
                        )
                    )
            )

            ScanningAnimation(
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "AI is analyzing nutrients...",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Please wait a moment",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ScanningAnimation(
    modifier: Modifier = Modifier
) {
    var offset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            offset = 0f
            kotlinx.coroutines.delay(100)
            offset = 1f
            kotlinx.coroutines.delay(100)
        }
    }

    val animatedOffset by animateFloatAsState(
        targetValue = offset,
        animationSpec = tween(1500),
        label = "scan"
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.TopCenter)
                .padding(top = (animatedOffset * 300).dp)
        )
    }
}

@Composable
private fun ResultScreen(
    mealWithNutrition: MealWithNutrition?,
    hasHealthConnectPermissions: Boolean?,
    onEditClick: ((String) -> Unit)? = null,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    if (mealWithNutrition == null) return

    val meal = mealWithNutrition.meal
    val nutrition = mealWithNutrition.nutritionResult
    val items = mealWithNutrition.items
    val nutrients = mealWithNutrition.nutrients

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        meal.imagePath?.let { path ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = "Analyzed meal",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nutrition?.title ?: "Analysis Result",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    // Portion ratio selector
                    nutrition?.let { result ->
                        AssistChip(
                            onClick = {
                                result.mealLogId.let { mealId -> onEditClick?.invoke(mealId) }
                            },
                            label = { Text("${result.portionRatio} x") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Change portion",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    if (hasHealthConnectPermissions == false) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Health Connect permissions required",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                items?.let { items ->
                    if (items.isNotEmpty()) {
                        Text(
                            text = "Detected Items (${items.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        items.forEach { item ->
                            MealItemCard(item = item, editable = false)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NutritionValue(
                        value = "${nutrition?.calories ?: 0}",
                        label = "Calories",
                        unit = "kcal",
                        color = CalorieColor
                    )

                    nutrients?.let { nutrients ->
                        val protein = nutrients.find {
                            it.name.contains(
                                "Protein",
                                ignoreCase = true
                            )
                        }?.amount?.toInt() ?: 0
                        val fat = nutrients.find { it.name.contains("Fat", ignoreCase = true) }?.amount?.toInt() ?: 0
                        val carbs = nutrients.find {
                            it.name.contains(
                                "Carbohydrate",
                                ignoreCase = true
                            )
                        }?.amount?.toInt() ?: 0

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NutrientBadge(
                                color = ProteinColor,
                                label = "P",
                                value = "${protein}g",
                                size = 24.dp
                            )
                            NutrientBadge(
                                color = FatColor,
                                label = "F",
                                value = "${fat}g",
                                size = 24.dp
                            )
                            NutrientBadge(
                                color = CarbsColor,
                                label = "C",
                                value = "${carbs}g",
                                size = 24.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onSaveClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save & Complete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    currentKey: String,
    currentLevel: String,
    currentDayStartHour: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var text by remember { mutableStateOf(currentKey) }
    var selectedLevel by remember { mutableStateOf(currentLevel) }
    var dayStartHourText by remember { mutableStateOf(currentDayStartHour.toString()) }
    val context = LocalContext.current

    AlertDialog(
        modifier = Modifier.heightIn(max = 600.dp),
        onDismissRequest = onDismiss,
        title = { Text("Settings", maxLines = 1) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // API Key section
                Column {
                    Text(
                        "OpenAI API Key",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Text(
                        text = "Get API Key from OpenAI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://platform.openai.com/api-keys")
                                )
                                context.startActivity(intent)
                            }
                    )
                }

                // Model Quality section
                Column {
                    Text(
                        "Analysis Model Quality",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AiConfig.options.forEach { option ->
                        ModelLevelOption(
                            label = option.label,
                            description = option.description,
                            selected = selectedLevel == option.level,
                            onClick = { selectedLevel = option.level }
                        )
                    }
                }

                // Day Start Time section
                Column {
                    Text(
                        "Day Start Time (Hour)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = dayStartHourText,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                val intValue = newValue.toIntOrNull()
                                if (newValue.isEmpty() || (intValue != null && intValue in 0..23)) {
                                    dayStartHourText = newValue
                                }
                            }
                        },
                        label = { Text("Hour (0-23)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hour = dayStartHourText.toIntOrNull() ?: 5
                    onSave(text, selectedLevel, hour)
                },
                enabled = text.isNotBlank() && dayStartHourText.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ModelLevelOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun NutritionValue(
    value: String,
    label: String,
    unit: String,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun MealItemCard(
    item: MealItemWithNutrients,
    editable: Boolean = false,
    onEditClick: ((String) -> Unit)? = null
) {
    val mealItem = item.mealItem
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mealItem.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = mealItem.quantity,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${mealItem.calories} kcal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (editable && onEditClick != null) {
                        IconButton(
                            onClick = { onEditClick(mealItem.id) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Adjust portion",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item.nutrients.let { nutrients ->
                val p = nutrients.find { it.name.contains("Protein", ignoreCase = true) }?.amount?.toInt() ?: 0
                val f = nutrients.find { it.name.contains("Fat", ignoreCase = true) }?.amount?.toInt() ?: 0
                val c = nutrients.find { it.name.contains("Carbohydrate", ignoreCase = true) }?.amount?.toInt() ?: 0

                Spacer(modifier = Modifier.height(8.dp))

                PFCBadges(protein = p, fat = f, carbs = c)
            }
        }
    }
}
