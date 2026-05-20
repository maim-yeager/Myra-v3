package com.myra.assistant.ai

import com.myra.assistant.model.AppCommand
import com.myra.assistant.model.CommandType

class CommandParser {

    fun parse(text: String): AppCommand? {
        val lowerText = text.lowercase().trim()

        return when {
            // App commands
            lowerText.contains("youtube") && (lowerText.contains("kholo") || lowerText.contains("open")) ->
                AppCommand(CommandType.OPEN_APP, mapOf("app" to "com.google.android.youtube"))
            lowerText.contains("whatsapp") && (lowerText.contains("kholo") || lowerText.contains("open")) ->
                AppCommand(CommandType.OPEN_APP, mapOf("app" to "com.whatsapp"))
            lowerText.contains("instagram") && (lowerText.contains("kholo") || lowerText.contains("open")) ->
                AppCommand(CommandType.OPEN_APP, mapOf("app" to "com.instagram.android"))
            lowerText.contains("chrome") && (lowerText.contains("kholo") || lowerText.contains("open")) ->
                AppCommand(CommandType.OPEN_APP, mapOf("app" to "com.android.chrome"))
            lowerText.contains("settings") && (lowerText.contains("kholo") || lowerText.contains("open")) ->
                AppCommand(CommandType.OPEN_APP, mapOf("app" to "com.android.settings"))

            // Close app
            lowerText.contains("close") || lowerText.contains("band") ->
                AppCommand(CommandType.CLOSE_APP)

            // Volume control
            lowerText.contains("volume") && (lowerText.contains("up") || lowerText.contains("barhao") || lowerText.contains("increase")) ->
                AppCommand(CommandType.VOLUME_UP)
            lowerText.contains("volume") && (lowerText.contains("down") || lowerText.contains("koro") || lowerText.contains("decrease")) ->
                AppCommand(CommandType.VOLUME_DOWN)

            // Flashlight
            lowerText.contains("torch") && (lowerText.contains("on") || lowerText.contains("on koro")) ->
                AppCommand(CommandType.FLASHLIGHT_ON)
            lowerText.contains("torch") && (lowerText.contains("off") || lowerText.contains("off koro")) ->
                AppCommand(CommandType.FLASHLIGHT_OFF)

            // WiFi
            lowerText.contains("wifi") && (lowerText.contains("on") || lowerText.contains("on koro")) ->
                AppCommand(CommandType.WIFI_ON)
            lowerText.contains("wifi") && (lowerText.contains("off") || lowerText.contains("off koro")) ->
                AppCommand(CommandType.WIFI_OFF)

            // Bluetooth
            lowerText.contains("bluetooth") && (lowerText.contains("on") || lowerText.contains("on koro")) ->
                AppCommand(CommandType.BLUETOOTH_ON)
            lowerText.contains("bluetooth") && (lowerText.contains("off") || lowerText.contains("off koro")) ->
                AppCommand(CommandType.BLUETOOTH_OFF)

            // Battery check
            lowerText.contains("battery") && (lowerText.contains("check") || lowerText.contains("bolo")) ->
                AppCommand(CommandType.CHECK_BATTERY)

            // Time
            lowerText.contains("time") && (lowerText.contains("bolo") || lowerText.contains("what") || lowerText.contains("check")) ->
                AppCommand(CommandType.GET_TIME)

            // Date
            lowerText.contains("date") && (lowerText.contains("bolo") || lowerText.contains("what") || lowerText.contains("check")) ->
                AppCommand(CommandType.GET_DATE)

            // Music control
            lowerText.contains("gaan") && (lowerText.contains("chalao") || lowerText.contains("play")) ->
                AppCommand(CommandType.MUSIC_PLAY)
            lowerText.contains("gaan") && (lowerText.contains("bondho") || lowerText.contains("stop")) ->
                AppCommand(CommandType.MUSIC_STOP)
            lowerText.contains("next") && (lowerText.contains("gaan") || lowerText.contains("song")) ->
                AppCommand(CommandType.MUSIC_NEXT)
            lowerText.contains("previous") && (lowerText.contains("gaan") || lowerText.contains("song")) ->
                AppCommand(CommandType.MUSIC_PREV)
            lowerText.contains("pause") || lowerText.contains("pause koro") ->
                AppCommand(CommandType.MUSIC_PAUSE)
            lowerText.contains("resume") || lowerText.contains("resume koro") ->
                AppCommand(CommandType.MUSIC_RESUME)

            // Alarm
            lowerText.contains("alarm") && (lowerText.contains("deo") || lowerText.contains("set") || lowerText.contains("lagabo")) -> {
                val time = extractTime(lowerText)
                AppCommand(CommandType.SET_ALARM, mapOf("time" to time))
            }

            // Reminder
            lowerText.contains("reminder") && (lowerText.contains("deo") || lowerText.contains("set") || lowerText.contains("mone")) -> {
                val minutes = extractMinutes(lowerText)
                AppCommand(CommandType.SET_REMINDER, mapOf("minutes" to minutes))
            }

            // Location
            lowerText.contains("kothai") && (lowerText.contains("achi") || lowerText.contains("ache")) ->
                AppCommand(CommandType.GET_LOCATION)
            lowerText.contains("nearest") || lowerText.contains("nearby") -> {
                val place = extractPlace(lowerText)
                AppCommand(CommandType.SEARCH_NEARBY, mapOf("place" to place))
            }

            // Notifications
            lowerText.contains("notification") && (lowerText.contains("poro") || lowerText.contains("read")) ->
                AppCommand(CommandType.READ_NOTIFICATIONS)

            // Photo
            lowerText.contains("photo") && (lowerText.contains("tolo") || lowerText.contains("take")) ->
                AppCommand(CommandType.TAKE_PHOTO)

            // Home/Back
            lowerText.contains("app close") || lowerText.contains("home") ->
                AppCommand(CommandType.GO_HOME)
            lowerText.contains("back") || lowerText.contains("piche") ->
                AppCommand(CommandType.GO_BACK)

            // Screenshot
            lowerText.contains("screenshot") || lowerText.contains("screen shot") ->
                AppCommand(CommandType.SCREENSHOT)

            // Game
            lowerText.contains("game") && (lowerText.contains("chalao") || lowerText.contains("start") || lowerText.contains("khelbo")) ->
                AppCommand(CommandType.START_GAME)

            // Expense
            lowerText.contains("expense") || lowerText.contains("khorsho") || lowerText.contains("expense add") -> {
                val amount = extractAmount(lowerText)
                AppCommand(CommandType.ADD_EXPENSE, mapOf("amount" to amount))
            }

            else -> null
        }
    }

    private fun extractTime(text: String): String {
        val hourPattern = Regex("(\\d+)\\s*(?:টা|hour)")
        val match = hourPattern.find(text)
        return match?.groupValues?.get(1) ?: "7"
    }

    private fun extractMinutes(text: String): String {
        val minutePattern = Regex("(\\d+)\\s*মিনিট|(\\d+)\\s*minute")
        val match = minutePattern.find(text)
        return match?.groupValues?.firstOrNull { it.isNotEmpty() } ?: "30"
    }

    private fun extractPlace(text: String): String {
        val placePattern = Regex("nearest\\s+(\\w+)|(\\w+)\\s+khujo")
        val match = placePattern.find(text)
        return match?.groupValues?.firstOrNull { it.isNotEmpty() && it != match.value } ?: "restaurant"
    }

    private fun extractAmount(text: String): String {
        val amountPattern = Regex("(\\d+)\\s*টাকা|(\\d+)\\s*taka|(\\d+)\\s*tk|\\$?(\\d+)")
        val match = amountPattern.find(text)
        return match?.groupValues?.firstOrNull { it.isNotEmpty() && it.all { c -> c.isDigit() } } ?: "0"
    }
}