package com.example

import com.example.domain.model.DiffLineType
import com.example.domain.tools.PatchEngine
import com.example.domain.tools.WorkspaceFs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiffPatchTest {

    @get:Rule val temporary = TemporaryFolder()

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
    fun `parseHunks does not swallow the next file header`() {
        val multiFile = """--- a/one.kt
+++ b/one.kt
@@ -1,1 +1,1 @@
-old
+new
--- a/two.kt
+++ b/two.kt
@@ -1,1 +1,1 @@
-a
+b
"""
        val hunks = PatchEngine.parseHunks(multiFile)
        assertEquals(2, hunks.size)
        assertEquals(listOf("-old", "+new"), hunks[0].lines)
        assertEquals(listOf("-a", "+b"), hunks[1].lines)
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
        val ws = WorkspaceFs(temporary.newFolder())
        var caught = false
        try {
            ws.resolve("/etc/passwd")
        } catch (e: WorkspaceFs.PathException) {
            caught = true
            assertTrue(e.message!!.contains("escapes"))
        }
        assertTrue(caught)
    }

    @Test
    fun `WorkspaceFs path safety - directory traversal rejected`() {
        val ws = WorkspaceFs(temporary.newFolder())
        var caught = false
        try {
            ws.resolve("../../etc/passwd")
        } catch (e: WorkspaceFs.PathException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `WorkspaceFs path safety - relative path accepted`() {
        val ws = WorkspaceFs(temporary.newFolder())
        val f = ws.resolve("subdir/file.txt")
        assertTrue(f.path.startsWith(ws.canonicalRoot().path + java.io.File.separator))
    }

    @Test
    fun `WorkspaceFs relativeOf always uses forward slashes`() {
        val ws = WorkspaceFs(temporary.newFolder())
        val nested = java.io.File(ws.root, "sub" + java.io.File.separator + "dir" + java.io.File.separator + "file.txt")
        nested.parentFile.mkdirs()
        nested.writeText("x")
        assertEquals("sub/dir/file.txt", ws.relativeOf(nested))
        assertEquals(listOf("sub/dir/file.txt"), ws.walkFiles())
    }

    @Test
    fun `Patch apply - tolerates line drift from hunks applied earlier`() {
        val original = "a\nb\nc\nd\ne\n"
        val patched = "a\nb\nc\nd2\ne\n"
        val patch = PatchEngine.formatPatchString(PatchEngine.computeDiff(original, patched, "f.kt"))

        // The file grew by two lines before the hunk region; line numbers in the patch no longer fit.
        val grown = "NEW1\nNEW2\na\nb\nc\nd\ne\n"
        val result = PatchEngine.applyPatch(grown, patch)
        assertTrue(result is PatchEngine.ApplyResult.Success)
        assertEquals("NEW1\nNEW2\na\nb\nc\nd2\ne\n", (result as PatchEngine.ApplyResult.Success).content)
    }

    @Test
    fun `Patch apply - round trip without trailing newline`() {
        val before = "fun a() = 1"   // no trailing newline on either side
        val after = "fun a() = 2"
        val patch = PatchEngine.formatPatchString(PatchEngine.computeDiff(before, after, "f.kt"))
        val result = PatchEngine.applyPatch(before, patch)
        assertTrue(result is PatchEngine.ApplyResult.Success)
        assertEquals(after, (result as PatchEngine.ApplyResult.Success).content)
    }

    @Test
    fun `Patch apply - round trip converting to trailing newline`() {
        val before = "fun a() = 1"          // missing trailing newline
        val after = "fun a() = 1\nfun b() = 2\n"
        val patch = PatchEngine.formatPatchString(PatchEngine.computeDiff(before, after, "f.kt"))
        val result = PatchEngine.applyPatch(before, patch)
        assertTrue(result is PatchEngine.ApplyResult.Success)
        assertEquals(after, (result as PatchEngine.ApplyResult.Success).content)
    }
}
