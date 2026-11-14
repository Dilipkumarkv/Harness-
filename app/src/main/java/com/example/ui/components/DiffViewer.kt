package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DiffLineType
import com.example.domain.tools.PatchEngine
import com.example.ui.theme.DiffAddedBg
import com.example.ui.theme.DiffAddedText
import com.example.ui.theme.DiffRemovedBg
import com.example.ui.theme.DiffRemovedText
import com.example.ui.theme.OceanDark900
import com.example.ui.theme.OceanMuted

@Composable
fun DiffViewer(
    diffContent: String,
    modifier: Modifier = Modifier
) {
    val diffLines = remember(diffContent) {
        PatchEngine.parseDiffLines(diffContent)
    }

    val horizontalScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OceanDark900, RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp)
            .horizontalScroll(horizontalScroll)
    ) {
        diffLines.forEach { line ->
            val (bgColor, textColor, prefixSymbol) = when (line.type) {
                DiffLineType.ADDED -> Triple(DiffAddedBg, DiffAddedText, "+")
                DiffLineType.REMOVED -> Triple(DiffRemovedBg, DiffRemovedText, "-")
                DiffLineType.HEADER -> Triple(Color(0x3030363D), Color(0xFF58A6FF), "@")
                DiffLineType.UNCHANGED -> Triple(Color.Transparent, Color(0xFFC9D1D9), " ")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                // Line numbers
                Text(
                    text = line.oldLineNum?.toString() ?: "",
                    color = OceanMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(28.dp)
                )
                Text(
                    text = line.newLineNum?.toString() ?: "",
                    color = OceanMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(28.dp)
                )
                // Prefix
                Text(
                    text = prefixSymbol,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(16.dp)
                )
                // Content
                Text(
                    text = line.content.removePrefix("+").removePrefix("-"),
                    color = textColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
