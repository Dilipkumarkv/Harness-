package com.example.domain.tools

import com.example.domain.model.DiffLine
import com.example.domain.model.DiffLineType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Real unified-diff engine.
 *
 * [computeDiff] performs an LCS-based line diff (common prefix/suffix trimmed first, then a
 * dynamic-programming LCS on the remaining region) and emits proper `@@ -a,b +c,d @@` hunks with
 * three lines of context, matching what `git diff` prints.
 *
 * [applyPatch] is the inverse: it parses real unified-diff hunks and applies them to the original
 * text by searching for the hunk's source lines near their expected position, tolerating line
 * drift (preceding hunks that added/removed lines) and whitespace fuzz. It never appends blindly:
 * a hunk that cannot be located is reported as a failure with the hunk number.
 */
object PatchEngine {

    private const val CONTEXT_LINES = 3
    private const val MAX_SEARCH_OFFSET = 200
    private const val MAX_DIFF_CELLS = 2_000_000
    const val NO_NEWLINE_MARKER = "\\ No newline at end of file"

    // ---------------------------------------------------------------------------------------
    // Public results
    // ---------------------------------------------------------------------------------------

    sealed class ApplyResult {
        data class Success(
            val content: String,
            val hunksApplied: Int,
            val maxFuzz: Int,
            val maxOffset: Int
        ) : ApplyResult()

        data class Failure(
            val message: String,
            val hunkNumber: Int? = null
        ) : ApplyResult()
    }

    data class Hunk(
        val oldStart: Int,
        val oldCount: Int,
        val newStart: Int,
        val newCount: Int,
        val lines: List<String>
    )

    // ---------------------------------------------------------------------------------------
    // Diff generation
    // ---------------------------------------------------------------------------------------

    /**
     * Computes a real unified diff between [oldText] and [newText].
     * Header lines (`---`, `+++`, `@@`) come back as [DiffLineType.HEADER].
     */
    fun computeDiff(oldText: String, newText: String, filePath: String): List<DiffLine> {
        val oldDoc = parseDocument(oldText)
        val newDoc = parseDocument(newText)

        val result = mutableListOf<DiffLine>()
        result.add(DiffLine(DiffLineType.HEADER, "--- a/$filePath"))
        result.add(DiffLine(DiffLineType.HEADER, "+++ b/$filePath"))

        val tagged = tag(oldDoc.lines, newDoc.lines)
        if (tagged.none { it.type != DiffLineType.UNCHANGED }) return result

        // Running counts of old/new lines *before* each tagged entry, so hunk headers are exact.
        val oldAt = IntArray(tagged.size + 1)
        val newAt = IntArray(tagged.size + 1)
        var oldRun = 0
        var newRun = 0
        for (i in tagged.indices) {
            oldAt[i] = oldRun
            newAt[i] = newRun
            when (tagged[i].type) {
                DiffLineType.UNCHANGED -> { oldRun++; newRun++ }
                DiffLineType.REMOVED -> oldRun++
                DiffLineType.ADDED -> newRun++
                DiffLineType.HEADER -> Unit
            }
        }
        oldAt[tagged.size] = oldRun
        newAt[tagged.size] = newRun

        val changeIndexes = tagged.indices.filter { tagged[it].type != DiffLineType.UNCHANGED }
        if (changeIndexes.isEmpty()) return result

        // Group changes that are separated by more than 2*context unchanged lines.
        val groups = mutableListOf<IntRange>()
        var groupStart = changeIndexes.first()
        var groupEnd = changeIndexes.first()
        for (i in 1 until changeIndexes.size) {
            val idx = changeIndexes[i]
            if (idx - groupEnd - 1 > 2 * CONTEXT_LINES) {
                groups.add(groupStart..groupEnd)
                groupStart = idx
            }
            groupEnd = idx
        }
        groups.add(groupStart..groupEnd)

        for (group in groups) {
            val spanStart = max(0, group.first - CONTEXT_LINES)
            val spanEnd = min(tagged.size - 1, group.last + CONTEXT_LINES)

            val aStart = oldAt[spanStart]
            val aCount = oldAt[spanEnd + 1] - oldAt[spanStart]
            val bStart = newAt[spanStart]
            val bCount = newAt[spanEnd + 1] - newAt[spanStart]

            val aHeader = if (aCount == 0) "$aStart,0" else "${aStart + 1},$aCount"
            val bHeader = if (bCount == 0) "$bStart,0" else "${bStart + 1},$bCount"
            result.add(DiffLine(DiffLineType.HEADER, "@@ -$aHeader +$bHeader @@"))

            for (i in spanStart..spanEnd) {
                result.add(tagged[i])
            }
        }

        // Same marker GNU diff emits: tells applyPatch the result has no trailing newline.
        if (!newDoc.endsWithNewline) {
            result.add(DiffLine(DiffLineType.HEADER, NO_NEWLINE_MARKER))
        }

        return result
    }

    /** Formats [DiffLine]s back into a standard unified-diff patch string. */
    fun formatPatchString(diffLines: List<DiffLine>): String =
        diffLines.joinToString("\n") { it.content }

    /** Parses unified-diff text for display in [com.example.ui.components.DiffViewer]. */
    fun parseDiffLines(patchText: String): List<DiffLine> {
        val out = mutableListOf<DiffLine>()
        var oldLine = 0
        var newLine = 0
        for (raw in patchText.split("\n")) {
            val line = raw.removeSuffix("\r")
            when {
                line.startsWith("---") || line.startsWith("+++") -> {
                    out.add(DiffLine(DiffLineType.HEADER, line))
                }
                line.startsWith("@@") -> {
                    val h = parseHunkHeader(line)
                    if (h != null) {
                        oldLine = h.oldStart
                        newLine = h.newStart
                    }
                    out.add(DiffLine(DiffLineType.HEADER, line))
                }
                line.startsWith("+") -> {
                    out.add(DiffLine(DiffLineType.ADDED, line, newLineNum = newLine))
                    newLine++
                }
                line.startsWith("-") -> {
                    out.add(DiffLine(DiffLineType.REMOVED, line, oldLineNum = oldLine))
                    oldLine++
                }
                line.startsWith("\\") -> out.add(DiffLine(DiffLineType.HEADER, line))
                // git diff metadata (diff --git, index, new file mode, Binary files, …) is not
                // content — render it as a header and do not advance the line counters.
                isDiffMetadata(line) -> out.add(DiffLine(DiffLineType.HEADER, line))
                else -> {
                    out.add(DiffLine(DiffLineType.UNCHANGED, line, oldLine, newLine))
                    oldLine++
                    newLine++
                }
            }
        }
        return out
    }

    // ---------------------------------------------------------------------------------------
    // Patch application
    // ---------------------------------------------------------------------------------------

    /** Parses every `@@ ... @@` hunk in [patchText]. File headers and `diff --git` lines are skipped. */
    fun parseHunks(patchText: String): List<Hunk> {
        val hunks = mutableListOf<Hunk>()
        var current: MutableList<String>? = null
        var oldRemaining = 0
        var newRemaining = 0
        var oldStart = 0
        var oldCount = 0
        var newStart = 0

        fun flush() {
            val lines = current
            if (lines != null) {
                hunks.add(
                    Hunk(
                        oldStart = oldStart,
                        oldCount = oldCount,
                        newStart = newStart,
                        newCount = lines.count { it.startsWith(" ") || it.startsWith("+") },
                        lines = lines.toList()
                    )
                )
            }
            current = null
        }

        for (raw in patchText.split("\n")) {
            val line = raw.removeSuffix("\r")
            when {
                line.startsWith("@@") -> {
                    flush()
                    val h = parseHunkHeader(line)
                    if (h == null) return emptyList()
                    oldStart = h.oldStart
                    oldCount = h.oldCount
                    newStart = h.newStart
                    oldRemaining = h.oldCount
                    newRemaining = h.newCount
                    current = mutableListOf()
                }
                current == null -> Unit // still in the file header preamble
                line.startsWith("\\") -> Unit // "No newline at end of file"
                line.startsWith("+") || line.startsWith("-") || line.startsWith(" ") -> {
                    // Only consume as hunk content while the header's line budget lasts;
                    // anything after that is the next file's header, not this hunk's code.
                    if (oldRemaining <= 0 && newRemaining <= 0) {
                        flush()
                        current = null
                        continue
                    }
                    current?.add(line)
                    if (line.startsWith(" ")) { oldRemaining--; newRemaining-- }
                    else if (line.startsWith("-")) oldRemaining--
                    else newRemaining--
                    if (oldRemaining <= 0 && newRemaining <= 0) {
                        // Hunk complete; a following header must not be swallowed into it.
                        flush()
                    }
                }
                line.isEmpty() -> {
                    // Tolerate trimmed trailing whitespace on context lines.
                    if (oldRemaining > 0 || newRemaining > 0) {
                        current?.add(" ")
                        oldRemaining--; newRemaining--
                        if (oldRemaining <= 0 && newRemaining <= 0) flush()
                    }
                }
                else -> {
                    // Anything else (e.g. "diff --git", "index …", "--- a/…", "+++ b/…") ends the hunk.
                    if (current != null) {
                        flush()
                    }
                }
            }
        }
        flush()
        return hunks
    }

    /**
     * Applies [patchText] to [original].
     *
     * If the patch contains no hunks it is treated as a full-file replacement (the documented
     * "replacement block" form), unless it clearly looks like a malformed diff, in which case the
     * call fails instead of silently overwriting the file.
     */
    fun applyPatch(original: String, patchText: String): ApplyResult {
        if (patchText.isBlank()) return ApplyResult.Failure("Patch is empty.")

        val hunks = parseHunks(patchText)
        if (hunks.isEmpty()) {
            // Trim before sniffing: an indented "@@" is still a malformed diff, not replacement
            // content, and must fail rather than silently overwrite the file.
            val looksLikeDiff = patchText.lineSequence().any {
                val t = it.trimStart()
                t.startsWith("---") || t.startsWith("+++") || t.startsWith("@@") || t.startsWith("diff --git")
            }
            return if (looksLikeDiff) {
                ApplyResult.Failure(
                    "Patch contains file headers but no parsable @@ hunks. " +
                        "Expected lines like: @@ -1,3 +1,4 @@ followed by context, '-' and '+' lines."
                )
            } else {
                ApplyResult.Success(patchText, 0, 0, 0)
            }
        }

        val document = parseDocument(original)
        val buffer = document.lines.toMutableList()
        var drift = 0
        var maxFuzz = 0
        var maxOffset = 0

        hunks.forEachIndexed { index, hunk ->
            val oldLines = hunk.lines
                .filter { it.startsWith(" ") || it.startsWith("-") }
                .map { it.drop(1) }
            val newLines = hunk.lines
                .filter { it.startsWith(" ") || it.startsWith("+") }
                .map { it.drop(1) }

            val expected = if (hunk.oldStart > 0) {
                hunk.oldStart - 1 + drift
            } else {
                expectedInsertionPoint(buffer, hunk)
            }

            val match = findMatch(buffer, oldLines, expected)
                ?: return ApplyResult.Failure(
                    "Hunk #${index + 1} (@@ -${hunk.oldStart},${hunk.oldCount} +${hunk.newStart},${hunk.newCount} @@) " +
                        "does not apply: its ${oldLines.size} source line(s) were not found within " +
                        "$MAX_SEARCH_OFFSET lines of line ${expected + 1}. The file changed since the patch was made.",
                    index + 1
                )

            repeat(oldLines.size) { buffer.removeAt(match.index) }
            buffer.addAll(match.index, newLines)

            drift += newLines.size - oldLines.size
            maxFuzz = max(maxFuzz, match.fuzz)
            maxOffset = max(maxOffset, abs(match.index - expected).coerceAtLeast(0))
        }

        return ApplyResult.Success(
            content = render(Document(buffer, resultEndsWithNewline(document, patchText))),
            hunksApplied = hunks.size,
            maxFuzz = maxFuzz,
            maxOffset = maxOffset
        )
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    private data class Document(val lines: List<String>, val endsWithNewline: Boolean)

    private data class Match(val index: Int, val fuzz: Int)

    private fun parseDocument(text: String): Document {
        if (text.isEmpty()) return Document(emptyList(), false)
        val raw = text.split("\n").map { it.removeSuffix("\r") }
        val trailing = raw.isNotEmpty() && raw.last().isEmpty()
        return Document(if (trailing) raw.dropLast(1) else raw, trailing)
    }

    private fun render(doc: Document): String {
        // An emptied file is 0 bytes, never a single newline.
        if (doc.lines.isEmpty()) return ""
        val body = doc.lines.joinToString("\n")
        return if (doc.endsWithNewline) "$body\n" else body
    }

    /**
     * A hunked patch cannot carry a trailing-newline flag except through the `\ No newline`
     * marker, so its absence means "the file ends with a newline" — the same rule GNU patch uses.
     */
    private fun resultEndsWithNewline(original: Document, patchText: String): Boolean =
        if (parseHunks(patchText).isEmpty()) {
            original.endsWithNewline
        } else {
            !patchText.contains(NO_NEWLINE_MARKER)
        }

    private fun isDiffMetadata(line: String): Boolean =
        line.startsWith("diff --git") ||
            line.startsWith("index ") ||
            line.startsWith("new file mode") ||
            line.startsWith("deleted file mode") ||
            line.startsWith("old mode") ||
            line.startsWith("new mode") ||
            line.startsWith("similarity index") ||
            line.startsWith("dissimilarity index") ||
            line.startsWith("rename from") ||
            line.startsWith("rename to") ||
            line.startsWith("copy from") ||
            line.startsWith("copy to") ||
            line.startsWith("Binary files") ||
            line.startsWith("GIT binary patch")

    /** Returns (oldStart, oldCount, newStart, newCount) from a `@@ -a,b +c,d @@` header. */
    private data class HunkHeader(val oldStart: Int, val oldCount: Int, val newStart: Int, val newCount: Int)

    private fun parseHunkHeader(line: String): HunkHeader? {
        val match = Regex("@@\\s*-(\\d+)(?:,(\\d+))?\\s*\\+(\\d+)(?:,(\\d+))?\\s*@@").find(line)
            ?: return null
        val oldStart = match.groupValues[1].toIntOrNull() ?: return null
        val oldCount = match.groupValues[2].toIntOrNull() ?: 1
        val newStart = match.groupValues[3].toIntOrNull() ?: return null
        val newCount = match.groupValues[4].toIntOrNull() ?: 1
        return HunkHeader(oldStart, oldCount, newStart, newCount)
    }

    /** Pure insertion before any existing line (new file, or `-0,0` hunks). */
    private fun expectedInsertionPoint(buffer: List<String>, hunk: Hunk): Int {
        val firstContext = hunk.lines.firstOrNull { it.startsWith(" ") }
        if (firstContext == null) return 0
        val needle = firstContext.drop(1)
        val idx = buffer.indexOf(needle)
        return if (idx >= 0) idx else 0
    }

    /**
     * Locates [needle] in [buffer], searching outward from [expected] and degrading whitespace
     * strictness. Fuzz 0 = exact, 1 = ignore trailing whitespace, 2 = ignore all whitespace runs.
     */
    private fun findMatch(buffer: List<String>, needle: List<String>, expected: Int): Match? {
        if (needle.isEmpty()) {
            return Match(expected.coerceIn(0, buffer.size), 0)
        }
        if (needle.size > buffer.size) return null

        val anchor = expected.coerceIn(-MAX_SEARCH_OFFSET, buffer.size)
        val limit = min(MAX_SEARCH_OFFSET, buffer.size)
        for (offset in 0..limit) {
            val candidates = if (offset == 0) listOf(anchor) else listOf(anchor + offset, anchor - offset)
            for (pos in candidates) {
                if (pos < 0 || pos + needle.size > buffer.size) continue
                val fuzz = fuzzAt(buffer, pos, needle)
                if (fuzz >= 0) return Match(pos, fuzz)
            }
        }
        return null
    }

    private fun fuzzAt(buffer: List<String>, pos: Int, needle: List<String>): Int {
        var exact = true
        var trailing = true
        var loose = true
        for (i in needle.indices) {
            val have = buffer[pos + i]
            val want = needle[i]
            if (exact && have != want) exact = false
            if (trailing && have.trimEnd() != want.trimEnd()) trailing = false
            if (loose && collapse(have) != collapse(want)) loose = false
            if (!exact && !trailing && !loose) return -1
        }
        return when {
            exact -> 0
            trailing -> 1
            loose -> 2
            else -> -1
        }
    }

    private fun collapse(line: String): String = line.trim().replace(Regex("\\s+"), " ")

    private fun tag(a: List<String>, b: List<String>): List<DiffLine> {
        // Trim the common prefix and suffix so the LCS table stays small.
        var prefix = 0
        while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) prefix++

        var suffix = 0
        while (suffix < (min(a.size, b.size) - prefix) && a[a.size - 1 - suffix] == b[b.size - 1 - suffix]) {
            suffix++
        }

        val out = mutableListOf<DiffLine>()
        var oldNo = 0
        var newNo = 0

        fun unchanged(line: String) {
            oldNo++
            newNo++
            out.add(DiffLine(DiffLineType.UNCHANGED, " $line", oldNo, newNo))
        }

        for (i in 0 until prefix) unchanged(a[i])

        val midA = a.subList(prefix, a.size - suffix)
        val midB = b.subList(prefix, b.size - suffix)

        when {
            midA.isEmpty() && midB.isEmpty() -> Unit
            midA.isEmpty() || midB.isEmpty() -> {
                for (line in midA) {
                    oldNo++
                    out.add(DiffLine(DiffLineType.REMOVED, "-$line", oldNo, null))
                }
                for (line in midB) {
                    newNo++
                    out.add(DiffLine(DiffLineType.ADDED, "+$line", null, newNo))
                }
            }
            midA.size.toLong() * midB.size.toLong() > MAX_DIFF_CELLS -> {
                // Too large for a precise diff: emit one coarse replacement block (still correct).
                for (line in midA) {
                    oldNo++
                    out.add(DiffLine(DiffLineType.REMOVED, "-$line", oldNo, null))
                }
                for (line in midB) {
                    newNo++
                    out.add(DiffLine(DiffLineType.ADDED, "+$line", null, newNo))
                }
            }
            else -> {
                for (op in lcsEditScript(midA, midB)) {
                    when (op.op) {
                        Op.EQUAL -> unchanged(midA[op.aIndex])
                        Op.DELETE -> {
                            oldNo++
                            out.add(DiffLine(DiffLineType.REMOVED, "-${midA[op.aIndex]}", oldNo, null))
                        }
                        Op.INSERT -> {
                            newNo++
                            out.add(DiffLine(DiffLineType.ADDED, "+${midB[op.bIndex]}", null, newNo))
                        }
                    }
                }
            }
        }

        val suffixStartA = a.size - suffix
        for (i in 0 until suffix) unchanged(a[suffixStartA + i])

        return out
    }

    private enum class Op { EQUAL, DELETE, INSERT }

    private data class Edit(val op: Op, val aIndex: Int, val bIndex: Int)

    /** Classic LCS reconstruction. Both inputs are already trimmed of common prefix/suffix. */
    private fun lcsEditScript(a: List<String>, b: List<String>): List<Edit> {
        val n = a.size
        val m = b.size
        if (n == 0) return b.indices.map { Edit(Op.INSERT, 0, it) }
        if (m == 0) return a.indices.map { Edit(Op.DELETE, it, 0) }

        val width = m + 1
        val table = ShortArray((n + 1) * width)
        for (i in 1..n) {
            for (j in 1..m) {
                table[i * width + j] = if (a[i - 1] == b[j - 1]) {
                    (table[(i - 1) * width + j - 1] + 1).toShort()
                } else {
                    max(table[(i - 1) * width + j].toInt(), table[i * width + j - 1].toInt()).toShort()
                }
            }
        }

        val edits = ArrayList<Edit>(n + m)
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            when {
                a[i - 1] == b[j - 1] -> {
                    edits.add(Edit(Op.EQUAL, i - 1, j - 1))
                    i--
                    j--
                }
                table[(i - 1) * width + j] >= table[i * width + j - 1] -> {
                    edits.add(Edit(Op.DELETE, i - 1, j - 1))
                    i--
                }
                else -> {
                    edits.add(Edit(Op.INSERT, i - 1, j - 1))
                    j--
                }
            }
        }
        while (i > 0) {
            edits.add(Edit(Op.DELETE, i - 1, 0))
            i--
        }
        while (j > 0) {
            edits.add(Edit(Op.INSERT, 0, j - 1))
            j--
        }
        edits.reverse()
        return edits
    }
}
