package com.example.moki.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.moki.data.preferences.EscalationMode
import com.example.moki.ui.MokiViewModel
import com.example.moki.ui.components.ModelDownloadCard
import com.example.moki.ui.components.PermissionChecklistCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: MokiViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val isDownloaded by viewModel.modelDownloaded.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    var geminiKeyInput by remember(settings.geminiApiKey) {
        mutableStateOf(settings.geminiApiKey)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ModelDownloadCard(
                isDownloaded = isDownloaded,
                progressPercent = downloadProgress,
                onDownloadClick = { viewModel.downloadLocalModel() }
            )

            PermissionChecklistCard()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Wake Word",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.startBackgroundWakeService() }) {
                            Text("Start Listening")
                        }
                        OutlinedButton(onClick = { viewModel.stopBackgroundWakeService() }) {
                            Text("Stop")
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AI Engine",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    EscalationMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = settings.escalationMode == mode,
                                onClick = { viewModel.setEscalationMode(mode) }
                            )
                            Text(
                                text = when (mode) {
                                    EscalationMode.LOCAL_ONLY -> "On-Device Only"
                                    EscalationMode.GEMINI_APP_HANDOFF -> "Gemini App Handoff"
                                    EscalationMode.CLOUD_API -> "Gemini API"
                                }
                            )
                        }
                    }
                    if (settings.escalationMode == EscalationMode.CLOUD_API) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = geminiKeyInput,
                            onValueChange = { geminiKeyInput = it },
                            label = { Text("API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.saveGeminiApiKey(geminiKeyInput) }) {
                            Text("Save")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { viewModel.clearChatHistory() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Chat History")
            }
        }
    }
}