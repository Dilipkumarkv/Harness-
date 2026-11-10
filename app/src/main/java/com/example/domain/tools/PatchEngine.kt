package com.example.domain.tools

import com.example.domain.model.DiffLine
import com.example.domain.model.DiffLineType

object PatchEngine {

    /**
     * Computes a unified diff list between old text and new text.
     */
    fun computeDiff(oldText: String, newText: String, filePath: String): List<DiffLine> {
        val oldLines = if (oldText.isEmpty()) emptyList() else oldText.lines()
        val newLines = if (newText.isEmpty()) emptyList() else newText.lines()

        val result = mutableListOf<DiffLine>()
        result.add(DiffLine(DiffLineType.HEADER, "--- a/$filePath"))
        result.add(DiffLine(DiffLineType.HEADER, "+++ b/$filePath"))

        var oldIdx = 0
        var newIdx = 0

        while (oldIdx < oldLines.size || newIdx < newLines.size) {
            if (oldIdx < oldLines.size && newIdx < newLines.size && oldLines[oldIdx] == newLines[newIdx]) {
                result.add(
                    DiffLine(
                        type = DiffLineType.UNCHANGED,
                        content = " " + oldLines[oldIdx],
                        oldLineNum = oldIdx + 1,
                        newLineNum = newIdx + 1
                    )
                )
                oldIdx++
                newIdx++
            } else if (newIdx < newLines.size && (oldIdx >= oldLines.size || !oldLines.contains(newLines[newIdx]))) {
                result.add(
                    DiffLine(
                        type = DiffLineType.ADDED,
                        content = "+" + newLines[newIdx],
                        newLineNum = newIdx + 1
                    )
                )
                newIdx++
            } else if (oldIdx < oldLines.size) {
                result.add(
                    DiffLine(
                        type = DiffLineType.REMOVED,
                        content = "-" + oldLines[oldIdx],
                        oldLineNum = oldIdx + 1
                    )
                )
                oldIdx++
            }
        }

        return result
    }

    /**
     * Formats a list of DiffLines into a standard patch string.
     */
    fun formatPatchString(diffLines: List<DiffLine>): String {
        return diffLines.joinToString("\n") { it.content }
    }

    /**
     * Parses a unified diff or patch text into a list of DiffLine objects.
     */
    fun parseDiffLines(patchText: String): List<DiffLine> {
        val lines = patchText.lines()
        var oldLine = 1
        var newLine = 1
        return lines.map { line ->
            when {
                line.startsWith("---") || line.startsWith("+++") || line.startsWith("@@") -> {
                    DiffLine(DiffLineType.HEADER, line)
                }
                line.startsWith("+") -> {
                    DiffLine(DiffLineType.ADDED, line, newLineNum = newLine++)
                }
                line.startsWith("-") -> {
                    DiffLine(DiffLineType.REMOVED, line, oldLineNum = oldLine++)
                }
                else -> {
                    DiffLine(DiffLineType.UNCHANGED, line, oldLineNum = oldLine++, newLineNum = newLine++)
                }
            }
        }
    }
}
