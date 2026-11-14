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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.components.DiffViewer
import com.example.ui.theme.OceanSteel
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
fun GitDiffsScreen(
    viewModel: HarnessViewModel,
    modifier: Modifier = Modifier
) {
    val gitStatusText by viewModel.gitStatusText.collectAsStateWithLifecycle()
    val gitDiffText by viewModel.gitDiffText.collectAsStateWithLifecycle()
    val gitLogText by viewModel.gitLogText.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isGitRefreshing.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OceanDark900)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Repo Overview Card (real git status)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OceanDark800),
                border = androidx.compose.foundation.BorderStroke(1.dp, OceanBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Difference,
                                contentDescription = null,
                                tint = OceanSteel,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GIT REPOSITORY STATUS",
                                color = OceanIceWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.refreshGit() },
                            enabled = !isRefreshing,
                            colors = ButtonDefaults.buttonColors(containerColor = OceanSteel.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isRefreshing) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF1E1136),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF1E1136), modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh", color = Color(0xFF1E1136), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = gitStatusText.ifBlank { "(git status unavailable — is git installed?)" }.take(500),
                        color = OceanIceSky,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.selectTab(0)
                            viewModel.sendPrompt("git status and review all uncommitted diffs")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OceanSteel),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Analyze Diff with Agent", color = Color(0xFF1E1136), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Active Working Tree Diff (real git diff) — file-by-file expandable sections
        item {
            val sections = remember(gitDiffText) { splitDiffSections(gitDiffText) }
            var expandedFiles by remember { mutableStateOf(setOf<String>()) }

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE WORKING TREE DIFF",
                        color = OceanIceSky,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (sections.isNotEmpty()) {
                        Text(
                            text = "${sections.size} file(s) changed",
                            color = OceanMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (sections.isEmpty()) {
                    Text(
                        text = "No tracked modifications. Untracked files appear under git status, not git diff.",
                        color = OceanMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    sections.forEach { section ->
                        val isExpanded = section.filePath in expandedFiles
                        val added = section.content.lineSequence().count { it.startsWith("+") && !it.startsWith("+++") }
                        val removed = section.content.lineSequence().count { it.startsWith("-") && !it.startsWith("---") }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedFiles = if (isExpanded) expandedFiles - section.filePath
                                    else expandedFiles + section.filePath
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                contentDescription = if (isExpanded) "Collapse diff" else "Expand diff",
                                tint = OceanIceSky,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = section.filePath,
                                color = OceanIceWhite,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "+$added",
                                color = com.example.ui.theme.OceanSuccess,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "  −$removed",
                                color = com.example.ui.theme.OceanError,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (isExpanded) {
                            DiffViewer(
                                diffContent = section.content,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Commit Log (real git log)
        item {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = OceanMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RECENT COMMITS",
                        color = OceanMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                val logLines = gitLogText.split("\n").filter { it.isNotBlank() }
                if (logLines.isEmpty()) {
                    Text(
                        text = "No commits yet — initialise git and make a commit.",
                        color = OceanMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(logLines) { line ->
                            CommitItem(
                                hash = line.take(7),
                                msg = line.drop(7).takeIf { it.isNotBlank() } ?: line,
                                author = "",
                                timeAgo = ""
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class DiffSection(val filePath: String, val content: String)

/** Splits a `git diff` into one section per file, keyed on `diff --git` boundaries. */
private fun splitDiffSections(diff: String): List<DiffSection> {
    if (diff.isBlank()) return emptyList()
    val sections = mutableListOf<DiffSection>()
    var currentPath = ""
    var current = StringBuilder()

    fun flush() {
        if (current.isNotEmpty()) {
            sections.add(DiffSection(currentPath.ifBlank { "(unknown)" }, current.toString().trimEnd()))
        }
        current = StringBuilder()
    }

    for (line in diff.split("\n")) {
        if (line.startsWith("diff --git ")) {
            flush()
            // Prefer the b/ side (the new path); fall back to whatever is there.
            val fromB = line.substringAfter(" b/", "").trim()
            currentPath = fromB.ifBlank { line.removePrefix("diff --git ").trim() }
        }
        current.appendLine(line)
    }
    flush()
    return sections
}

@Composable
fun CommitItem(
    hash: String,
    msg: String,
    author: String,
    timeAgo: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OceanDark800),
        border = androidx.compose.foundation.BorderStroke(1.dp, OceanBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Commit,
                contentDescription = null,
                tint = OceanIceSky,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = msg,
                    color = OceanIceWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (author.isNotBlank() || timeAgo.isNotBlank()) {
                    Row(modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            text = hash,
                            color = OceanSteel,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (author.isNotBlank()) {
                            Text(
                                text = " • $author",
                                color = OceanMuted,
                                fontSize = 11.sp
                            )
                        }
                        if (timeAgo.isNotBlank()) {
                            Text(
                                text = " • $timeAgo",
                                color = OceanMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
