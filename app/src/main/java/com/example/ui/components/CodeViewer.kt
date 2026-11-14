package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.WorkspaceFileEntity
import com.example.ui.theme.OceanAqua
import com.example.ui.theme.OceanDark800
import com.example.ui.theme.OceanDark900
import com.example.ui.theme.OceanIceSky
import com.example.ui.theme.OceanIceWhite
import com.example.ui.theme.OceanMuted
import com.example.ui.theme.OceanSuccess

/** Cheap per-language syntax highlighting for chat-grade code display. */
private object SyntaxHighlight {
    private val KEYWORDS = Regex(
        "\\b(fun|val|var|class|object|interface|enum|data|return|if|else|when|for|while|do|in|is|as|" +
            "try|catch|finally|throw|import|package|private|public|protected|internal|override|abstract|" +
            "sealed|open|suspend|inline|const|lateinit|by|init|constructor|companion|typealias|val|" +
            "val|fun|new|this|super|null|true|false)\\b"
    )
    private val STRING = Regex("\"[^\"\n]*\"|'[^'\n]*'")
    private val COMMENT = Regex("//.*$")

    fun colorLine(line: String, language: String): AnnotatedString = buildAnnotatedString {
        // Comments win the whole rest of the line.
        val commentMatch = COMMENT.find(line)
        val codeEnd = commentMatch?.range?.first ?: line.length

        appendLineHighlighted(line.substring(0, codeEnd))
        if (commentMatch != null) {
            withStyle(SpanStyle(color = OceanMuted, fontFamily = FontFamily.Monospace)) {
                append(line.substring(commentMatch.range.first))
            }
        }
    }

    private fun AnnotatedString.Builder.appendLineHighlighted(code: String) {
        var cursor = 0
        data class Token(val start: Int, val end: Int, val style: SpanStyle)
        val tokens = mutableListOf<Token>()
        STRING.findAll(code).forEach {
            tokens += Token(it.range.first, it.range.last + 1, SpanStyle(color = OceanSuccess, fontFamily = FontFamily.Monospace))
        }
        KEYWORDS.findAll(code).forEach { m ->
            if (tokens.none { m.range.first >= it.start && m.range.last + 1 <= it.end }) {
                tokens += Token(m.range.first, m.range.last + 1, SpanStyle(color = OceanAqua, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            }
        }
        tokens.sortBy { it.start }
        for (token in tokens) {
            if (token.start > cursor) append(code.substring(cursor, token.start))
            withStyle(token.style) { append(code.substring(token.start, token.end)) }
            cursor = token.end
        }
        if (cursor < code.length) append(code.substring(cursor))
    }
}

@Composable
fun CodeViewer(
    file: WorkspaceFileEntity,
    onSendToAgent: (String) -> Unit,
    modifier: Modifier = Modifier,
    breadcrumbBar: (@Composable () -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val lines = remember(file.content) { file.content.lines() }
    val highlighted = remember(file.content, file.language) {
        lines.map { SyntaxHighlight.colorLine(it, file.language) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OceanDark900)
    ) {
        // Top file toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OceanDark800)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = OceanAqua,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.path.substringAfterLast('/'),
                    color = OceanIceWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = "${lines.size} lines • ${file.language}",
                    color = OceanMuted,
                    fontSize = 11.sp
                )
            }

            // Copy Action
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(file.content)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = OceanIceSky,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Ask Agent Action
            OutlinedButton(
                onClick = { onSendToAgent("Explain and review ${file.path}") },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OceanIceSky),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ask Agent", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Breadcrumb slot (files tab passes a path navigator here)
        breadcrumbBar?.invoke()

        // Code Content with Line Numbers — virtualized rows; long lines wrap rather than clip
        // (horizontal scroll cannot be combined safely with lazy rows).
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(highlighted) { index, line ->
                Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = (index + 1).toString(),
                        color = OceanMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp,
                        modifier = Modifier.width(44.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = line,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp,
                        softWrap = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
