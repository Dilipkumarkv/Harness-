package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.domain.model.HarnessSettings
import com.example.domain.model.LocalToolProtocol
import com.example.domain.model.ProviderType
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberRose
import com.example.ui.theme.CyberSky
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateDark600
import com.example.ui.theme.SlateDark700
import com.example.ui.theme.SlateDark800
import com.example.ui.theme.SlateDark900
import com.example.ui.theme.SlateLight
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateWhite
import com.example.ui.viewmodel.HarnessViewModel

@Composable
fun SettingsScreen(
    viewModel: HarnessViewModel,
    onOpenDoctor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var localEndpoint by remember(settings) { mutableStateOf(settings.localEndpointUrl) }
    var customKey by remember(settings) { mutableStateOf(settings.customApiKey) }
    var customEndpoint by remember(settings) { mutableStateOf(settings.customEndpointUrl) }
    var modelName by remember(settings) { mutableStateOf(settings.modelName) }
    var gitPath by remember(settings) { mutableStateOf(settings.gitPath) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDark900)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Model Provider Selection
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateDark800),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateDark600),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI MODEL ENGINE",
                            color = SlateWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Option 1: Local Tool Calling LLM
                    ProviderOptionItem(
                        title = "Local Tool LLM (Recommended)",
                        description = "On-device autonomous agent loop with ReAct/XML tool-calling and local Ollama/Termux endpoint support.",
                        selected = settings.providerType == ProviderType.LOCAL_TOOL_LLM,
                        onClick = {
                            viewModel.updateSettings(settings.copy(providerType = ProviderType.LOCAL_TOOL_LLM))
                        }
                    )

                    // Option 2: Custom BYOK
                    ProviderOptionItem(
                        title = "Custom BYOK Cloud Endpoint",
                        description = "Use your own OpenAI-compatible API (OpenAI, OpenRouter, Groq, Together, Ollama, LocalAI…).",
                        selected = settings.providerType == ProviderType.BYOK_CUSTOM,
                        onClick = {
                            viewModel.updateSettings(settings.copy(providerType = ProviderType.BYOK_CUSTOM))
                        }
                    )
                }
            }
        }

        // Local Tool Calling Configuration (shown if Local LLM is selected)
        if (settings.providerType == ProviderType.LOCAL_TOOL_LLM) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateDark800),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateDark600),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = CyberSky,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LOCAL TOOL-CALLING PROTOCOL",
                                color = SlateWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProtocolChip(
                                label = "XML Tags (<tool_call>)",
                                selected = settings.localProtocol == LocalToolProtocol.XML_TAGS,
                                onClick = { viewModel.updateSettings(settings.copy(localProtocol = LocalToolProtocol.XML_TAGS)) }
                            )
                            ProtocolChip(
                                label = "ReAct Format",
                                selected = settings.localProtocol == LocalToolProtocol.REACT_TEXT,
                                onClick = { viewModel.updateSettings(settings.copy(localProtocol = LocalToolProtocol.REACT_TEXT)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = localEndpoint,
                            onValueChange = {
                                localEndpoint = it
                                viewModel.updateSettings(settings.copy(localEndpointUrl = it))
                            },
                            label = { Text("Local Model Endpoint (Ollama / Termux)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonEmerald,
                                unfocusedBorderColor = SlateDark600,
                                focusedTextColor = SlateWhite,
                                unfocusedTextColor = SlateWhite
                            )
                        )
                    }
                }
            }
        }

        // Custom BYOK Settings
        if (settings.providerType == ProviderType.BYOK_CUSTOM) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateDark800),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateDark600),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = CyberAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CUSTOM API CREDENTIALS",
                                color = SlateWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = customKey,
                            onValueChange = {
                                customKey = it
                                viewModel.updateSettings(settings.copy(customApiKey = it))
                            },
                            label = { Text("API Key (Stored locally in app-private SharedPreferences, not encrypted)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonEmerald,
                                unfocusedBorderColor = SlateDark600,
                                focusedTextColor = SlateWhite,
                                unfocusedTextColor = SlateWhite
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customEndpoint,
                            onValueChange = {
                                customEndpoint = it
                                viewModel.updateSettings(settings.copy(customEndpointUrl = it))
                            },
                            label = { Text("Custom Endpoint URL (OpenAI-compatible) e.g. https://api.openai.com/v1 or http://10.0.2.2:11434/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonEmerald,
                                unfocusedBorderColor = SlateDark600,
                                focusedTextColor = SlateWhite,
                                unfocusedTextColor = SlateWhite
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = modelName,
                            onValueChange = {
                                modelName = it
                                viewModel.updateSettings(settings.copy(modelName = it))
                            },
                            label = { Text("Model Name (e.g. gpt-4o-mini, llama3.1:8b, qwen2.5-coder:7b)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonEmerald,
                                unfocusedBorderColor = SlateDark600,
                                focusedTextColor = SlateWhite,
                                unfocusedTextColor = SlateWhite
                            )
                        )
                    }
                }
            }
        }

        // Git binary path
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateDark800),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateDark600),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = CyberSky,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GIT BINARY PATH",
                            color = SlateWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = gitPath,
                        onValueChange = {
                            gitPath = it
                            viewModel.updateSettings(settings.copy(gitPath = it))
                        },
                        label = { Text("Git executable (default: git)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = SlateDark600,
                            focusedTextColor = SlateWhite,
                            unfocusedTextColor = SlateWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "On Android, 'git' is not bundled. Install Termux and `pkg install git`, then set the path (e.g. /data/data/com.termux/files/usr/bin/git).",
                        color = SlateMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Diagnostics Doctor
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateDark800),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateDark600),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = null,
                            tint = CyberAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SYSTEM DIAGNOSTICS & DOCTOR",
                            color = SlateWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Run internal self-tests verifying real filesystem, toolchain sandbox, ReAct/XML parser, and patch engine.",
                        color = SlateLight,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenDoctor,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberAmber),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("run_doctor_button")
                    ) {
                        Text("Launch Doctor Self-Test", color = Color(0xFF1E1405), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Session Actions
        item {
            OutlinedButton(
                onClick = { viewModel.clearSessionHistory() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberRose),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clear_history_button")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear Conversation History", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProviderOptionItem(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = NeonEmerald, unselectedColor = SlateMuted)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = title,
                color = if (selected) NeonEmerald else SlateWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                text = description,
                color = SlateMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun ProtocolChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(if (selected) NeonEmerald.copy(alpha = 0.2f) else SlateDark700, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) NeonEmerald else SlateDark600, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) NeonEmerald else SlateLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
