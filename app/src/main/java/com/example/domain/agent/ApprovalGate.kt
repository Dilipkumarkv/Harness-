package com.example.domain.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Suspends a tool call until the user approves it.
 *
 * Used when `autoApproveTools` is off: the agent loop parks on [request] and the workspace screen
 * shows an approve/deny card. Denying returns `false`, the engine turns that into a normal tool
 * error, and the model sees the refusal as an observation — it is never silently ignored.
 */
class ApprovalGate {

    data class Pending(
        val id: String,
        val toolName: String,
        val argumentsJson: String,
        val reason: String
    )

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    @Volatile
    private var deferred: CompletableDeferred<Boolean>? = null

    /** Returns true when the user approved, false when they denied or the turn was cancelled. */
    suspend fun request(toolName: String, argumentsJson: String): Boolean {
        val gate = CompletableDeferred<Boolean>()
        deferred = gate
        _pending.value = Pending(
            id = UUID.randomUUID().toString(),
            toolName = toolName,
            argumentsJson = argumentsJson,
            reason = reasonFor(toolName)
        )
        return try {
            gate.await()
        } finally {
            _pending.value = null
            deferred = null
        }
    }

    fun approve() {
        deferred?.complete(true)
    }

    fun deny() {
        deferred?.complete(false)
    }

    /** Clears a stale prompt when the agent loop is cancelled. */
    fun cancel() {
        deferred?.complete(false)
        deferred = null
        _pending.value = null
    }

    private fun reasonFor(toolName: String): String = when (toolName) {
        "write_file" -> "will overwrite or create a file on disk"
        "apply_patch" -> "will modify a file on disk"
        "shell_exec" -> "will run a command"
        else -> "is about to run"
    }
}
