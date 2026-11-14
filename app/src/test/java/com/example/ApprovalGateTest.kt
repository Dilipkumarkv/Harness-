package com.example

import com.example.domain.agent.ApprovalGate
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalGateTest {

    @Test
    fun `approve resolves the pending request with true`() = runTest {
        val gate = ApprovalGate()
        val deferred = async { gate.request("write_file", """{"path":"a"}""") }
        // Let the request reach the pending state before approving.
        while (gate.pending.value == null) delay(1)
        assertEquals("write_file", gate.pending.value?.toolName)
        gate.approve()
        assertTrue(deferred.await())
        assertNull(gate.pending.value)
    }

    @Test
    fun `deny resolves the pending request with false`() = runTest {
        val gate = ApprovalGate()
        val deferred = async { gate.request("shell_exec", """{"command":"gradle assembleDebug"}""") }
        while (gate.pending.value == null) delay(1)
        gate.deny()
        assertFalse(deferred.await())
        assertNull(gate.pending.value)
    }

    @Test
    fun `cancel resolves the pending request with false`() = runTest {
        val gate = ApprovalGate()
        val deferred = async { gate.request("apply_patch", "{}") }
        while (gate.pending.value == null) delay(1)
        gate.cancel()
        assertFalse(deferred.await())
        assertNull(gate.pending.value)
    }
}
