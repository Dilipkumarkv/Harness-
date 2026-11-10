package com.example.domain.settings

import android.content.Context
import androidx.core.content.edit
import com.example.domain.model.HarnessSettings
import com.example.domain.model.LocalToolProtocol
import com.example.domain.model.ProviderType

/**
 * Persists [HarnessSettings] in the app's private SharedPreferences.
 *
 * The API key is written to `/data/data/<pkg>/shared_prefs/`, which other apps cannot read
 * without root.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("harness_settings", Context.MODE_PRIVATE)

    fun load(): HarnessSettings {
        val defaults = HarnessSettings()
        return HarnessSettings(
            providerType = readEnum(KEY_PROVIDER, defaults.providerType),
            localProtocol = readEnum(KEY_PROTOCOL, defaults.localProtocol),
            customApiKey = prefs.getString(KEY_API_KEY, defaults.customApiKey) ?: defaults.customApiKey,
            customEndpointUrl = prefs.getString(KEY_CUSTOM_ENDPOINT, defaults.customEndpointUrl)
                ?: defaults.customEndpointUrl,
            modelName = prefs.getString(KEY_MODEL, defaults.modelName) ?: defaults.modelName,
            maxAutonomousIterations = prefs.getInt(KEY_MAX_STEPS, defaults.maxAutonomousIterations),
            autoApproveTools = prefs.getBoolean(KEY_AUTO_APPROVE, defaults.autoApproveTools),
            gitPath = prefs.getString(KEY_GIT_PATH, defaults.gitPath) ?: defaults.gitPath
        )
    }

    fun save(settings: HarnessSettings) {
        prefs.edit {
            putString(KEY_PROVIDER, settings.providerType.name)
            putString(KEY_PROTOCOL, settings.localProtocol.name)
            putString(KEY_API_KEY, settings.customApiKey)
            putString(KEY_CUSTOM_ENDPOINT, settings.customEndpointUrl)
            putString(KEY_MODEL, settings.modelName)
            putInt(KEY_MAX_STEPS, settings.maxAutonomousIterations)
            putBoolean(KEY_AUTO_APPROVE, settings.autoApproveTools)
            putString(KEY_GIT_PATH, settings.gitPath)
        }
    }

    private inline fun <reified T : Enum<T>> readEnum(key: String, fallback: T): T {
        val stored = prefs.getString(key, null) ?: return fallback
        return try {
            enumValueOf<T>(stored)
        } catch (_: IllegalArgumentException) {
            fallback
        }
    }

    private companion object {
        const val KEY_PROVIDER = "provider_type"
        const val KEY_PROTOCOL = "local_protocol"
        const val KEY_API_KEY = "custom_api_key"
        const val KEY_CUSTOM_ENDPOINT = "custom_endpoint"
        const val KEY_MODEL = "model_name"
        const val KEY_MAX_STEPS = "max_steps"
        const val KEY_AUTO_APPROVE = "auto_approve"
        const val KEY_GIT_PATH = "git_path"
    }
}
