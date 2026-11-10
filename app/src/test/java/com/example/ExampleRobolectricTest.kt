package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.agent.ToolCallParser
import com.example.domain.model.DiffLineType
import com.example.domain.tools.PatchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AndroidHarness", appName)
    }

    @Test
    fun `test XML tool call parsing`() {
        val text = """
            <thought>I need to read the manifest file.</thought>
            <tool_call>{"name": "read_file", "arguments": {"path": "app/src/main/AndroidManifest.xml"}}</tool_call>
            I will read the manifest now.
        """.trimIndent()

        val parsed = ToolCallParser.parse(text)
        assertEquals("I need to read the manifest file.", parsed.thought)
        assertNotNull(parsed.toolCall)
        assertEquals("read_file", parsed.toolCall?.name)
    }

    @Test
    fun `test ReAct tool call parsing`() {
        val text = """
            Thought: Checking git status
            Action: git_status
            Action Input: {}
        """.trimIndent()

        val parsed = ToolCallParser.parse(text)
        assertEquals("Checking git status", parsed.thought)
        assertNotNull(parsed.toolCall)
        assertEquals("git_status", parsed.toolCall?.name)
    }

    @Test
    fun `test PatchEngine computeDiff`() {
        val oldCode = "fun hello() = 1"
        val newCode = "fun hello() = 2\nfun world() = true"
        val diff = PatchEngine.computeDiff(oldCode, newCode, "Sample.kt")

        assertTrue(diff.any { it.type == DiffLineType.ADDED })
        assertTrue(diff.any { it.type == DiffLineType.REMOVED })
        assertTrue(diff.any { it.type == DiffLineType.HEADER && it.content.contains("@@") })
    }

    @Test
    fun `test PatchEngine applyPatch roundtrip`() {
        val oldCode = "fun a() = 1\nfun b() = 2\nfun c() = 3\n"
        val newCode = "fun a() = 1\nfun b() = 22\nfun c() = 3\nfun d() = 4\n"
        val patch = PatchEngine.formatPatchString(PatchEngine.computeDiff(oldCode, newCode, "X.kt"))
        val applied = PatchEngine.applyPatch(oldCode, patch)
        assertTrue(applied is com.example.domain.tools.PatchEngine.ApplyResult.Success)
        assertEquals(newCode, (applied as com.example.domain.tools.PatchEngine.ApplyResult.Success).content)
    }

    @Test
    fun `test ToolCallParser JSON block`() {
        val text = """
            Some context
            ```json
            {"tool": "read_file", "arguments": {"path": "foo.kt"}}
            ```
            More text
        """.trimIndent()
        val parsed = ToolCallParser.parse(text)
        assertNotNull(parsed.toolCall)
        assertEquals("read_file", parsed.toolCall?.name)
    }
}
