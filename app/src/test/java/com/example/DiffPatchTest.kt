package com.example

import com.example.domain.model.DiffLineType
import com.example.domain.tools.PatchEngine
import org.junit.Assert.*
import org.junit.Test

class DiffPatchTest {

    @Test
    fun `LCS diff - simple change`() {
        val old = "fun a() = 1\nfun b() = 2\nfun c() = 3\n"
        val nw = "fun a() = 1\nfun b() = 22\nfun c() = 3\nfun d() = 4\n"
        val diff = PatchEngine.computeDiff(old, nw, "X.kt")
        assertTrue(diff.any { it.type == DiffLineType.ADDED })
        assertTrue(diff.any { it.type == DiffLineType.REMOVED })
        assertTrue(diff.any { it.type == DiffLineType.UNCHANGED })
        // Should have @@ header
        assertTrue(diff.any { it.type == DiffLineType.HEADER && it.content.contains("@@") })
    }

    @Test
    fun `Patch apply - exact hunk match`() {
        val before = "fun a() = 1\nfun b() = 2\nfun c() = 3\n"
        val after = "fun a() = 1\nfun b() = 22\nfun c() = 3\nfun d() = 4\n"
        val patch = PatchEngine.formatPatchString(PatchEngine.computeDiff(before, after, "X.kt"))

        val result = PatchEngine.applyPatch(before, patch)
        assertTrue(result is PatchEngine.ApplyResult.Success)
        val success = result as PatchEngine.ApplyResult.Success
        assertEquals(after, success.content)
        assertEquals(1, success.hunksApplied)
    }

    @Test
    fun `Patch apply - replacement block (no @@ hunks)`() {
        val before = "old content\n"
        val after = "completely new file content\nwith multiple lines\n"
        val result = PatchEngine.applyPatch(before, after)
        assertTrue(result is PatchEngine.ApplyResult.Success)
        val success = result as PatchEngine.ApplyResult.Success
        assertEquals(after, success.content)
        assertEquals(0, success.hunksApplied)
    }

    @Test
    fun `Patch apply - fuzz tolerance for trailing whitespace`() {
        val before = "line 1\nline 2  \nline 3\n"
        val after = "line 1\nline 2\nline 3\n"
        val patch = PatchEngine.formatPatchString(PatchEngine.computeDiff(before, after, "X.kt"))
        val result = PatchEngine.applyPatch(before, patch)
        assertTrue(result is PatchEngine.ApplyResult.Success)
    }

    @Test
    fun `Patch apply - failure when hunk cannot be located`() {
        val before = "line 1\nline 2\nline 3\n"
        // This hunk expects "line X" which does not exist
        val badPatch = """--- a/X.kt
+++ b/X.kt
@@ -1,3 +1,4 @@
 line 1
-line X
+line 2
+line 3
+line 4
""".trimIndent()
        val result = PatchEngine.applyPatch(before, badPatch)
        assertTrue(result is PatchEngine.ApplyResult.Failure)
    }

    @Test
    fun `WorkspaceFs path safety - absolute path rejected`() {
        val ws = com.example.domain.tools.WorkspaceFs(java.io.File("/tmp/test"))
        var caught = false
        try {
            ws.resolve("/etc/passwd")
        } catch (e: com.example.domain.tools.WorkspaceFs.PathException) {
            caught = true
            assertTrue(e.message!!.contains("escapes"))
        }
        assertTrue(caught)
    }

    @Test
    fun `WorkspaceFs path safety - directory traversal rejected`() {
        val ws = com.example.domain.tools.WorkspaceFs(java.io.File("/tmp/test"))
        var caught = false
        try {
            ws.resolve("../../etc/passwd")
        } catch (e: com.example.domain.tools.WorkspaceFs.PathException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `WorkspaceFs path safety - relative path accepted`() {
        val ws = com.example.domain.tools.WorkspaceFs(java.io.File("/tmp/test"))
        val f = ws.resolve("subdir/file.txt")
        assertTrue(f.path.startsWith(ws.canonicalRoot().path + java.io.File.separator))
    }
}
