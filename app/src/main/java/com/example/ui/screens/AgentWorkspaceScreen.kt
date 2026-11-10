package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ChatMessage
import com.example.ui.components.ToolExecutionCard
import com.example.ui.theme.OceanAqua
import com.example.ui.theme.OceanBorder
import com.example.ui.theme.OceanDark700
import com.example.ui.theme.OceanDark800
import com.example.ui.theme.OceanDark900
import com.example.ui.theme.OceanError
import com.example.ui.theme.OceanIceSky
import com.example.ui.theme.OceanIceWhite
import com.example.ui.theme.OceanMuted
import com.example.ui.theme.OceanSteel
import com.example.ui.theme.OceanWarning
import com.example.ui.viewmodel.HarnessViewModel

@Composable
fun AgentWorkspaceScreen(
    viewModel: HarnessViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isRunning by viewModel.isAgentRunning.collectAsStateWithLifecycle()
    val currentThought by viewModel.currentThought.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Scroll to bottom on new message
    LaunchedEffect(messages.size, isRunning) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val suggestionChips = listOf(
        "⚡ Run Gradle Build Check",
        "🩹 Add FloatingActionButton to MainActivity",
        "🩺 Run Doctor Diagnostics",
        "🔀 Check Git Status & Diffs",
        "🔍 Search Code for ComponentActivity"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OceanDark900) // #0A1931 Base
    ) {
        // Quick suggestion chips horizontally scrollable
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OceanDark800)
                .border(width = 1.dp, color = OceanBorder)
                .padding(vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            suggestionChips.forEach { prompt ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = OceanDark700, // #1A3D63
                    border = androidx.compose.foundation.BorderStroke(1.dp, OceanSteel.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable(enabled = !isRunning) {
                        viewModel.sendPrompt(prompt)
                    }
                ) {
                    Text(
                        text = prompt,
                        color = OceanIceSky, // #B3CFE5
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Messages Conversation Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                if (message.role == "user") {
                    UserMessageBubble(message)
                } else {
                    AssistantMessageItem(message)
                }
            }

            // Active agent loop indicator
            if (isRunning) {
                item {
                    ActiveAgentPulse(thought = currentThought)
                }
            }
        }

        // Bottom Input Dock
        Surface(
            color = OceanDark800,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, OceanBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Ask agent to code, patch, or debug...",
                            color = OceanMuted,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("prompt_input_field"),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OceanIceSky,
                        unfocusedBorderColor = OceanSteel.copy(alpha = 0.5f),
                        focusedContainerColor = OceanDark900,
                        unfocusedContainerColor = OceanDark900,
                        focusedTextColor = OceanIceWhite,
                        unfocusedTextColor = OceanIceWhite
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (isRunning) {
                    IconButton(
                        onClick = { viewModel.stopAgent() },
                        modifier = Modifier
                            .size(44.dp)
                            .background(OceanError.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, OceanError, CircleShape)
                            .testTag("stop_agent_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Agent",
                            tint = OceanError
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendPrompt(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (inputText.isNotBlank()) OceanIceSky else OceanDark700, CircleShape)
                            .testTag("send_prompt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Prompt",
                            tint = if (inputText.isNotBlank()) OceanDark900 else OceanMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    color = OceanDark700, // #1A3D63
                    shape = RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
                )
                .border(1.dp, OceanSteel.copy(alpha = 0.6f), RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp))
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                color = OceanIceWhite, // #F6FAFD
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun AssistantMessageItem(message: ChatMessage) {
    var thoughtExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Agent Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(OceanIceSky.copy(alpha = 0.2f), CircleShape)
                    .border(1.dp, OceanIceSky.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = OceanIceSky,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "AndroidHarness Agent",
                color = OceanIceSky, // #B3CFE5
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Thought block (collapsible)
        if (!message.thought.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = OceanDark700),
                border = androidx.compose.foundation.BorderStroke(1.dp, OceanBorder),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { thoughtExpanded = !thoughtExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = OceanAqua,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Agent Reasoning Plan",
                                color = OceanAqua,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Icon(
                            imageVector = if (thoughtExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = OceanIceSky,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = thoughtExpanded) {
                        Text(
                            text = message.thought,
                            color = OceanIceSky.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        // Tool Execution Cards
        message.toolCalls.forEach { toolCall ->
            ToolExecutionCard(toolCall = toolCall)
        }

        // Assistant Text Explanation
        if (message.content.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(OceanDark800, RoundedCornerShape(12.dp))
                    .border(1.dp, OceanBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    color = OceanIceWhite, // #F6FAFD
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun ActiveAgentPulse(thought: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OceanDark700, RoundedCornerShape(8.dp))
            .border(1.dp, OceanIceSky.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = OceanIceSky,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = thought ?: "Autonomous agent reasoning and selecting tools...",
            color = OceanIceSky,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
