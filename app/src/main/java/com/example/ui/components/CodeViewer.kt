package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.WorkspaceFileEntity
import com.example.ui.theme.CyberSky
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateDark600
import com.example.ui.theme.SlateDark800
import com.example.ui.theme.SlateDark900
import com.example.ui.theme.SlateLight
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateWhite

@Composable
fun CodeViewer(
    file: WorkspaceFileEntity,
    onSendToAgent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val lines = file.content.lines()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDark900)
    ) {
        // Top file toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateDark800)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = CyberSky,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.path.substringAfterLast('/'),
                    color = SlateWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${lines.size} lines • ${file.language}",
                    color = SlateMuted,
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
                    tint = SlateLight,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Ask Agent Action
            OutlinedButton(
                onClick = { onSendToAgent("Explain and review ${file.path}") },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonEmerald),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ask Agent", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Code Content with Line Numbers
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll)
                .padding(vertical = 8.dp)
        ) {
            // Line numbers column
            Column(
                modifier = Modifier
                    .background(SlateDark800.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp)
            ) {
                lines.forEachIndexed { index, _ ->
                    Text(
                        text = (index + 1).toString(),
                        color = SlateMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Code text lines
            Column(modifier = Modifier.padding(end = 16.dp)) {
                lines.forEach { line ->
                    Text(
                        text = if (line.isEmpty()) " " else line,
                        color = getSyntaxColor(line),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

private fun getSyntaxColor(line: String): Color {
    val trimmed = line.trim()
    return when {
        trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*") -> com.example.ui.theme.OceanMuted // #8FAAC2 Comment
        trimmed.startsWith("import ") || trimmed.startsWith("package ") -> com.example.ui.theme.OceanSteel // #4A7FA7 Module import
        trimmed.startsWith("class ") || trimmed.startsWith("interface ") || trimmed.startsWith("fun ") -> com.example.ui.theme.OceanAqua // #5CE1E6 Declaration
        trimmed.startsWith("@") -> com.example.ui.theme.OceanIceSky // #B3CFE5 Annotation
        trimmed.startsWith("<") && trimmed.endsWith(">") -> com.example.ui.theme.OceanSuccess // XML tag
        trimmed.contains("val ") || trimmed.contains("var ") -> com.example.ui.theme.OceanIceSky
        else -> com.example.ui.theme.OceanIceWhite // #F6FAFD High-contrast body
    }
}
