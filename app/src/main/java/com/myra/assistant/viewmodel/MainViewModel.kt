package com.myra.assistant.viewmodel

import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.model.AppCommand
import com.myra.assistant.model.CommandType
import com.myra.assistant.service.AccessibilityHelperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(private val context: Context) : ViewModel() {

    private val commandParser = CommandParser()
    val commandResult = androidx.lifecycle.MutableLiveData<String?>()

    private val appPackageMap = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "twitter" to "com.twitter.android",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "settings" to "com.android.settings",
        "calculator" to "com.android.calculator2",
        "calendar" to "com.android.calendar",
        "clock" to "com.google.android.deskclock",
        "phone" to "com.android.dialer",
        "contacts" to "com.android.contacts",
        "play store" to "com.android.vending",
        "amazon" to "com.amazon.mShop.android.shopping",
        "flipkart" to "com.flipkart.android",
        "paytm" to "net.one97.paytm",
        "phonepe" to "com.phonepe.app",
        "gpay" to "com.google.android.apps.nearby.payments",
        "zoom" to "us.zoom.videomeetings",
        "meet" to "com.google.android.apps.tachyon",
        "teams" to "com.microsoft.teams",
        "tiktok" to "com.zhiliaoapp.musically",
        "discord" to "com.discord",
        "linkedin" to "com.linkedin.android"
    )

    fun parseCommand(text: String): AppCommand? = commandParser.parse(text)

    fun executeCommand(command: AppCommand) {
        viewModelScope.launch(Dispatchers.IO) {
            when (command.type) {
                CommandType.OPEN_APP -> openApp(command.params["app"] ?: "")
                CommandType.CLOSE_APP -> closeApp()
                CommandType.CALL -> callContact(command.params["name"] ?: "")
                CommandType.SMS -> sendSms(command.params["name"] ?: "", command.params["message"] ?: "")
                CommandType.WHATSAPP_CALL -> openWhatsApp(command.params["number"] ?: "", "")
                CommandType.WHATSAPP_MSG -> openWhatsApp(command.params["number"] ?: "", command.params["message"] ?: "")
                CommandType.PRIME_CALL -> callPrimeContact(command.params["index"]?.toIntOrNull() ?: 0)
                CommandType.PRIME_MSG -> sendSmsToPrime(command.params["index"]?.toIntOrNull() ?: 1, "Hello")
                CommandType.VOLUME_UP -> adjustVolume(10)
                CommandType.VOLUME_DOWN -> adjustVolume(-10)
                CommandType.FLASHLIGHT_ON -> toggleFlashlight(true)
                CommandType.FLASHLIGHT_OFF -> toggleFlashlight(false)
                CommandType.WIFI_ON -> toggleWifi(true)
                CommandType.WIFI_OFF -> toggleWifi(false)
                CommandType.BLUETOOTH_ON -> toggleBluetooth(true)
                CommandType.BLUETOOTH_OFF -> toggleBluetooth(false)
                CommandType.CHECK_BATTERY -> checkBattery()
                CommandType.GET_TIME -> getTime()
                CommandType.GET_DATE -> getDate()
                CommandType.GO_HOME -> goHome()
                CommandType.GO_BACK -> goBack()
                CommandType.SCREENSHOT -> takeScreenshot()
                else -> commandResult.postValue("Command not supported")
            }
        }
    }

    private suspend fun openApp(appName: String) {
        withContext(Dispatchers.IO) {
            val packageName = appPackageMap[appName.lowercase()] ?: findAppByName(appName)
            if (packageName != null) {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    commandResult.postValue("Opened $appName")
                } else commandResult.postValue("Cannot open $appName")
            } else commandResult.postValue("App not found: $appName")
        }
    }

    private fun findAppByName(name: String): String? {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .find { it.loadLabel(pm).toString().lowercase().contains(name.lowercase()) }
            ?.packageName
    }

    private fun closeApp() {
        if (AccessibilityHelperService.isEnabled()) {
            AccessibilityHelperService.instance?.closeCurrentApp()
            commandResult.postValue("App closed")
        } else commandResult.postValue("Enable accessibility service to close apps")
    }

    private suspend fun callContact(name: String) {
        withContext(Dispatchers.IO) {
            val number = findContactNumber(name)
            if (number != null) {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$number")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    commandResult.postValue("Calling $name")
                } catch (e: Exception) {
                    commandResult.postValue("Cannot make call")
                }
            } else commandResult.postValue("Contact not found: $name")
        }
    }

    private fun findContactNumber(name: String): String? {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, selection, selectionArgs, null
            )
            cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) { null }
    }

    private suspend fun sendSms(name: String, message: String) {
        withContext(Dispatchers.IO) {
            val number = findContactNumber(name)
            if (number != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("smsto:$number")
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                commandResult.postValue("SMS to $name")
            } else commandResult.postValue("Contact not found: $name")
        }
    }

    private fun openWhatsApp(number: String, message: String) {
        try {
            val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
            val clean = if (number.startsWith("+")) number.substring(1) else number
            val uri = Uri.parse("https://wa.me/$clean?text=$encodedMessage")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            commandResult.postValue("Opening WhatsApp")
        } catch (e: Exception) { commandResult.postValue("Cannot open WhatsApp") }
    }

    private fun callPrimeContact(index: Int) {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        try {
            val contacts = org.json.JSONArray(prefs.getString("prime_contacts_json", "[]"))
            if (index < contacts.length()) {
                val contact = contacts.getJSONObject(index)
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:${contact.getString("number")}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                commandResult.postValue("Calling ${contact.getString("name")}")
            } else commandResult.postValue("Prime contact not found")
        } catch (e: Exception) { commandResult.postValue("No prime contacts set") }
    }

    private fun sendSmsToPrime(index: Int, message: String) {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        try {
            val contacts = org.json.JSONArray(prefs.getString("prime_contacts_json", "[]"))
            if (index < contacts.length()) {
                val contact = contacts.getJSONObject(index)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("smsto:${contact.getString("number")}")
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                commandResult.postValue("SMS to ${contact.getString("name")}")
            } else commandResult.postValue("Prime contact not found")
        } catch (e: Exception) { commandResult.postValue("No prime contacts set") }
    }

    private fun adjustVolume(delta: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (current + delta).coerceIn(0, max), 0)
        commandResult.postValue("Volume ${if (delta > 0) "increased" else "decreased"}")
    }

    private fun toggleFlashlight(on: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, on)
                commandResult.postValue("Flashlight ${if (on) "on" else "off"}")
            }
        } catch (e: Exception) { commandResult.postValue("Cannot control flashlight") }
    }

    private fun toggleWifi(on: Boolean) {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = on
            commandResult.postValue("WiFi ${if (on) "enabled" else "disabled"}")
        } catch (e: Exception) { commandResult.postValue("Cannot control WiFi") }
    }

    private fun toggleBluetooth(on: Boolean) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                if (on) @Suppress("DEPRECATION") adapter.enable()
                else @Suppress("DEPRECATION") adapter.disable()
                commandResult.postValue("Bluetooth ${if (on) "enabled" else "disabled"}")
            }
        } catch (e: Exception) { commandResult.postValue("Cannot control Bluetooth") }
    }

    private fun checkBattery() {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        commandResult.postValue("Battery level: $level%${if (isCharging) " and charging" else ""}")
    }

    private fun getTime() {
        commandResult.postValue("Current time is ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())}")
    }

    private fun getDate() {
        commandResult.postValue("Today's date is ${SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())}")
    }

    private fun goHome() {
        if (AccessibilityHelperService.isEnabled()) {
            AccessibilityHelperService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            commandResult.postValue("Going to home screen")
        } else commandResult.postValue("Enable accessibility service for this action")
    }

    private fun goBack() {
        if (AccessibilityHelperService.isEnabled()) {
            AccessibilityHelperService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            commandResult.postValue("Pressed back")
        } else commandResult.postValue("Enable accessibility service for this action")
    }

    private fun takeScreenshot() {
        if (AccessibilityHelperService.isEnabled()) {
            AccessibilityHelperService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            commandResult.postValue("Screenshot taken")
        } else commandResult.postValue("Enable accessibility service for screenshots")
    }

    fun acceptCall() {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION")
            telecomManager.acceptRingingCall()
            commandResult.postValue("Call accepted")
        } catch (e: Exception) { commandResult.postValue("Cannot accept call") }
    }

    fun rejectCall() {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION")
            telecomManager.endCall()
            commandResult.postValue("Call rejected")
        } catch (e: Exception) { commandResult.postValue("Cannot reject call") }
    }
}
