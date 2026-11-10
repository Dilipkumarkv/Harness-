package com.example.domain.tools

import java.io.File
import java.io.IOException

/**
 * Real filesystem access, scoped to one project directory.
 *
 * Every path the agent supplies is resolved relative to [root] and rejected if it escapes the root
 * (absolute paths, `..` traversal, or a symlink pointing outside). Writes are atomic: content goes
 * to a temporary file in the same directory and is then renamed over the target, so a crash mid-run
 * cannot leave a truncated source file behind.
 */
class WorkspaceFs(val root: File) {

    class PathException(message: String) : Exception(message)
    class IoFailure(message: String) : Exception(message)

    data class Entry(val path: String, val isDirectory: Boolean, val size: Long)
    data class Match(val path: String, val line: Int, val text: String)
    data class WriteInfo(val path: String, val bytes: Int, val created: Boolean)

    companion object {
        const val MAX_READ_BYTES: Int = 512 * 1024
        const val MAX_INDEX_BYTES: Int = 128 * 1024
        const val MAX_SEARCH_FILE_BYTES: Long = 512L * 1024L

        val IGNORED_DIRS: Set<String> = setOf(
            ".git", ".gradle", ".idea", ".kotlin", "build", "node_modules",
            ".cxx", "captures", "__pycache__", ".harness"
        )
    }

    init {
        if (!root.exists()) root.mkdirs()
    }

    fun canonicalRoot(): File = try {
        root.canonicalFile
    } catch (_: IOException) {
        root
    }

    /** Resolves a workspace-relative path, refusing anything outside [root]. */
    fun resolve(relative: String): File {
        val trimmed = relative.trim()
        if (trimmed.startsWith("/") || trimmed.startsWith("\\")) {
            throw PathException("Path must be relative to the workspace root, got '$relative'.")
        }
        if (Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(trimmed)) {
            throw PathException("Path must be relative to the workspace root, got '$relative'.")
        }
        val candidate = if (trimmed.isEmpty() || trimmed == ".") root else File(root, trimmed)
        val canonical = try {
            candidate.canonicalFile
        } catch (e: IOException) {
            throw PathException("Cannot resolve '$relative': ${e.message}")
        }
        val base = canonicalRoot()
        val basePath = base.path
        val targetPath = canonical.path
        if (targetPath != basePath && !targetPath.startsWith(basePath + File.separator)) {
            throw PathException("Path '$relative' escapes the workspace root.")
        }
        return canonical
    }

    fun relativeOf(file: File): String {
        val base = canonicalRoot()
        val canonical = try {
            file.canonicalFile
        } catch (_: IOException) {
            file
        }
        return if (canonical.path == base.path) {
            ""
        } else {
            canonical.path.removePrefix(base.path).removePrefix(File.separator)
        }
    }

    fun exists(relative: String): Boolean = resolve(relative).exists()

    fun readText(relative: String, maxBytes: Int = MAX_READ_BYTES): String {
        val file = resolve(relative)
        if (!file.exists()) throw IoFailure("File not found: '$relative'")
        if (file.isDirectory) throw IoFailure("'$relative' is a directory, not a file.")
        if (file.length() > maxBytes) {
            throw IoFailure("'$relative' is ${file.length()} bytes, larger than the ${maxBytes}-byte read limit.")
        }
        val bytes = try {
            file.readBytes()
        } catch (e: IOException) {
            throw IoFailure("Cannot read '$relative': ${e.message}")
        }
        if (looksBinary(bytes)) {
            throw IoFailure("'$relative' looks like a binary file (contains NUL bytes) and cannot be read as text.")
        }
        return String(bytes, Charsets.UTF_8)
    }

    fun writeText(relative: String, content: String): WriteInfo {
        val target = resolve(relative)
        if (target.isDirectory) throw IoFailure("'$relative' is a directory.")
        val created = !target.exists()
        target.parentFile?.mkdirs()

        val temp = File(target.parentFile ?: root, ".${target.name}.harness-tmp")
        try {
            temp.writeText(content, Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (e: IOException) {
            temp.delete()
            throw IoFailure("Failed to write '$relative': ${e.message}")
        }
        return WriteInfo(relative, content.toByteArray(Charsets.UTF_8).size, created)
    }

    fun delete(relative: String): Boolean {
        val file = resolve(relative)
        if (!file.exists()) return false
        return file.deleteRecursively()
    }

    /** Direct children (or deeper with [depth] > 1) of [relative], ignoring build/VCS dirs. */
    fun listEntries(relative: String = "", depth: Int = 1, limit: Int = 300): List<Entry> {
        val base = resolve(relative)
        if (!base.exists()) throw IoFailure("Directory not found: '${relative.ifEmpty { "." }}'")
        if (!base.isDirectory) throw IoFailure("'$relative' is a file, not a directory.")

        val out = mutableListOf<Entry>()
        fun walk(dir: File, level: Int) {
            if (out.size >= limit) return
            val children = dir.listFiles()?.sortedWith(
                compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
            ) ?: return
            for (child in children) {
                if (out.size >= limit) return
                if (child.isDirectory && child.name in IGNORED_DIRS) continue
                out.add(Entry(relativeOf(child), child.isDirectory, if (child.isDirectory) 0L else child.length()))
                if (child.isDirectory && level < depth) walk(child, level + 1)
            }
        }
        walk(base, 1)
        return out
    }

    /** Every regular file under the root (relative paths), used for indexing and search. */
    fun walkFiles(limit: Int = 5000): List<String> {
        val out = mutableListOf<String>()
        fun walk(dir: File) {
            if (out.size >= limit) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (out.size >= limit) return
                if (child.name in IGNORED_DIRS) continue
                if (child.isDirectory) walk(child) else out.add(relativeOf(child))
            }
        }
        walk(root)
        return out
    }

    fun search(
        query: String,
        include: String? = null,
        maxResults: Int = 100,
        ignoreCase: Boolean = true
    ): List<Match> {
        if (query.isEmpty()) throw IoFailure("Search query is empty.")
        val glob = include?.takeIf { it.isNotBlank() }?.let { globToRegex(it) }
        val results = mutableListOf<Match>()
        walkFiles().forEach { path ->
            if (results.size >= maxResults) return@forEach
            val name = path.substringAfterLast('/')
            val matchesInclude = glob == null ||
                glob.matches(name) ||
                glob.matches(path)
            if (!matchesInclude) return@forEach
            val file = File(root, path)
            if (file.length() > MAX_SEARCH_FILE_BYTES) return@forEach
            val bytes = try {
                file.readBytes()
            } catch (_: IOException) {
                return@forEach
            }
            if (looksBinary(bytes)) return@forEach
            String(bytes, Charsets.UTF_8).split("\n").forEachIndexed { index, line ->
                if (results.size < maxResults && line.contains(query, ignoreCase)) {
                    results.add(Match(path, index + 1, line.trimEnd().take(240)))
                }
            }
        }
        return results
    }

    fun isTextFile(file: File): Boolean = try {
        if (file.length() > MAX_SEARCH_FILE_BYTES) {
            false
        } else {
            !looksBinary(file.readBytes())
        }
    } catch (_: IOException) {
        false
    }

    fun languageFor(path: String): String = when {
        path.endsWith(".kt") || path.endsWith(".kts") -> "kotlin"
        path.endsWith(".java") -> "java"
        path.endsWith(".xml") -> "xml"
        path.endsWith(".json") -> "json"
        path.endsWith(".md") -> "markdown"
        path.endsWith(".gradle") -> "gradle"
        path.endsWith(".yml") || path.endsWith(".yaml") -> "yaml"
        path.endsWith(".toml") -> "toml"
        path.endsWith(".js") || path.endsWith(".mjs") -> "javascript"
        path.endsWith(".ts") -> "typescript"
        path.endsWith(".py") -> "python"
        path.endsWith(".sh") -> "shell"
        path.endsWith(".html") -> "html"
        path.endsWith(".css") -> "css"
        else -> "text"
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val sample = minOf(bytes.size, 8_000)
        for (i in 0 until sample) {
            if (bytes[i] == 0.toByte()) return true
        }
        return false
    }

    private fun globToRegex(glob: String): Regex {
        val pattern = StringBuilder()
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when (c) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        pattern.append(".*")
                        i++
                    } else {
                        pattern.append("[^/]*")
                    }
                }
                '?' -> pattern.append("[^/]")
                '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' ->
                    pattern.append('\\').append(c)
                else -> pattern.append(c)
            }
            i++
        }
        return Regex("^$pattern$")
    }
}
