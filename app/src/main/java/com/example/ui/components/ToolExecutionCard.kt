package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ToolCallItem
import com.example.domain.model.ToolCallStatus
import com.example.ui.theme.OceanAqua
import com.example.ui.theme.OceanBorder
import com.example.ui.theme.OceanDark700
import com.example.ui.theme.OceanDark900
import com.example.ui.theme.OceanError
import com.example.ui.theme.OceanIceSky
import com.example.ui.theme.OceanIceWhite
import com.example.ui.theme.OceanMuted
import com.example.ui.theme.OceanSteel
import com.example.ui.theme.OceanSuccess
import com.example.ui.theme.OceanWarning

@Composable
fun ToolExecutionCard(
    toolCall: ToolCallItem,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val (toolIcon, toolAccent) = getToolVisuals(toolCall.toolName)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, OceanSteel.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .animateContentSize()
            .testTag("tool_card_${toolCall.toolName}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = OceanDark700 // #1A3D63 elevated surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Tool Icon Box
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(toolAccent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .border(1.dp, toolAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = toolIcon,
                            contentDescription = toolCall.toolName,
                            tint = toolAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = toolCall.toolName,
                                color = OceanIceWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (toolCall.durationMs > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${toolCall.durationMs}ms",
                                    color = OceanIceSky.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Summary of args
                        val summaryText = extractArgsSummary(toolCall.argumentsJson)
                        if (summaryText.isNotBlank()) {
                            Text(
                                text = summaryText,
                                color = OceanIceSky,
                                fontSize = 12.sp,
                                maxLines = 1,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(status = toolCall.status)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = OceanIceSky,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expanded Detail View
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    // Arguments Section
                    Text(
                        text = "PARAMETERS",
                        color = OceanSteel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 8.dp)
                            .background(OceanDark900, RoundedCornerShape(6.dp))
                            .border(1.dp, OceanBorder, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = toolCall.argumentsJson,
                            color = OceanIceSky,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Diff View if present
                    if (!toolCall.diffContent.isNullOrBlank()) {
                        Text(
                            text = "UNIFIED DIFF",
                            color = OceanSuccess,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        DiffViewer(
                            diffContent = toolCall.diffContent,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Console Output (truncated by default so huge diffs/logs don't blow up the card)
                    if (!toolCall.resultJson.isNullOrBlank()) {
                        val output = toolCall.resultJson
                        var outputExpanded by remember { mutableStateOf(false) }
                        val limit = 2_000
                        val visible = if (outputExpanded || output.length <= limit) {
                            output
                        } else {
                            output.take(limit) + "\n… (+${output.length - limit} more chars)"
                        }
                        Text(
                            text = "OUTPUT",
                            color = OceanSteel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .background(OceanDark900, RoundedCornerShape(6.dp))
                                .border(1.dp, OceanBorder, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = visible,
                                color = if (toolCall.status == ToolCallStatus.ERROR) OceanError else OceanIceWhite,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (output.length > limit) {
                            Text(
                                text = if (outputExpanded) "Show less" else "Show full output",
                                color = OceanIceSky,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { outputExpanded = !outputExpanded }
                                    .testTag("toggle_full_output_${toolCall.toolName}")
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Breathing dot shown only while a tool call is RUNNING. */
@Composable
private fun PulseDot(color: androidx.compose.ui.graphics.Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .alpha(alphaAnim)
            .background(color, CircleShape)
    )
}

@Composable
fun StatusPill(status: ToolCallStatus) {
    val (bgColor, textColor, label) = when (status) {
        ToolCallStatus.RUNNING -> Triple(OceanIceSky.copy(alpha = 0.2f), OceanIceSky, "RUNNING")
        ToolCallStatus.SUCCESS -> Triple(OceanSuccess.copy(alpha = 0.2f), OceanSuccess, "SUCCESS")
        ToolCallStatus.ERROR -> Triple(OceanError.copy(alpha = 0.2f), OceanError, "ERROR")
    }

    Row(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, textColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status == ToolCallStatus.RUNNING) {
            PulseDot(color = textColor)
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun getToolVisuals(toolName: String): Pair<ImageVector, Color> {
    return when (toolName) {
        "read_file" -> Pair(Icons.Default.Description, OceanIceSky)
        "write_file" -> Pair(Icons.Default.Edit, OceanAqua)
        "apply_patch" -> Pair(Icons.Default.Code, OceanAqua)
        "list_directory" -> Pair(Icons.Default.Folder, OceanSteel)
        "search_code" -> Pair(Icons.Default.Search, OceanIceSky)
        "shell_exec" -> Pair(Icons.Default.Terminal, OceanAqua)
        "git_status", "git_diff" -> Pair(Icons.Default.Code, OceanSteel)
        "doctor_diagnostics" -> Pair(Icons.Default.MedicalServices, OceanWarning)
        else -> Pair(Icons.Default.Terminal, OceanIceSky)
    }
}

private fun extractArgsSummary(argsJson: String): String {
    return try {
        val obj = org.json.JSONObject(argsJson)
        when {
            obj.has("path") -> obj.getString("path")
            obj.has("command") -> "$ ${obj.getString("command")}"
            obj.has("query") -> "query: \"${obj.getString("query")}\""
            else -> ""
        }
    } catch (_: Exception) {
        ""
    }
}
