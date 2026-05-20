package com.myra.assistant.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationReaderService : NotificationListenerService() {

    companion object {
        var instance: NotificationReaderService? = null
        private var isEnabled = false
        private var onNotificationReceived: ((String) -> Unit)? = null

        fun setEnabled(enabled: Boolean) {
            isEnabled = enabled
        }

        fun isAutoReadEnabled(): Boolean = isEnabled

        fun setOnNotificationReceivedListener(listener: (String) -> Unit) {
            onNotificationReceived = listener
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        if (!isEnabled) return

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isNotEmpty() || text.isNotEmpty()) {
            val appName = getAppName(packageName)
            val notificationText = "Notification from $appName: $title. $text"
            onNotificationReceived?.invoke(notificationText)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun getAppName(packageName: String): String {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}