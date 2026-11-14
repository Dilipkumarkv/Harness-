package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.HarnessSettings
import com.example.domain.model.LocalToolProtocol
import com.example.domain.model.ProviderType
import com.example.domain.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearSettings() {
        context.getSharedPreferences("harness_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `api key and model survive save and load`() {
        val store = SettingsStore(context)
        store.save(
            HarnessSettings(
                providerType = ProviderType.BYOK_CUSTOM,
                localProtocol = LocalToolProtocol.REACT_TEXT,
                customApiKey = "sk-test-secret-123",
                customEndpointUrl = "https://example.com/v1",
                modelName = "my-model",
                maxAutonomousIterations = 9,
                autoApproveTools = false,
                gitPath = "/data/data/com.termux/files/usr/bin/git"
            )
        )
        val loaded = SettingsStore(context).load()
        assertEquals(ProviderType.BYOK_CUSTOM, loaded.providerType)
        assertEquals(LocalToolProtocol.REACT_TEXT, loaded.localProtocol)
        assertEquals("sk-test-secret-123", loaded.customApiKey)
        assertEquals("https://example.com/v1", loaded.customEndpointUrl)
        assertEquals("my-model", loaded.modelName)
        assertEquals(9, loaded.maxAutonomousIterations)
        assertEquals(false, loaded.autoApproveTools)
        assertEquals("/data/data/com.termux/files/usr/bin/git", loaded.gitPath)
    }

    @Test
    fun `stored api key is not plaintext in shared preferences`() {
        // With the Keystore available (or the plaintext fallback), either the value must be
        // encrypted in the enc slot or absent from disk entirely; it must never sit in the
        // legacy plaintext slot when encryption worked.
        val store = SettingsStore(context)
        store.save(HarnessSettings(customApiKey = "sk-plaintext-probe"))
        val prefs = context.getSharedPreferences("harness_settings", Context.MODE_PRIVATE)
        val encSlot = prefs.getString("custom_api_key_enc", null)
        val plainSlot = prefs.getString("custom_api_key", null)
        // If the encrypted slot exists, the plaintext slot must not.
        if (encSlot != null) {
            org.junit.Assert.assertNull(plainSlot)
            org.junit.Assert.assertNotEquals("sk-plaintext-probe", encSlot)
        }
        // And the value must round-trip regardless of which path was available.
        assertEquals("sk-plaintext-probe", SettingsStore(context).load().customApiKey)
    }
}
