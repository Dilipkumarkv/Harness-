package com.example.llm

/**
 * JNI entry points into `libharness_llama.so`.
 *
 * The library is only present when the app was built with the Android NDK
 * (see `app/src/main/cpp/CMakeLists.txt`). Loading is attempted once; if it fails the rest of the
 * app keeps working and [unavailableReason] explains why the on-device provider cannot be used.
 */
object LlamaBridge {

    private val loadFailure: String? = try {
        System.loadLibrary("harness_llama")
        null
    } catch (t: Throwable) {
        t.message ?: t.javaClass.simpleName
    }

    val isAvailable: Boolean get() = loadFailure == null

    val unavailableReason: String
        get() = loadFailure ?: "available"

    /** Returns a model handle, or 0 on failure. */
    external fun nativeLoadModel(path: String, nThreads: Int, nCtx: Int): Long

    /** Blocking generation. Call from a background dispatcher. */
    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        nThreads: Int,
        nCtx: Int
    ): String

    external fun nativeFreeModel(handle: Long)
}
