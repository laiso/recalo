package so.lai.recalo.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(private val context: Context) {
    companion object {
        private const val TAG = "SessionManager"
    }

    data class SessionState(
        val openAIKey: String?
    ) {
        val isLoggedIn: Boolean
            get() = !openAIKey.isNullOrBlank()
    }

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            "caroli_prefs_encrypted",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create EncryptedSharedPreferences, falling back to plain", e)
        context.getSharedPreferences("caroli_prefs", Context.MODE_PRIVATE)
    }
    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshSessionState() }
    private val _sessionState = MutableStateFlow(readSessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun saveOpenAIKey(key: String) {
        val encrypted = SecurityUtils.encrypt(context, key)
        prefs.edit().putString("encrypted_openai_key", encrypted).apply()
        refreshSessionState()
    }

    fun saveModelLevel(level: String) {
        prefs.edit().putString("openai_model_level", level).apply()
        refreshSessionState()
    }

    fun getModelLevel(): String {
        return prefs.getString("openai_model_level", AiConfig.LEVEL_LOW) ?: AiConfig.LEVEL_LOW
    }

    fun getModelName(): String {
        return AiConfig.getModelId(getModelLevel())
    }

    fun saveDayStartHour(hour: Int) {
        prefs.edit().putInt("day_start_hour", hour).apply()
        refreshSessionState()
    }

    fun getDayStartHour(): Int {
        return prefs.getInt("day_start_hour", 5)
    }

    fun getOpenAIKey(): String? {
        val encrypted = prefs.getString("encrypted_openai_key", null) ?: return null
        return try {
            SecurityUtils.decrypt(context, encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt OpenAI key", e)
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
        refreshSessionState()
    }

    private fun readSessionState(): SessionState {
        return SessionState(openAIKey = getOpenAIKey())
    }

    private fun refreshSessionState() {
        _sessionState.value = readSessionState()
    }
}
