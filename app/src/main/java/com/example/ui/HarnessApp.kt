package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.domain.model.ProviderType
import com.example.ui.screens.AgentWorkspaceScreen
import com.example.ui.screens.DoctorDialog
import com.example.ui.screens.GitDiffsScreen
import com.example.ui.screens.ProjectFilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.OceanAqua
import com.example.ui.theme.OceanBorder
import com.example.ui.theme.OceanDark700
import com.example.ui.theme.OceanDark800
import com.example.ui.theme.OceanDark900
import com.example.ui.theme.OceanIceSky
import com.example.ui.theme.OceanIceWhite
import com.example.ui.theme.OceanMuted
import com.example.ui.theme.OceanSteel
import com.example.ui.theme.OceanWarning
import com.example.ui.viewmodel.HarnessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarnessApp(
    viewModel: HarnessViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isRunning by viewModel.isAgentRunning.collectAsStateWithLifecycle()

    var showDoctorDialog by remember { mutableStateOf(false) }

    // Handle back button on secondary screens
    if (selectedTab != 0) {
        BackHandler {
            viewModel.selectTab(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚡ AndroidHarness",
                            color = OceanIceWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // Active Engine status pill
                        Box(
                            modifier = Modifier
                                .background(OceanDark700, RoundedCornerShape(12.dp))
                                .border(1.dp, OceanSteel.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            if (isRunning) OceanAqua else OceanIceSky,
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (settings.providerType) {
                                        ProviderType.LOCAL_TOOL_LLM -> "LOCAL AGENT"
                                        ProviderType.FIREBASE_AI_LOGIC -> "FIREBASE"
                                        ProviderType.BYOK_CUSTOM -> "BYOK"
                                    },
                                    color = OceanIceWhite,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDoctorDialog = true },
                        modifier = Modifier.testTag("appbar_doctor_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = "Doctor Diagnostics",
                            tint = OceanAqua
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OceanDark800
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = OceanDark800,
                contentColor = OceanIceSky
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Agent") },
                    label = { Text("Agent") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OceanIceWhite,
                        selectedTextColor = OceanIceSky,
                        indicatorColor = OceanSteel.copy(alpha = 0.4f),
                        unselectedIconColor = OceanMuted,
                        unselectedTextColor = OceanMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_agent")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Files") },
                    label = { Text("Files") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OceanIceWhite,
                        selectedTextColor = OceanIceSky,
                        indicatorColor = OceanSteel.copy(alpha = 0.4f),
                        unselectedIconColor = OceanMuted,
                        unselectedTextColor = OceanMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_files")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.Difference, contentDescription = "Diffs") },
                    label = { Text("Git Diffs") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OceanIceWhite,
                        selectedTextColor = OceanIceSky,
                        indicatorColor = OceanSteel.copy(alpha = 0.4f),
                        unselectedIconColor = OceanMuted,
                        unselectedTextColor = OceanMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_diffs")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OceanIceWhite,
                        selectedTextColor = OceanIceSky,
                        indicatorColor = OceanSteel.copy(alpha = 0.4f),
                        unselectedIconColor = OceanMuted,
                        unselectedTextColor = OceanMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> AgentWorkspaceScreen(viewModel = viewModel)
                1 -> ProjectFilesScreen(viewModel = viewModel)
                2 -> GitDiffsScreen(viewModel = viewModel)
                3 -> SettingsScreen(
                    viewModel = viewModel,
                    onOpenDoctor = { showDoctorDialog = true }
                )
            }

            if (showDoctorDialog) {
                DoctorDialog(
                    viewModel = viewModel,
                    onDismiss = { showDoctorDialog = false }
                )
            }
        }
    }
}
