package com.example.domain.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.domain.model.HarnessSettings
import com.example.domain.model.LocalToolProtocol
import com.example.domain.model.ProviderType

/**
 * Persists [HarnessSettings] in the app's private SharedPreferences.
 *
 * The API key is encrypted with an Android Keystore AES/GCM key before it touches disk; legacy
 * plaintext values are migrated on first load. If the Keystore is unavailable the key falls back
 * to the app-private plaintext slot rather than losing the user's credential.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("harness_settings", Context.MODE_PRIVATE)

    private val cipher = SecretCipher()

    fun load(): HarnessSettings {
        val defaults = HarnessSettings()
        return HarnessSettings(
            providerType = readEnum(KEY_PROVIDER, defaults.providerType),
            localProtocol = readEnum(KEY_PROTOCOL, defaults.localProtocol),
            customApiKey = readApiKey(defaults.customApiKey),
            customEndpointUrl = prefs.getString(KEY_CUSTOM_ENDPOINT, defaults.customEndpointUrl)
                ?: defaults.customEndpointUrl,
            modelName = prefs.getString(KEY_MODEL, defaults.modelName) ?: defaults.modelName,
            maxAutonomousIterations = prefs.getInt(KEY_MAX_STEPS, defaults.maxAutonomousIterations),
            autoApproveTools = prefs.getBoolean(KEY_AUTO_APPROVE, defaults.autoApproveTools),
            gitPath = prefs.getString(KEY_GIT_PATH, defaults.gitPath) ?: defaults.gitPath,
            modelPath = prefs.getString(KEY_MODEL_PATH, defaults.modelPath) ?: defaults.modelPath,
            modelThreads = prefs.getInt(KEY_MODEL_THREADS, defaults.modelThreads),
            modelContextSize = prefs.getInt(KEY_MODEL_CTX, defaults.modelContextSize)
        )
    }

    fun save(settings: HarnessSettings) {
        prefs.edit {
            putString(KEY_PROVIDER, settings.providerType.name)
            putString(KEY_PROTOCOL, settings.localProtocol.name)
            writeApiKey(this, settings.customApiKey)
            putString(KEY_CUSTOM_ENDPOINT, settings.customEndpointUrl)
            putString(KEY_MODEL, settings.modelName)
            putInt(KEY_MAX_STEPS, settings.maxAutonomousIterations)
            putBoolean(KEY_AUTO_APPROVE, settings.autoApproveTools)
            putString(KEY_GIT_PATH, settings.gitPath)
            putString(KEY_MODEL_PATH, settings.modelPath)
            putInt(KEY_MODEL_THREADS, settings.modelThreads)
            putInt(KEY_MODEL_CTX, settings.modelContextSize)
        }
    }

    private fun readApiKey(fallback: String): String {
        val encrypted = prefs.getString(KEY_API_KEY_ENC, null)
        if (encrypted != null) {
            val decrypted = cipher.decrypt(encrypted)
            if (decrypted != null) return decrypted
        }
        val plain = prefs.getString(KEY_API_KEY, null) ?: return fallback
        // Migrate a legacy plaintext key into the encrypted slot.
        val migrated = cipher.encrypt(plain)
        if (migrated != null) {
            prefs.edit {
                putString(KEY_API_KEY_ENC, migrated)
                remove(KEY_API_KEY)
            }
        }
        return plain
    }

    private fun writeApiKey(editor: SharedPreferences.Editor, value: String) {
        if (value.isEmpty()) {
            editor.remove(KEY_API_KEY).remove(KEY_API_KEY_ENC)
            return
        }
        val encrypted = cipher.encrypt(value)
        if (encrypted != null) {
            editor.putString(KEY_API_KEY_ENC, encrypted).remove(KEY_API_KEY)
        } else {
            editor.putString(KEY_API_KEY, value).remove(KEY_API_KEY_ENC)
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
        const val KEY_API_KEY_ENC = "custom_api_key_enc"
        const val KEY_CUSTOM_ENDPOINT = "custom_endpoint"
        const val KEY_MODEL = "model_name"
        const val KEY_MAX_STEPS = "max_steps"
        const val KEY_AUTO_APPROVE = "auto_approve"
        const val KEY_GIT_PATH = "git_path"
        const val KEY_MODEL_PATH = "model_path"
        const val KEY_MODEL_THREADS = "model_threads"
        const val KEY_MODEL_CTX = "model_context"
    }
}
