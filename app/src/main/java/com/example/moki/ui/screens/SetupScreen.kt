package com.example.moki.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.moki.brain.providers.CloudProviderType
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

    val selectedProvider = settings.activeCloudProvider
    var providerKeyInput by remember(selectedProvider, settings.apiKeyFor(selectedProvider)) {
        mutableStateOf(settings.apiKeyFor(selectedProvider))
    }
    var modelOverrideInput by remember(settings.cloudModelOverride) {
        mutableStateOf(settings.cloudModelOverride)
    }
    var showApiKey by remember { mutableStateOf(false) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var savedBanner by remember { mutableStateOf(false) }

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
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = settings.escalationMode == mode,
                                    onClick = { viewModel.setEscalationMode(mode) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = settings.escalationMode == mode,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (mode) {
                                    EscalationMode.LOCAL_ONLY -> "On-Device Only"
                                    EscalationMode.GEMINI_APP_HANDOFF -> "Gemini App Handoff"
                                    EscalationMode.CLOUD_API -> "Cloud API (Multi-Model)"
                                }
                            )
                        }
                    }

                    if (settings.escalationMode == EscalationMode.CLOUD_API) {
                        Spacer(modifier = Modifier.height(12.dp))

                        ExposedDropdownMenuBox(
                            expanded = providerMenuExpanded,
                            onExpandedChange = { providerMenuExpanded = !providerMenuExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedProvider.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Cloud Provider") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = providerMenuExpanded,
                                onDismissRequest = { providerMenuExpanded = false }
                            ) {
                                CloudProviderType.entries.forEach { provider ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(provider.displayName)
                                                Text(
                                                    text = "Default: ${provider.defaultModel}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            providerMenuExpanded = false
                                            savedBanner = false
                                            viewModel.setActiveCloudProvider(provider)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = providerKeyInput,
                            onValueChange = {
                                providerKeyInput = it
                                savedBanner = false
                            },
                            label = { Text("${selectedProvider.displayName} API Key") },
                            placeholder = { Text(selectedProvider.hintPlaceholder) },
                            singleLine = true,
                            visualTransformation = if (showApiKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) {
                                            Icons.Rounded.VisibilityOff
                                        } else {
                                            Icons.Rounded.Visibility
                                        },
                                        contentDescription = if (showApiKey) "Hide API Key" else "Show API Key"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = modelOverrideInput,
                            onValueChange = {
                                modelOverrideInput = it
                                savedBanner = false
                            },
                            label = { Text("Model ID (optional)") },
                            placeholder = { Text(selectedProvider.defaultModel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.saveCloudProviderConfig(
                                        provider = selectedProvider,
                                        apiKey = providerKeyInput,
                                        modelOverride = modelOverrideInput
                                    )
                                    savedBanner = true
                                }
                            ) {
                                Text("Save")
                            }
                            if (savedBanner) {
                                Text(
                                    text = "Saved",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
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