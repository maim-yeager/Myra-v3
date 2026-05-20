package com.myra.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.ServiceInfo
import android.view.accessibility.AccessibilityEvent

class AccessibilityHelperService : AccessibilityService() {

    companion object {
        var instance: AccessibilityHelperService? = null

        fun isEnabled(): Boolean {
            return instance != null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_DEFAULT or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
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
        val nodeInfo = rootInActiveWindow
        if (nodeInfo == null) return false

        val findNode = nodeInfo.findAccessibilityNodeInfosByText(text)
        if (findNode.isNotEmpty()) {
            val node = findNode.first()
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                val parent = node.parent
                return parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            }
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val nodeInfo = rootInActiveWindow ?: return false
        val editTexts = nodeInfo.findAccessibilityNodeInfosByText("")
        for (node in editTexts) {
            if (node.isEditable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_ARGUMENT, text)
                })
            }
        }
        return false
    }

    fun scrollDown(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_SCROLL_FORWARD)
    }

    fun scrollUp(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_SCROLL_BACKWARD)
    }
}