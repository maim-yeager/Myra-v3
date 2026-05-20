package com.myra.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class PowerButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val DOUBLE_PRESS_TIMEOUT = 600L
        private var lastPressTime = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF || intent.action == Intent.ACTION_SCREEN_ON) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastPressTime < DOUBLE_PRESS_TIMEOUT) {
                if (!MyraOverlayService.isOverlayRunning()) {
                    MyraOverlayService.start(context)
                } else {
                    MyraOverlayService.stop(context)
                }
            }
            lastPressTime = currentTime
        }
    }
}