package com.example.moki.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.moki.ui.MokiViewModel
import com.example.moki.ui.components.ActionConfirmCard
import com.example.moki.ui.components.ChatBubbleComponent
import com.example.moki.ui.components.VoiceOrbComponent
import com.example.moki.voice.VoiceState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MokiViewModel,
    onNavigateToSetup: () -> Unit
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.triggerSimulatedHeyMoki()
        }
    }

    fun handleOrbTap() {
        val isActiveTurn = voiceState is VoiceState.ListeningCommand ||
                voiceState is VoiceState.SpeakingAck ||
                voiceState is VoiceState.SpeakingResponse ||
                voiceState is VoiceState.Thinking

        if (isActiveTurn) {
            viewModel.stopListeningLoop()
            return
        }

        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.triggerSimulatedHeyMoki()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val reversedMessages = remember(messages) { messages.asReversed() }

    LaunchedEffect(messages.size) {
        if (reversedMessages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Moki",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.resetConversation() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Reset conversation",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToSetup) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            VoiceOrbComponent(
                voiceState = voiceState,
                onTapOrb = { handleOrbTap() }
            )

            Spacer(modifier = Modifier.height(6.dp))

            pendingConfirmation?.let { pending ->
                ActionConfirmCard(
                    confirmation = pending,
                    onConfirm = { viewModel.confirmAction() },
                    onCancel = { viewModel.cancelAction() }
                )
            }

            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(reversedMessages, key = { it.id }) { msg ->
                        ChatBubbleComponent(message = msg)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Type a message or say \"Hey Moki\"...") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (inputText.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            viewModel.sendTextPrompt(inputText)
                            inputText = ""
                        },
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}