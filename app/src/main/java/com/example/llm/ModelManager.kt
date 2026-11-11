package com.example.llm

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import com.example.llm.LlamaBridge

/**
 * Owns the `.gguf` files the user imports and the loaded native model handle.
 *
 * Models are copied out of the content URI into app-private storage because llama.cpp needs a real
 * file path and mmaps it — a `content://` stream cannot be mmapped.
 */
class ModelManager(context: Context) {

    private val appContext = context.applicationContext
    private val modelsDir: File
        get() = File(appContext.filesDir, "models").apply { if (!exists()) mkdirs() }

    @Volatile
    private var loadedHandle: Long = 0L

    @Volatile
    private var loadedPath: String = ""

    data class ModelInfo(val path: String, val name: String, val sizeBytes: Long)

    fun list(): List<ModelInfo> = modelsDir.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
        ?.sortedBy { it.name }
        ?.map { ModelInfo(it.absolutePath, it.name, it.length()) }
        ?: emptyList()

    /** Copies a picked document into private storage, checking the GGUF magic. */
    suspend fun importFrom(uri: Uri, displayName: String? = null): Result<ModelInfo> =
        withContext(Dispatchers.IO) {
            val name = sanitize(displayName ?: "model.gguf")
            val target = File(modelsDir, name)
            try {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                } ?: return@withContext Result.failure(IOException("Could not open the selected file."))

                if (!hasGgufMagic(target)) {
                    target.delete()
                    return@withContext Result.failure(
                        IOException("That file is not a GGUF model (missing 'GGUF' magic bytes).")
                    )
                }
                Result.success(ModelInfo(target.absolutePath, target.name, target.length()))
            } catch (e: Exception) {
                target.delete()
                Result.failure(IOException("Import failed: ${e.message}", e))
            }
        }

    fun delete(path: String): Boolean {
        if (path == loadedPath) unload()
        return File(path).delete()
    }

    /** Loads [path] if it is not already resident. Returns an error message on failure. */
    suspend fun ensureLoaded(path: String, threads: Int, contextSize: Int): String? =
        withContext(Dispatchers.IO) {
            if (!LlamaBridge.isAvailable) {
                return@withContext "Native library not loaded (${LlamaBridge.unavailableReason}). " +
                    "Build the app with the Android NDK to enable on-device inference."
            }
            if (path.isBlank()) return@withContext "No model selected. Import a .gguf file first."
            if (!File(path).isFile) return@withContext "Model file is missing: $path"

            if (loadedHandle != 0L && loadedPath == path) return@withContext null

            unload()
            val handle = LlamaBridge.nativeLoadModel(path, threads, contextSize)
            if (handle == 0L) {
                return@withContext "llama.cpp could not load ${File(path).name}. " +
                    "It may be corrupt, or need more RAM than this device has."
            }
            loadedHandle = handle
            loadedPath = path
            null
        }

    /** Blocking generation against the loaded model. Must be called from an IO dispatcher. */
    fun generate(prompt: String, maxTokens: Int, threads: Int, contextSize: Int): String {
        val handle = loadedHandle
        if (handle == 0L) return ""
        return LlamaBridge.nativeGenerate(handle, prompt, maxTokens, threads, contextSize)
    }

    fun unload() {
        val handle = loadedHandle
        if (handle != 0L) {
            LlamaBridge.nativeFreeModel(handle)
            loadedHandle = 0L
            loadedPath = ""
        }
    }

    private fun hasGgufMagic(file: File): Boolean = try {
        file.inputStream().use { stream ->
            val magic = ByteArray(4)
            stream.read(magic) == 4 &&
                magic[0] == 'G'.code.toByte() &&
                magic[1] == 'G'.code.toByte() &&
                magic[2] == 'U'.code.toByte() &&
                magic[3] == 'F'.code.toByte()
        }
    } catch (_: IOException) {
        false
    }

    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_')
        val safe = cleaned.ifBlank { "model.gguf" }
        return if (safe.endsWith(".gguf", ignoreCase = true)) safe else "$safe.gguf"
    }
}
