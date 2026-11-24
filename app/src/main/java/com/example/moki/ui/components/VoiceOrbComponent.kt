package com.example.moki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.moki.voice.VoiceState

@Composable
fun VoiceOrbComponent(
    voiceState: VoiceState,
    onTapOrb: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = voiceState !is VoiceState.Idle && voiceState !is VoiceState.WakeWordArmed

    val statusText = when (voiceState) {
        is VoiceState.Idle -> "Ready — tap mic or say \"Hey Moki\""
        is VoiceState.WakeWordArmed -> "Listening for \"Hey Moki\""
        is VoiceState.SpeakingAck -> voiceState.phrase
        is VoiceState.ListeningCommand -> voiceState.partialText.ifEmpty { "Listening..." }
        is VoiceState.Thinking -> "Thinking..."
        is VoiceState.SpeakingResponse -> "Speaking..."
    }

    val accentColor = when (voiceState) {
        is VoiceState.ListeningCommand -> Color(0xFF3B82F6)
        is VoiceState.SpeakingAck, is VoiceState.SpeakingResponse -> Color(0xFFF59E0B)
        is VoiceState.Thinking -> Color(0xFF8B5CF6)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable { onTapOrb() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                    contentDescription = "Voice",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}