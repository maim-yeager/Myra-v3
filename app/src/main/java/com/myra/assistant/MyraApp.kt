package com.myra.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MyraApp : Application() {

    companion object {
        const val CHANNEL_ID_OVERLAY = "myra_overlay_channel"
        const val CHANNEL_ID_CALL = "myra_call_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val overlayChannel = NotificationChannel(
                CHANNEL_ID_OVERLAY,
                getString(R.string.notification_channel_overlay),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MYRA Overlay Service"
                setShowBadge(false)
            }

            val callChannel = NotificationChannel(
                CHANNEL_ID_CALL,
                getString(R.string.notification_channel_call),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Call Monitor Service"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(overlayChannel)
            notificationManager.createNotificationChannel(callChannel)
        }
    }
}