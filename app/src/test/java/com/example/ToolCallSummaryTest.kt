package com.example

import com.example.domain.agent.ToolCallSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallSummaryTest {

    @Test
    fun `write_file summary includes path and size`() {
        val summary = ToolCallSummary.describe(
            "write_file",
            """{"path": "app/src/Main.kt", "content": "hello world"}"""
        )
        assertTrue(summary.contains("app/src/Main.kt"))
        assertTrue(summary.contains("11 B"))
    }

    @Test
    fun `apply_patch summary counts hunks`() {
        val patch = "@@ -1,1 +1,1 @@\n-a\n+b\n@@ -9,1 +9,1 @@\n-x\n+y\n"
        val args = """{"path": "f", "patch": "$patch"}"""
        val summary = ToolCallSummary.describe("apply_patch", args)
        assertTrue(summary.contains("`f`"))
        assertTrue(summary.contains("2 hunks"))
    }

    @Test
    fun `apply_patch summary notes whole-file replacement`() {
        val summary = ToolCallSummary.describe(
            "apply_patch",
            """{"path": "f", "patch": "new content with no hunks"}"""
        )
        assertTrue(summary.contains("whole-file replacement"))
    }

    @Test
    fun `shell_exec summary shows the command`() {
        val summary = ToolCallSummary.describe(
            "shell_exec",
            """{"command": "gradle assembleDebug"}"""
        )
        assertEquals("Run `gradle assembleDebug`", summary)
    }

    @Test
    fun `malformed json degrades gracefully instead of crashing`() {
        assertEquals("Write a file", ToolCallSummary.describe("write_file", "{{{not json"))
        assertEquals("mysterious_tool", ToolCallSummary.describe("mysterious_tool", ""))
    }

    @Test
    fun `escaped quotes in content do not break parsing`() {
        val args = """{"path": "a.kt", "content": "say \"hi\"\n"}"""
        val summary = ToolCallSummary.describe("write_file", args)
        assertTrue(summary.contains("a.kt"))
    }
}
