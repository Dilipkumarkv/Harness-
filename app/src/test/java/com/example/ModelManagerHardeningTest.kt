package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.llm.ModelManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ModelManagerHardeningTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `delete cannot escape models directory`() {
        val outside = File(context.filesDir, "hardening-secret.txt").apply { writeText("keep") }
        assertFalse(ModelManager(context).delete(outside.path))
        assertEquals("keep", outside.readText())
        outside.delete()
    }

    @Test fun `failed import preserves existing model and removes temporary file`() = runBlocking<Unit> {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val target = File(dir, "hardening.gguf").apply { writeText("original model") }
        val source = File(context.cacheDir, "hardening-invalid.gguf").apply { writeText("GGUF") }
        val result = ModelManager(context).importFrom(Uri.fromFile(source), target.name)
        assertTrue(result.isFailure)
        assertEquals("original model", target.readText())
        assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".part") })
        source.delete()
        target.delete()
    }
}
