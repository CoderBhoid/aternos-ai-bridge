package com.aternos.bridge.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Repository responsible for securely persisting sensitive configuration (API keys, endpoints, parameters)
 * using AndroidX EncryptedSharedPreferences backed by Android Keystore.
 */
class EncryptedConfigRepository(private val context: Context) {

    private val tag = "EncryptedConfigRepo"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize EncryptedSharedPreferences, falling back to private prefs with warning", e)
            context.getSharedPreferences(FALLBACK_PREFS_FILENAME, Context.MODE_PRIVATE)
        }
    }

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<AppConfig> = _configFlow.asStateFlow()

    init {
        // Broadcast initial load
        _configFlow.value = loadConfig()
    }

    fun getConfig(): AppConfig = _configFlow.value

    fun updateConfig(update: (AppConfig) -> AppConfig) {
        val newConfig = update(_configFlow.value)
        saveConfig(newConfig)
        _configFlow.update { newConfig }
    }

    private fun loadConfig(): AppConfig {
        return try {
            val rawJson = prefs.getString(KEY_APP_CONFIG, null)
            if (rawJson != null) {
                json.decodeFromString<AppConfig>(rawJson)
            } else {
                AppConfig()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error deserializing AppConfig, resetting to defaults", e)
            AppConfig()
        }
    }

    fun saveConfig(config: AppConfig) {
        try {
            val rawJson = json.encodeToString(AppConfig.serializer(), config)
            prefs.edit().putString(KEY_APP_CONFIG, rawJson).apply()
            _configFlow.update { config }
        } catch (e: Exception) {
            Log.e(tag, "Error serializing AppConfig", e)
        }
    }

    companion object {
        private const val PREFS_FILENAME = "aternos_bridge_secure_prefs"
        private const val FALLBACK_PREFS_FILENAME = "aternos_bridge_fallback_prefs"
        private const val KEY_APP_CONFIG = "app_config_json"

        @Volatile
        private var INSTANCE: EncryptedConfigRepository? = null

        fun getInstance(context: Context): EncryptedConfigRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EncryptedConfigRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
