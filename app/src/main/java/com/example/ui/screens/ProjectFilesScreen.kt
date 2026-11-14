package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import com.example.ui.theme.OceanAqua
import com.example.ui.theme.OceanBorder
import com.example.ui.theme.OceanDark700
import com.example.ui.theme.OceanDark800
import com.example.ui.theme.OceanDark900
import com.example.ui.theme.OceanIceSky
import com.example.ui.theme.OceanIceWhite
import com.example.ui.theme.OceanMuted
import com.example.ui.viewmodel.HarnessViewModel

private const val WIDE_SCREEN_MIN_WIDTH = 700

@Composable
fun ProjectFilesScreen(
    viewModel: HarnessViewModel,
    modifier: Modifier = Modifier
) {
    val files by viewModel.workspaceFiles.collectAsStateWithLifecycle()
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    // Single-pane mode: which side of the explorer/viewer to show.
    var viewerVisible by remember { mutableStateOf(false) }

    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files else files.filter { it.path.contains(searchQuery, ignoreCase = true) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(OceanDark900)
    ) {
        val wide = maxWidth >= WIDE_SCREEN_MIN_WIDTH.dp

        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                FileExplorerPane(
                    files = filteredFiles,
                    selectedFile = selectedFile,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onFileClick = viewModel::selectFile,
                    onCreateClick = { showCreateDialog = true },
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    val current = selectedFile
                    if (current != null) {
                        CodeViewer(
                            file = current,
                            onSendToAgent = { prompt ->
                                viewModel.selectTab(0)
                                viewModel.sendPrompt(prompt)
                            },
                            breadcrumbBar = {
                                Breadcrumb(
                                    path = current.path,
                                    onNavigate = { searchQuery = it }
                                )
                            }
                        )
                    } else {
                        EmptyCodePane()
                    }
                }
            }
        } else {
            // Phone/compact: single pane with a back arrow in the viewer.
            val current = selectedFile
            if (viewerVisible && current != null) {
                CodeViewer(
                    file = current,
                    onSendToAgent = { prompt ->
                        viewModel.selectTab(0)
                        viewModel.sendPrompt(prompt)
                    },
                    breadcrumbBar = {
                        Breadcrumb(
                            path = current.path,
                            onBack = { viewerVisible = false },
                            onNavigate = { searchQuery = it }
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                FileExplorerPane(
                    files = filteredFiles,
                    selectedFile = selectedFile,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onFileClick = {
                        viewModel.selectFile(it)
                        viewerVisible = true
                    },
                    onCreateClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // New File Dialog
    if (showCreateDialog) {
        var newPath by remember { mutableStateOf("") }
        var newContent by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Workspace File", color = OceanIceWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPath,
                        onValueChange = { newPath = it },
                        label = { Text("File Path (e.g. app/src/main/Utils.kt)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OceanIceSky,
                            unfocusedBorderColor = OceanBorder,
                            focusedTextColor = OceanIceWhite,
                            unfocusedTextColor = OceanIceWhite
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
                            focusedBorderColor = OceanIceSky,
                            unfocusedBorderColor = OceanBorder,
                            focusedTextColor = OceanIceWhite,
                            unfocusedTextColor = OceanIceWhite
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
                    colors = ButtonDefaults.buttonColors(containerColor = OceanIceSky)
                ) {
                    Text("Create", color = Color(0xFF0D1117), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = OceanMuted)
                }
            },
            containerColor = OceanDark800
        )
    }
}

@Composable
private fun FileExplorerPane(
    files: List<WorkspaceFileEntity>,
    selectedFile: WorkspaceFileEntity?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onFileClick: (WorkspaceFileEntity) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(OceanDark800)
            .border(1.dp, OceanBorder)
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
                    tint = OceanIceSky,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "EXPLORER",
                    color = OceanIceWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }

            IconButton(
                onClick = onCreateClick,
                modifier = Modifier
                    .size(28.dp)
                    .background(OceanIceSky.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .testTag("create_file_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New File",
                    tint = OceanIceSky,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Search filter
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Filter files...", color = OceanMuted, fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OceanMuted, modifier = Modifier.size(14.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OceanIceSky,
                unfocusedBorderColor = OceanBorder,
                focusedContainerColor = OceanDark900,
                unfocusedContainerColor = OceanDark900,
                focusedTextColor = OceanIceWhite,
                unfocusedTextColor = OceanIceWhite
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
            items(files, key = { it.id }) { file ->
                val isSelected = file.id == selectedFile?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) OceanDark700 else Color.Transparent)
                        .clickable { onFileClick(file) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            file.path.endsWith(".kt") -> Icons.Default.Code
                            else -> Icons.Default.Description
                        },
                        contentDescription = null,
                        tint = if (isSelected) OceanIceSky else OceanAqua,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = file.path.substringAfterLast('/'),
                            color = if (isSelected) OceanIceSky else OceanIceWhite,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                        val dir = file.path.substringBeforeLast('/', "")
                        if (dir.isNotBlank()) {
                            Text(
                                text = dir,
                                color = OceanMuted,
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
}

@Composable
private fun Breadcrumb(
    path: String,
    onNavigate: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val segments = path.split('/').filter { it.isNotBlank() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(OceanDark800)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to file list",
                    tint = OceanIceSky,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
        }
        segments.forEachIndexed { index, segment ->
            val isLast = index == segments.lastIndex
            Text(
                text = segment,
                color = if (isLast) OceanIceWhite else OceanIceSky,
                fontSize = 12.sp,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                modifier = if (isLast) {
                    Modifier
                } else {
                    Modifier.clickable { onNavigate(segments.take(index + 1).joinToString("/") + "/") }
                }
            )
            if (!isLast) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = OceanMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyCodePane() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Select a file to inspect",
            color = OceanMuted,
            fontSize = 14.sp
        )
    }
}
