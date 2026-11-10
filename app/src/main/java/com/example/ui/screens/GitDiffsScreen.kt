package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DiffViewer
import com.example.ui.theme.CyberAmethyst
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
fun GitDiffsScreen(
    viewModel: HarnessViewModel,
    modifier: Modifier = Modifier
) {
    val sampleActiveDiff = """
--- a/app/src/main/java/com/example/MainActivity.kt
+++ b/app/src/main/java/com/example/MainActivity.kt
@@ -19,4 +19,10 @@
         enableEdgeToEdge()
         setContent {
-            MyApplicationTheme {
+            AndroidHarnessTheme {
+                Scaffold(
+                    floatingActionButton = {
+                        ExtendedFloatingActionButton(
+                            onClick = { /* Autonomous agent run */ }
+                        )
+                    }
+                )
    """.trimIndent()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDark900)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Repo Overview Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateDark800),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateDark600),
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
                                tint = CyberAmethyst,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GIT REPOSITORY STATUS",
                                color = SlateWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(CyberAmethyst.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "branch: main",
                                color = CyberAmethyst,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1 Modified file: app/src/main/java/com/example/MainActivity.kt",
                        color = SlateLight,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.selectTab(0)
                            viewModel.sendPrompt("git status and review all uncommitted diffs")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberAmethyst),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Analyze Diff with Agent", color = Color(0xFF1E1136), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Active Diff Section
        item {
            Column {
                Text(
                    text = "ACTIVE WORKING TREE DIFF",
                    color = NeonEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                DiffViewer(diffContent = sampleActiveDiff)
            }
        }

        // Commit Log Section
        item {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = SlateMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RECENT COMMITS",
                        color = SlateMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                CommitItem(
                    hash = "4e9e6b3",
                    msg = "feat: Add LocalToolCallingEngine with ReAct and XML tool calling",
                    author = "AndroidHarness Agent",
                    timeAgo = "10 minutes ago"
                )
                Spacer(modifier = Modifier.height(6.dp))
                CommitItem(
                    hash = "8f12a9c",
                    msg = "chore: Scaffold Room persistence and virtual workspace filesystem",
                    author = "AndroidHarness Agent",
                    timeAgo = "45 minutes ago"
                )
                Spacer(modifier = Modifier.height(6.dp))
                CommitItem(
                    hash = "c1a8f90",
                    msg = "initial project template with Jetpack Compose M3",
                    author = "Developer",
                    timeAgo = "yesterday"
                )
            }
        }
    }
}

@Composable
fun CommitItem(
    hash: String,
    msg: String,
    author: String,
    timeAgo: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateDark800),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateDark600),
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
                tint = NeonEmerald,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = msg,
                    color = SlateWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = hash,
                        color = CyberAmethyst,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = " • $author • $timeAgo",
                        color = SlateMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
