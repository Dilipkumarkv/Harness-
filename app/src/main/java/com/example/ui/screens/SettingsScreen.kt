package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.HarnessSettings
import com.example.domain.model.LocalToolProtocol
import com.example.domain.model.ProviderType
import com.example.llm.ModelManager
import com.example.ui.theme.OceanWarning
import com.example.ui.theme.OceanError
import com.example.ui.theme.OceanAqua
import com.example.ui.theme.OceanIceSky
import com.example.ui.theme.OceanBorder
import com.example.ui.theme.OceanDark700
import com.example.ui.theme.OceanDark800
import com.example.ui.theme.OceanDark900
import com.example.ui.theme.OceanIceSky
import com.example.ui.theme.OceanMuted
import com.example.ui.theme.OceanIceWhite
import com.example.ui.viewmodel.HarnessViewModel

@Composable
fun SettingsScreen(
    viewModel: HarnessViewModel,
    onOpenDoctor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImportingModel.collectAsStateWithLifecycle()
    val importError by viewModel.modelImportError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
            viewModel.importModel(uri, displayName)
        }
    }

    var customKey by remember(settings) { mutableStateOf(settings.customApiKey) }
    var customEndpoint by remember(settings) { mutableStateOf(settings.customEndpointUrl) }
    var modelName by remember(settings) { mutableStateOf(settings.modelName) }
    var gitPath by remember(settings) { mutableStateOf(settings.gitPath) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OceanDark900)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Model Provider Selection
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OceanDark800),
                border = androidx.compose.foundation.BorderStroke(1.dp, OceanBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = OceanIceSky,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI MODEL ENGINE",
                            color = OceanIceWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Option 1: Keyword router (no model)
                    ProviderOptionItem(
                        title = "Local Keyword Router",
                        description = "No model, no network. Matches keywords to run a real tool. Cannot author code.",
                        selected = settings.providerType == ProviderType.LOCAL_TOOL_LLM,
                        onClick = {
                            viewModel.updateSettings(settings.copy(providerType = ProviderType.LOCAL_TOOL_LLM))
                        }
                    )

                    // Option 2: Custom endpoint with a real model
                    ProviderOptionItem(
                        title = "Custom Endpoint (real model)",
                        description = "Send your prompt to an OpenAI-compatible /chat/completions endpoint and let the model drive the tools.",
                        selected = settings.providerType == ProviderType.BYOK_CUSTOM,
                        onClick = {
                            viewModel.updateSettings(settings.copy(providerType = ProviderType.BYOK_CUSTOM))
                        }
                    )

                    // Option 3: On-device GGUF model via llama.cpp
                    ProviderOptionItem(
                        title = "On-Device Model (GGUF)",
                        description = "Run an imported .gguf model fully offline with llama.cpp. No network, no API key.",
                        selected = settings.providerType == ProviderType.ON_DEVICE_LLAMA,
                        onClick = {
                            viewModel.updateSettings(settings.copy(providerType = ProviderType.ON_DEVICE_LLAMA))
                        }
                    )
                }
            }
        }

        // Keyword router: no model involved.
        if (settings.providerType == ProviderType.LOCAL_TOOL_LLM) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OceanDark800),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OceanBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = OceanAqua,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "KEYWORD ROUTER",
                                color = OceanIceWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "No model runs on this device and no network request is made. " +
                                "Your message is matched against keywords to pick a real tool:\n\n" +
                                "  build / compile  \u2192 gradle assembleDebug\n" +
                                "  git / diff       \u2192 git status, git diff\n" +
                                "  doctor           \u2192 health probes\n" +
                                "  search / find    \u2192 search_code\n" +
                                "  list / files     \u2192 list_directory\n" +
                                "  anything else    \u2192 reads the file you name, then says it cannot author code",
                            color = OceanIceSky,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "To have the agent write or edit code, switch to the custom endpoint above and supply a model.",
                            color = OceanMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Custom BYOK Settings
        if (settings.providerType == ProviderType.BYOK_CUSTOM) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OceanDark800),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OceanBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = OceanWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CUSTOM API CREDENTIALS",
                                color = OceanIceWhite,
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
                            label = { Text("API Key (saved in this app's private settings)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OceanIceSky,
                                unfocusedBorderColor = OceanBorder,
                                focusedTextColor = OceanIceWhite,
                                unfocusedTextColor = OceanIceWhite
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
                                focusedBorderColor = OceanIceSky,
                                unfocusedBorderColor = OceanBorder,
                                focusedTextColor = OceanIceWhite,
                                unfocusedTextColor = OceanIceWhite
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = modelName,
                            onValueChange = {
                                modelName = it
                                viewModel.updateSettings(settings.copy(modelName = it))
                            },
                            label = { Text("Model Name (e.g. gpt-4o-mini, qwen2.5-coder:7b)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OceanIceSky,
                                unfocusedBorderColor = OceanBorder,
                                focusedTextColor = OceanIceWhite,
                                unfocusedTextColor = OceanIceWhite
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "TOOL-CALL FORMAT",
                            color = OceanIceWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "How the model is asked to emit tool calls. Pick whichever your model follows best.",
                            color = OceanMuted,
                            fontSize = 10.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProtocolChip(
                                label = "XML",
                                selected = settings.localProtocol == LocalToolProtocol.XML_TAGS,
                                onClick = { viewModel.updateSettings(settings.copy(localProtocol = LocalToolProtocol.XML_TAGS)) }
                            )
                            ProtocolChip(
                                label = "ReAct",
                                selected = settings.localProtocol == LocalToolProtocol.REACT_TEXT,
                                onClick = { viewModel.updateSettings(settings.copy(localProtocol = LocalToolProtocol.REACT_TEXT)) }
                            )
                            ProtocolChip(
                                label = "JSON",
                                selected = settings.localProtocol == LocalToolProtocol.JSON_BLOCK,
                                onClick = { viewModel.updateSettings(settings.copy(localProtocol = LocalToolProtocol.JSON_BLOCK)) }
                            )
                        }
                    }
                }
            }
        }

        // On-device GGUF model management
        if (settings.providerType == ProviderType.ON_DEVICE_LLAMA) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OceanDark800),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OceanBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = OceanIceSky,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ON-DEVICE MODEL (GGUF)",
                                color = OceanIceWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Import a .gguf model (e.g. Qwen2.5-Coder-1.5B/3B-Instruct, Q4_K_M). " +
                                "Inference runs on this device's CPU via llama.cpp — small quantized models work best. " +
                                "Uses ${settings.modelThreads} threads and a ${settings.modelContextSize}-token context.",
                            color = OceanIceSky,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { pickModel.launch(arrayOf("*/*")) },
                            enabled = !isImporting,
                            colors = ButtonDefaults.buttonColors(containerColor = OceanIceSky),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("import_model_button")
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    color = Color(0xFF04140C),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Importing…", color = Color(0xFF04140C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            } else {
                                Text("Import .gguf model", color = Color(0xFF04140C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (importError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = importError ?: "",
                                color = OceanError,
                                fontSize = 11.sp,
                                modifier = Modifier.testTag("model_import_error")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (models.isEmpty()) {
                            Text(
                                text = "No models imported yet.",
                                color = OceanMuted,
                                fontSize = 11.sp
                            )
                        } else {
                            models.forEach { model ->
                                ModelRow(
                                    model = model,
                                    selected = settings.modelPath == model.path,
                                    onSelect = { viewModel.selectModel(model.path) },
                                    onDelete = { viewModel.deleteModel(model.path) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "TOOL-CALL FORMAT",
                            color = OceanIceWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProtocolChip(
                                label = "XML",
                                selected = settings.localProtocol == LocalToolProtocol.XML_TAGS,
                                onClick = { viewModel.updateSettings(settings.copy(localProtocol = LocalToolProtocol.XML_TAGS)) }
                            )
                            ProtocolChip(
                                label = "ReAct",
                                selected = settings.localProtocol == LocalToolProtocol.REACT_TEXT,
                                onClick = { viewModel.updateSettings(settings.copy(localProtocol = LocalToolProtocol.REACT_TEXT)) }
                            )
                            ProtocolChip(
                                label = "JSON",
                                selected = settings.localProtocol == LocalToolProtocol.JSON_BLOCK,
                                onClick = { viewModel.updateSettings(settings.copy(localProtocol = LocalToolProtocol.JSON_BLOCK)) }
                            )
                        }
                    }
                }
            }
        }

        // Git binary path
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OceanDark800),
                border = androidx.compose.foundation.BorderStroke(1.dp, OceanBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = OceanAqua,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GIT BINARY PATH",
                            color = OceanIceWhite,
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
                            focusedBorderColor = OceanIceSky,
                            unfocusedBorderColor = OceanBorder,
                            focusedTextColor = OceanIceWhite,
                            unfocusedTextColor = OceanIceWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "On Android, 'git' is not bundled. Install Termux and `pkg install git`, then set the path (e.g. /data/data/com.termux/files/usr/bin/git).",
                        color = OceanMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Diagnostics Doctor
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OceanDark800),
                border = androidx.compose.foundation.BorderStroke(1.dp, OceanBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = null,
                            tint = OceanWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SYSTEM DIAGNOSTICS & DOCTOR",
                            color = OceanIceWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Run internal self-tests verifying real filesystem, toolchain sandbox, ReAct/XML parser, and patch engine.",
                        color = OceanIceSky,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenDoctor,
                        colors = ButtonDefaults.buttonColors(containerColor = OceanWarning),
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OceanError),
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
            colors = RadioButtonDefaults.colors(selectedColor = OceanIceSky, unselectedColor = OceanMuted)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = title,
                color = if (selected) OceanIceSky else OceanIceWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                text = description,
                color = OceanMuted,
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
            .background(if (selected) OceanIceSky.copy(alpha = 0.2f) else OceanDark700, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) OceanIceSky else OceanBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) OceanIceSky else OceanIceSky,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ModelRow(
    model: ModelManager.ModelInfo,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = OceanIceSky, unselectedColor = OceanMuted)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name,
                color = if (selected) OceanIceSky else OceanIceWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${model.sizeBytes / 1_048_576} MB",
                color = OceanMuted,
                fontSize = 10.sp
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_model_${model.name}")) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete ${model.name}",
                tint = OceanError,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
