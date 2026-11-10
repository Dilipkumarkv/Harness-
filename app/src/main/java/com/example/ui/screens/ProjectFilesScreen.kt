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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.entity.WorkspaceFileEntity
import com.example.ui.components.CodeViewer
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
fun ProjectFilesScreen(
    viewModel: HarnessViewModel,
    modifier: Modifier = Modifier
) {
    val files by viewModel.workspaceFiles.collectAsStateWithLifecycle()
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files else files.filter { it.path.contains(searchQuery, ignoreCase = true) }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDark900)
    ) {
        // Left Column: File Explorer
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxSize()
                .background(SlateDark800)
                .border(1.dp, SlateDark600)
        ) {
            // Header with search & add
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EXPLORER",
                        color = SlateWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .size(28.dp)
                        .background(NeonEmerald.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .testTag("create_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New File",
                        tint = NeonEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Search filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter files...", color = SlateMuted, fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(14.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonEmerald,
                    unfocusedBorderColor = SlateDark600,
                    focusedContainerColor = SlateDark900,
                    unfocusedContainerColor = SlateDark900,
                    focusedTextColor = SlateWhite,
                    unfocusedTextColor = SlateWhite
                ),
                singleLine = true
            )

            // File items list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                items(filteredFiles, key = { it.id }) { file ->
                    val isSelected = file.id == selectedFile?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) SlateDark700 else Color.Transparent)
                            .clickable { viewModel.selectFile(file) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                file.path.endsWith(".kt") -> Icons.Default.Code
                                else -> Icons.Default.Description
                            },
                            contentDescription = null,
                            tint = if (isSelected) NeonEmerald else CyberSky,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = file.path.substringAfterLast('/'),
                                color = if (isSelected) NeonEmerald else SlateWhite,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                            val dir = file.path.substringBeforeLast('/', "")
                            if (dir.isNotBlank()) {
                                Text(
                                    text = dir,
                                    color = SlateMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right Column: Code Viewer
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            if (selectedFile != null) {
                CodeViewer(
                    file = selectedFile!!,
                    onSendToAgent = { prompt ->
                        viewModel.selectTab(0) // Switch to Agent Workspace
                        viewModel.sendPrompt(prompt)
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a file to inspect",
                        color = SlateMuted,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // New File Dialog
    if (showCreateDialog) {
        var newPath by remember { mutableStateOf("") }
        var newContent by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Workspace File", color = SlateWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPath,
                        onValueChange = { newPath = it },
                        label = { Text("File Path (e.g. app/src/main/Utils.kt)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = SlateDark600,
                            focusedTextColor = SlateWhite,
                            unfocusedTextColor = SlateWhite
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        label = { Text("Initial Content") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = SlateDark600,
                            focusedTextColor = SlateWhite,
                            unfocusedTextColor = SlateWhite
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPath.isNotBlank()) {
                            viewModel.createFile(newPath.trim(), newContent)
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald)
                ) {
                    Text("Create", color = Color(0xFF0D1117), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = SlateMuted)
                }
            },
            containerColor = SlateDark800
        )
    }
}
