package com.example.moki

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.moki.ui.MokiViewModel
import com.example.moki.ui.navigation.MokiNavHost

class MainActivity : ComponentActivity() {

    private var assistTriggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        assistTriggered = intent?.action == Intent.ACTION_ASSIST

        setContent {
            val mokiColorScheme = darkColorScheme(
                primary = Color(0xFF3B82F6),          // Crisp Electric Blue
                onPrimary = Color(0xFFFFFFFF),
                secondary = Color(0xFF60A5FA),
                tertiary = Color(0xFFF59E0B),         // Warm Amber for speaking/confirmations
                background = Color(0xFF09090B),       // Deep Obsidian
                surface = Color(0xFF141417),          // Elevated Zinc Card
                surfaceVariant = Color(0xFF1F1F24),
                onBackground = Color(0xFFFAFAFA),
                onSurface = Color(0xFFF4F4F5),
                onSurfaceVariant = Color(0xFFA1A1AA),
                outline = Color(0xFF27272A)
            )
            MaterialTheme(colorScheme = mokiColorScheme) {
                val viewModel: MokiViewModel = viewModel()
                MokiNavHost(
                    viewModel = viewModel,
                    autoListenOnStart = assistTriggered
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_ASSIST) {
            setIntent(intent)
        }
    }
}