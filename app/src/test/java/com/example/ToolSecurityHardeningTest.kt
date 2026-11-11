package com.example

import com.example.domain.tools.PatchEngine
import com.example.domain.tools.WorkspaceFs
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ToolSecurityHardeningTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun rejectsTraversalEvenWhenItEndsInsideWorkspace() {
        val ws = WorkspaceFs(temporary.newFolder())
        for (path in listOf("sub/../file", "C:secret", "sub\\..\\file")) {
            assertThrows(WorkspaceFs.PathException::class.java) { ws.resolve(path) }
        }
    }

    @Test fun walksSkipExternalLinksAndCycles() {
        val root = temporary.newFolder()
        val outside = temporary.newFolder()
        File(outside, "secret").writeText("secret")
        File(root, "safe").writeText("safe")
        try {
            Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())
            Files.createSymbolicLink(File(root, "loop").toPath(), root.toPath())
        } catch (e: Exception) { assumeNoException(e) }
        val ws = WorkspaceFs(root)
        assertEquals(listOf("safe"), ws.walkFiles())
        assertEquals(listOf("safe"), ws.listEntries(depth = 6).map { it.path })
        assertTrue(ws.search("secret").isEmpty())
    }

    @Test fun atomicWriteDoesNotClobberPredictableTemporaryFile() {
        val root = temporary.newFolder()
        val sentinel = File(root, ".target.harness-tmp").apply { writeText("keep") }
        val ws = WorkspaceFs(root)
        ws.writeText("target", "first")
        ws.writeText("target", "second")
        assertEquals("second", ws.readText("target"))
        assertEquals("keep", sentinel.readText())
        assertEquals(setOf("target", sentinel.name), root.list()!!.toSet())
    }

    @Test fun malformedIndentedHunkIsNotReplacementContent() {
        assertTrue(PatchEngine.applyPatch("keep\n", "  @@ malformed\n-oops\n+bad") is PatchEngine.ApplyResult.Failure)
    }
}
