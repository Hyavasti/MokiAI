package com.example.moki.ui.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.moki.MokiApplication
import com.example.moki.voice.MokiVoiceService
import com.example.moki.voice.VoiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MokiFloatingOverlay(private val service: AccessibilityService) {

    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null

    private var bubbleX = 24
    private var bubbleY = 260

    val isShowing: Boolean
        get() = composeView != null

    fun toggle() {
        if (isShowing) dismiss() else show()
    }

    fun show() {
        if (composeView != null) return

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        lifecycleOwner = owner

        val params = createExpandedParams()
        overlayLayoutParams = params

        val view = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)

            setContent {
                var isExpanded by remember { mutableStateOf(true) }

                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFF3B82F6),
                        surface = Color(0xFF141417),
                        surfaceVariant = Color(0xFF1F1F24),
                        onSurface = Color(0xFFF4F4F5),
                        onSurfaceVariant = Color(0xFFA1A1AA),
                        outline = Color(0xFF27272A)
                    )
                ) {
                    if (isExpanded) {
                        MokiBottomPopupSheet(
                            onMinimize = {
                                isExpanded = false
                                applyWindowParams(createBubbleParams())
                            },
                            onInputFocusChanged = { focused ->
                                updateFocusableFlag(focused)
                            },
                            onDismiss = { dismiss() }
                        )
                    } else {
                        MokiFloatingBubble(
                            onExpand = {
                                isExpanded = true
                                applyWindowParams(createExpandedParams())
                            },
                            onDrag = { dx, dy ->
                                val lp = overlayLayoutParams ?: return@MokiFloatingBubble
                                bubbleX = (lp.x - dx.roundToInt()).coerceAtLeast(0)
                                bubbleY = lp.y + dy.roundToInt()
                                lp.x = bubbleX
                                lp.y = bubbleY
                                try {
                                    windowManager.updateViewLayout(this, lp)
                                } catch (_: Exception) {
                                }
                            }
                        )
                    }
                }
            }
        }

        composeView = view
        windowManager.addView(view, params)
    }

    fun dismiss() {
        val view = composeView ?: return
        composeView = null

        try {
            view.disposeComposition()
            windowManager.removeViewImmediate(view)
        } catch (_: Exception) {
        }

        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        overlayLayoutParams = null
    }

    private fun createExpandedParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 32
        }
    }

    private fun createBubbleParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            x = bubbleX
            y = bubbleY
        }
    }

    private fun applyWindowParams(newParams: WindowManager.LayoutParams) {
        val view = composeView ?: return
        overlayLayoutParams = newParams
        try {
            windowManager.updateViewLayout(view, newParams)
        } catch (_: Exception) {
        }
    }

    private fun updateFocusableFlag(focusable: Boolean) {
        val view = composeView ?: return
        val lp = overlayLayoutParams ?: return
        lp.flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        try {
            windowManager.updateViewLayout(view, lp)
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun MokiFloatingBubble(
    onExpand: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val voiceState by MokiVoiceService.voiceState.collectAsState()
    val isListening = voiceState is VoiceState.ListeningCommand

    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(if (isListening) Color(0xFFF59E0B) else Color(0xFF141417))
            .border(1.5.dp, Color(0xFF3B82F6), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onExpand() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isListening) Icons.Rounded.Mic else Icons.Rounded.AutoAwesome,
            contentDescription = "Open Moki AI",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun MokiBottomPopupSheet(
    onMinimize: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val voiceState by MokiVoiceService.voiceState.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var statusReply by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    fun submitCommand(command: String) {
        val clean = command.trim()
        if (clean.isEmpty() || isRunning) return
        inputText = ""
        copied = false
        isRunning = true
        statusReply = "Thinking..."
        scope.launch {
            val result = MokiApplication.instance.brainRouter.processUserTurn(
                userUtterance = clean,
                isForeground = true
            )
            statusReply = result.spokenReply
            isRunning = false
            MokiVoiceService.speakText(MokiApplication.instance, result.spokenReply)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .border(1.dp, Color(0xFF27272A), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF7141417),
        tonalElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6))
                    )
                    Text(
                        text = "Moki AI",
                        color = Color(0xFFF4F4F5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Minimize to bubble",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close overlay",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { submitCommand("Summarize my screen") },
                    label = { Text("Summarize screen", fontSize = 12.sp) }
                )
                AssistChip(
                    onClick = { submitCommand("Draft a reply to this") },
                    label = { Text("Draft reply", fontSize = 12.sp) }
                )
            }

            val displayMessage = when (val state = voiceState) {
                is VoiceState.ListeningCommand ->
                    state.partialText.ifBlank { "Listening..." }
                is VoiceState.Thinking -> "Thinking..."
                is VoiceState.SpeakingResponse -> state.responseText
                else -> statusReply
            }

            if (!displayMessage.isNullOrBlank()) {
                val showCopyButton = !isRunning &&
                        displayMessage != "Thinking..." &&
                        displayMessage != "Listening..."

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1F1F24))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = displayMessage,
                            color = Color(0xFFE4E4E7),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }

                    if (showCopyButton) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(displayMessage))
                                    copied = true
                                    scope.launch {
                                        delay(2000L)
                                        copied = false
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy text",
                                    tint = if (copied) Color(0xFF34D399) else Color(0xFF60A5FA),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (copied) "Copied" else "Copy",
                                    fontSize = 12.sp,
                                    color = if (copied) Color(0xFF34D399) else Color(0xFF60A5FA)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onInputFocusChanged(it.isFocused) },
                    placeholder = {
                        Text("Ask Moki AI...", fontSize = 13.sp, color = Color(0xFF71717A))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitCommand(inputText) })
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            submitCommand(inputText)
                        } else {
                            MokiVoiceService.startService(
                                MokiApplication.instance,
                                MokiVoiceService.ACTION_LISTEN_DIRECT
                            )
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3B82F6))
                ) {
                    Icon(
                        imageVector = if (inputText.isNotBlank()) Icons.Rounded.Send else Icons.Rounded.Mic,
                        contentDescription = if (inputText.isNotBlank()) "Send" else "Speak",
                        tint = Color.White
                    )
                }
            }
        }
    }
}