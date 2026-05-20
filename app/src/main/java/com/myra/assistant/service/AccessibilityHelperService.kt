package com.myra.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityHelperService : AccessibilityService() {

    companion object {
        var instance: AccessibilityHelperService? = null
        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun closeCurrentApp() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun clickOnText(text: String): Boolean {
        val nodeInfo = rootInActiveWindow ?: return false
        val nodes = nodeInfo.findAccessibilityNodeInfosByText(text)
        if (nodes.isNotEmpty()) {
            val node = nodes.first()
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                return node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            }
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val nodeInfo = rootInActiveWindow ?: return false
        val editTexts = nodeInfo.findAccessibilityNodeInfosByText("")
        for (node in editTexts) {
            if (node.isEditable) {
                return node.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
                        )
                    }
                )
            }
        }
        return false
    }

    fun scrollDown(): Boolean {
        val nodeInfo = rootInActiveWindow ?: return false
        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollUp(): Boolean {
        val nodeInfo = rootInActiveWindow ?: return false
        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }
}
