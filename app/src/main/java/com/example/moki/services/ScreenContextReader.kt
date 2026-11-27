package com.example.moki.services

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

data class ScreenSnapshot(
    val packageName: String,
    val visibleText: String,
    val hasEditableField: Boolean
)

object ScreenContextReader {

    private const val MAX_DEPTH = 28
    private const val MAX_CHARS = 3500

    fun captureActiveScreen(): ScreenSnapshot? {
        val service = MokiAccessibilityService.instance ?: return null
        val root = findTargetAppRoot(service) ?: return null

        val pkg = root.packageName?.toString().orEmpty()
        if (pkg.isBlank() || pkg == "com.example.moki") {
            return null
        }

        val lines = LinkedHashSet<String>()
        var editableFound = false

        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > MAX_DEPTH) return
            if (!node.isVisibleToUser) return

            if (node.isEditable) {
                editableFound = true
            }

            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()

            if (text.length in 2..600 && !isNoiseLabel(text)) {
                lines.add(text)
            } else if (desc.length in 3..250 && !isNoiseLabel(desc) && !node.isClickable) {
                lines.add(desc)
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i), depth + 1)
            }
        }

        traverse(root, 0)

        val combined = lines.joinToString("\n").take(MAX_CHARS).trim()
        if (combined.isBlank()) return null

        return ScreenSnapshot(
            packageName = pkg,
            visibleText = combined,
            hasEditableField = editableFound
        )
    }

    fun pasteIntoActiveInput(draftText: String): Boolean {
        val service = MokiAccessibilityService.instance ?: return false
        val root = findTargetAppRoot(service) ?: return false

        val targetNode = findBestEditableNode(root) ?: return false

        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                draftText
            )
        }
        return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findTargetAppRoot(service: MokiAccessibilityService): AccessibilityNodeInfo? {
        // 1. Check active window first if it isn't Moki's own overlay
        val activeRoot = try {
            service.rootInActiveWindow
        } catch (_: Exception) {
            null
        }
        val activePkg = activeRoot?.packageName?.toString().orEmpty()
        if (activeRoot != null && activePkg.isNotBlank() && activePkg != "com.example.moki") {
            return activeRoot
        }

        // 2. When MokiFloatingOverlay is visible, scan windows for the foreground application underneath
        val windows = try {
            service.windows
        } catch (_: Exception) {
            null
        }.orEmpty()

        return windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { window ->
                try {
                    window.root
                } catch (_: Exception) {
                    null
                }
            }
            .firstOrNull { root ->
                val pkg = root.packageName?.toString().orEmpty()
                pkg.isNotBlank() && pkg != "com.example.moki" && pkg != "com.android.systemui"
            }
    }

    private fun findBestEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val editables = mutableListOf<AccessibilityNodeInfo>()

        fun collect(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > MAX_DEPTH) return
            if (node.isVisibleToUser && node.isEditable) {
                editables.add(node)
            }
            for (i in 0 until node.childCount) {
                collect(node.getChild(i), depth + 1)
            }
        }

        collect(root, 0)
        if (editables.isEmpty()) return null

        editables.firstOrNull { it.isFocused }?.let { return it }

        return editables.maxByOrNull { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            rect.top + rect.height()
        }
    }

    private fun isNoiseLabel(value: String): Boolean {
        val lower = value.lowercase()
        return lower in setOf(
            "back", "navigate up", "more options", "search", "close",
            "send", "attach", "emoji", "microphone", "camera", "home", "overview"
        )
    }
}