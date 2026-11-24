package com.example.moki.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MokiAccessibilityService : AccessibilityService() {

    private enum class MessengerTaskType {
        SEND_MESSAGE,
        AUDIO_CALL,
        VIDEO_CALL
    }

    private enum class MessengerStep {
        IDLE,
        FIND_CHAT_OR_SEARCH,
        TYPING_SEARCH,
        PICK_RESULT,
        EXECUTE_ACTION
    }

    private val pollHandler = Handler(Looper.getMainLooper())
    private var messengerStep = MessengerStep.IDLE
    private var pendingMessengerTask = MessengerTaskType.SEND_MESSAGE
    private var pendingMessengerRecipient: String? = null
    private var pendingMessengerText: String? = null
    private var lastMapsBannerText: String? = null
    private var lastActionTimestamp = 0L

    private val stepPoller = object : Runnable {
        override fun run() {
            if (messengerStep != MessengerStep.IDLE) {
                try {
                    val root = rootInActiveWindow
                    if (root != null && root.packageName?.toString() == "com.facebook.orca") {
                        processMessengerStateMachine(root)
                    }
                } catch (e: Exception) {
                    Log.w("MokiAccessibility", "Poller step skipped", e)
                }
                pollHandler.postDelayed(this, 700L)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            val pkg = event?.packageName?.toString() ?: return
            val root = rootInActiveWindow ?: return

            if (pkg == "com.google.android.apps.maps") {
                captureMapsBanner(root)
            }

            if (pkg == "com.facebook.orca" && messengerStep != MessengerStep.IDLE) {
                processMessengerStateMachine(root)
            }
        } catch (e: Exception) {
            Log.w("MokiAccessibility", "Event skipped safely", e)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        pollHandler.removeCallbacksAndMessages(null)
        instance = null
        super.onDestroy()
    }

    fun lockDeviceScreen(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun queueMessengerAutomation(recipient: String, text: String) {
        pendingMessengerTask = MessengerTaskType.SEND_MESSAGE
        pendingMessengerRecipient = recipient
        pendingMessengerText = text
        messengerStep = MessengerStep.FIND_CHAT_OR_SEARCH
        lastActionTimestamp = 0L
        pollHandler.removeCallbacks(stepPoller)
        pollHandler.postDelayed(stepPoller, 850L)
    }

    fun queueMessengerCall(recipient: String, isVideo: Boolean = false) {
        pendingMessengerTask = if (isVideo) MessengerTaskType.VIDEO_CALL else MessengerTaskType.AUDIO_CALL
        pendingMessengerRecipient = recipient
        pendingMessengerText = null
        messengerStep = MessengerStep.FIND_CHAT_OR_SEARCH
        lastActionTimestamp = 0L
        pollHandler.removeCallbacks(stepPoller)
        pollHandler.postDelayed(stepPoller, 850L)
    }

    fun readMapsTrafficBanner(): String? = lastMapsBannerText

    private fun processMessengerStateMachine(root: AccessibilityNodeInfo) {
        val recipient = pendingMessengerRecipient ?: return

        val now = System.currentTimeMillis()
        if (now - lastActionTimestamp < 650L) return

        val isCorrectThreadOpen = isRecipientHeaderVisible(root, recipient)
        if (isCorrectThreadOpen) {
            messengerStep = MessengerStep.EXECUTE_ACTION
        }

        when (messengerStep) {
            MessengerStep.FIND_CHAT_OR_SEARCH -> {
                if (hasBottomMessageComposer(root) && !isCorrectThreadOpen) {
                    lastActionTimestamp = now
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return
                }

                val visibleResult = findResultRowNode(root, recipient)
                if (visibleResult != null) {
                    lastActionTimestamp = now
                    clickNodeOrTapBounds(visibleResult)
                    messengerStep = MessengerStep.EXECUTE_ACTION
                    return
                }

                val searchBar = findNodeByTextOrDesc(root, listOf("Search", "Ask Meta AI or search"))
                if (searchBar != null) {
                    lastActionTimestamp = now
                    clickNodeOrTapBounds(searchBar)
                    messengerStep = MessengerStep.TYPING_SEARCH
                }
            }

            MessengerStep.TYPING_SEARCH -> {
                val searchEdit = findEditableNode(root)
                if (searchEdit != null) {
                    lastActionTimestamp = now
                    val searchQuery = recipient.split(" ").firstOrNull()?.takeIf { it.length >= 2 } ?: recipient
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            searchQuery
                        )
                    }
                    searchEdit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    messengerStep = MessengerStep.PICK_RESULT
                }
            }

            MessengerStep.PICK_RESULT -> {
                val resultNode = findResultRowNode(root, recipient)
                if (resultNode != null) {
                    lastActionTimestamp = now
                    clickNodeOrTapBounds(resultNode)
                    messengerStep = MessengerStep.EXECUTE_ACTION
                }
            }

            MessengerStep.EXECUTE_ACTION -> {
                if (!isCorrectThreadOpen) return

                when (pendingMessengerTask) {
                    MessengerTaskType.SEND_MESSAGE -> {
                        val message = pendingMessengerText ?: return
                        val composer = findEditableNode(root) ?: return
                        lastActionTimestamp = now
                        val args = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                message
                            )
                        }
                        composer.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

                        val sendBtn = findNodeByTextOrDesc(root, listOf("Send"))
                        if (sendBtn != null) {
                            clickNodeOrTapBounds(sendBtn)
                            resetMessengerState()
                        }
                    }

                    MessengerTaskType.AUDIO_CALL -> {
                        val callBtn = findTopActionButton(root, listOf("Audio call", "Voice call", "Call"))
                        if (callBtn != null) {
                            lastActionTimestamp = now
                            clickNodeOrTapBounds(callBtn)
                            resetMessengerState()
                        }
                    }

                    MessengerTaskType.VIDEO_CALL -> {
                        val videoBtn = findTopActionButton(root, listOf("Video call", "Start video call"))
                        if (videoBtn != null) {
                            lastActionTimestamp = now
                            clickNodeOrTapBounds(videoBtn)
                            resetMessengerState()
                        }
                    }
                }
            }

            MessengerStep.IDLE -> Unit
        }
    }

    private fun findResultRowNode(root: AccessibilityNodeInfo, recipient: String): AccessibilityNodeInfo? {
        val exactMatches = root.findAccessibilityNodeInfosByText(recipient).orEmpty()
        val firstToken = recipient.split(" ").firstOrNull()?.takeIf { it.length >= 2 }
        val tokenMatches = if (exactMatches.isEmpty() && firstToken != null) {
            root.findAccessibilityNodeInfosByText(firstToken).orEmpty()
        } else {
            emptyList()
        }

        return (exactMatches + tokenMatches).firstOrNull { node ->
            if (node == null || node.isEditable) return@firstOrNull false
            val label = node.text?.toString()?.trim().orEmpty()
            // Skip group-chat message preview snippets like "Jhon Carl: para special exam"
            if (label.contains(":") || label.startsWith("You:", ignoreCase = true) || label.equals("Connected", ignoreCase = true)) {
                return@firstOrNull false
            }
            val rect = Rect()
            node.getBoundsInScreen(rect)
            // Must be below the search bar + filter tabs (top > 330)
            rect.top > 330 && rect.width() > 4 && rect.height() > 4
        }
    }

    private fun clickNodeOrTapBounds(node: AccessibilityNodeInfo) {
        val clickable = findClickableParent(node)
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val cx = rect.exactCenterX()
        val cy = rect.exactCenterY()
        if (rect.width() > 0 && rect.height() > 0 && cx >= 0f && cy >= 0f) {
            val path = Path().apply { moveTo(cx, cy) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    private fun resetMessengerState() {
        messengerStep = MessengerStep.IDLE
        pendingMessengerTask = MessengerTaskType.SEND_MESSAGE
        pendingMessengerRecipient = null
        pendingMessengerText = null
        pollHandler.removeCallbacks(stepPoller)
    }

    private fun isRecipientHeaderVisible(root: AccessibilityNodeInfo, recipient: String): Boolean {
        if (!hasBottomMessageComposer(root)) return false
        val firstToken = recipient.split(" ").firstOrNull()?.takeIf { it.length >= 2 } ?: recipient
        val matches = root.findAccessibilityNodeInfosByText(firstToken) ?: return false
        return matches.any { it != null && !it.isEditable }
    }

    private fun hasBottomMessageComposer(root: AccessibilityNodeInfo): Boolean {
        val edit = findEditableNode(root) ?: return false
        val rect = Rect()
        edit.getBoundsInScreen(rect)
        val hint = edit.hintText?.toString().orEmpty()
        return rect.top > 300 || hint.contains("Message", ignoreCase = true) || hint.contains("Aa", ignoreCase = true)
    }

    private fun findTopActionButton(
        node: AccessibilityNodeInfo?,
        keywords: List<String>
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString().orEmpty()
        val text = node.text?.toString().orEmpty()
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.top in 0..320 && keywords.any { kw ->
                desc.contains(kw, ignoreCase = true) || text.contains(kw, ignoreCase = true)
            }
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findTopActionButton(node.getChild(i), keywords)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByTextOrDesc(
        node: AccessibilityNodeInfo?,
        targets: List<String>
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (targets.any { t -> text.equals(t, ignoreCase = true) || desc.equals(t, ignoreCase = true) }) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findNodeByTextOrDesc(node.getChild(i), targets)
            if (found != null) return found
        }
        return null
    }

    private fun captureMapsBanner(root: AccessibilityNodeInfo) {
        val keywords = listOf("min", "km", "faster", "slower", "traffic", "route", "via")
        val collected = mutableListOf<String>()
        traverseText(root, collected)
        val banner = collected.firstOrNull { line ->
            line.length in 6..90 && keywords.any { kw -> line.contains(kw, ignoreCase = true) }
        }
        if (banner != null) {
            lastMapsBannerText = banner
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun traverseText(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            traverseText(node.getChild(i), out)
        }
    }

    companion object {
        @Volatile
        var instance: MokiAccessibilityService? = null
            private set
    }
}