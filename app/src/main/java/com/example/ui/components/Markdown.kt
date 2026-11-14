package com.example.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OceanAqua
import com.example.ui.theme.OceanIceWhite

/**
 * Small, dependency-free markdown-ish renderer for assistant messages.
 *
 * Supports the subset models actually emit: fenced code blocks, inline `code`, **bold**, headings,
 * and unordered lists. Everything else renders as plain text. Not a CommonMark implementation and
 * not meant to be one — chat messages do not need one.
 */
object SimpleMarkdown {

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^(\\s{0,4})[-*]\\s+(.*)$")
    private val INLINE_TOKEN = Regex("(`[^`\\n]+`|\\*\\*[^*\\n]+\\*\\*)")

    fun render(text: String): AnnotatedString = buildAnnotatedString {
        var inCode = false
        val lines = text.split("\n")
        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.removeSuffix("\r")
            when {
                line.trimStart().startsWith("```") -> {
                    inCode = !inCode
                }
                inCode -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, color = OceanAqua, fontSize = 12.sp)
                ) {
                    append(line)
                }
                else -> {
                    val heading = HEADING.find(line)
                    val bullet = BULLET.matchEntire(line)
                    when {
                        heading != null -> {
                            val level = heading.groupValues[1].length
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = OceanIceWhite,
                                    fontSize = maxOf(13f, 15f - level).sp
                                )
                            ) {
                                appendInline(heading.groupValues[2])
                            }
                        }
                        bullet != null -> {
                            append(bullet.groupValues[1])
                            append("• ")
                            appendInline(bullet.groupValues[2])
                        }
                        else -> appendInline(line)
                    }
                }
            }
            if (index != lines.lastIndex) append('\n')
        }
    }

    private fun AnnotatedString.Builder.appendInline(text: String) {
        var cursor = 0
        for (m in INLINE_TOKEN.findAll(text)) {
            if (m.range.first > cursor) append(text.substring(cursor, m.range.first))
            val part = m.value
            if (part.startsWith("`")) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = OceanAqua, fontSize = 12.sp)) {
                    append(part.substring(1, part.length - 1))
                }
            } else {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(part.substring(2, part.length - 2))
                }
            }
            cursor = m.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

/** Renders markdown content with the app's chat typography. */
@Composable
fun MarkdownText(
    text: String,
    color: androidx.compose.ui.graphics.Color = OceanIceWhite,
    modifier: Modifier = Modifier
) {
    Text(
        text = SimpleMarkdown.render(text),
        color = color,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        modifier = modifier
    )
}
