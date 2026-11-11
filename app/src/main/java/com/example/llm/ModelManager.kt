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

    companion object {
        /** magic(4) + version(4) + tensor_count(8) + metadata_kv_count(8). */
        private const val MIN_GGUF_BYTES = 24L
    }

    private val appContext = context.applicationContext
    private val modelsDir: File
        get() = File(appContext.filesDir, "models").apply { if (!exists()) mkdirs() }

    private val lock = Any()

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

    /**
     * Copies a picked document into private storage via a `.part` temp file, checking the GGUF
     * magic. Never overwrites an existing model: a failed or malicious import must not destroy
     * the model the user already has.
     */
    suspend fun importFrom(uri: Uri, displayName: String? = null): Result<ModelInfo> =
        withContext(Dispatchers.IO) {
            val name = sanitize(displayName ?: "model.gguf")
            val target = File(modelsDir, name)
            if (target.exists()) {
                return@withContext Result.failure(
                    IOException("A model named '$name' already exists. Delete it first or choose a different name.")
                )
            }
            val part = File(modelsDir, "$name.part")
            try {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    part.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                } ?: return@withContext Result.failure(IOException("Could not open the selected file."))

                if (part.length() < MIN_GGUF_BYTES || !hasGgufMagic(part)) {
                    part.delete()
                    return@withContext Result.failure(
                        IOException("That file is not a GGUF model (missing 'GGUF' magic bytes or truncated header).")
                    )
                }
                if (!part.renameTo(target)) {
                    part.delete()
                    return@withContext Result.failure(IOException("Could not finalize the imported model."))
                }
                Result.success(ModelInfo(target.absolutePath, target.name, target.length()))
            } catch (e: Exception) {
                part.delete()
                Result.failure(IOException("Import failed: ${e.message}", e))
            }
        }

    /** Deletes a model, but only if it really lives inside the models directory. */
    fun delete(path: String): Boolean {
        val canonical = try {
            File(path).canonicalFile
        } catch (_: IOException) {
            return false
        }
        val dir = try {
            modelsDir.canonicalFile
        } catch (_: IOException) {
            return false
        }
        if (canonical.parentFile?.path != dir.path || !canonical.isFile) return false
        synchronized(lock) {
            if (path == loadedPath || canonical.path == loadedPath) unloadLocked()
            return canonical.delete()
        }
    }

    /** Loads [path] if it is not already resident. Returns an error message on failure. */
    suspend fun ensureLoaded(path: String, threads: Int, contextSize: Int): String? =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (!LlamaBridge.isAvailable) {
                    return@synchronized "Native library not loaded (${LlamaBridge.unavailableReason}). " +
                        "Build the app with the Android NDK to enable on-device inference."
                }
                if (path.isBlank()) return@synchronized "No model selected. Import a .gguf file first."
                val file = File(path)
                if (!file.isFile) return@synchronized "Model file is missing: $path"
                val inside = try {
                    file.canonicalFile.parentFile?.path == modelsDir.canonicalFile.path
                } catch (_: IOException) {
                    false
                }
                if (!inside) return@synchronized "Model must live in the app's models directory: $path"

                if (loadedHandle != 0L && loadedPath == path) return@synchronized null

                unloadLocked()
                val handle = LlamaBridge.nativeLoadModel(path, threads, contextSize)
                if (handle == 0L) {
                    return@synchronized "llama.cpp could not load ${file.name}. " +
                        "It may be corrupt, or need more RAM than this device has."
                }
                loadedHandle = handle
                loadedPath = path
                null
            }
        }

    /** Blocking generation against the loaded model. Must be called from an IO dispatcher. */
    fun generate(prompt: String, maxTokens: Int, threads: Int, contextSize: Int): String =
        synchronized(lock) {
            val handle = loadedHandle
            if (handle == 0L) "" else LlamaBridge.nativeGenerate(handle, prompt, maxTokens, threads, contextSize)
        }

    fun unload() {
        synchronized(lock) { unloadLocked() }
    }

    private fun unloadLocked() {
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
