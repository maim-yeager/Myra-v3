package com.myra.assistant.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.myra.assistant.MyraApp
import com.myra.assistant.R
import com.myra.assistant.ui.main.MainActivity

class CallMonitorService : Service() {

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null

    companion object {
        const val ACTION_CALL_ENDED = "com.myra.CALL_ENDED"
        private var instance: CallMonitorService? = null

        fun start(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallMonitorService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForeground(1, createNotification())
        setupPhoneStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, MyraApp.CHANNEL_ID_CALL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.call_monitor_running))
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun setupPhoneStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        if (!phoneNumber.isNullOrEmpty()) {
                            val callerName = resolveCallerName(phoneNumber)
                            notifyIncomingCall(callerName ?: phoneNumber, phoneNumber)
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        sendBroadcast(Intent(ACTION_CALL_ENDED))
                    }
                }
            }
        }

        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun resolveCallerName(phoneNumber: String): String? {
        try {
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberToSearch = if (phoneNumber.startsWith("+")) {
                phoneNumber.substring(3)
            } else {
                phoneNumber
            }

            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                arrayOf("%$numberToSearch%"),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        return it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun notifyIncomingCall(name: String, number: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("INCOMING_CALL", true)
            putExtra("CALLER_NAME", name)
            putExtra("CALLER_NUMBER", number)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        instance = null
    }
}