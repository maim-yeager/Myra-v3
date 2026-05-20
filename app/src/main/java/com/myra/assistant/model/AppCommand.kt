package com.myra.assistant.model

data class AppCommand(
    val type: CommandType,
    val params: Map<String, String> = emptyMap()
)

enum class CommandType {
    OPEN_APP,
    CLOSE_APP,
    CALL,
    SMS,
    WHATSAPP_MSG,
    WHATSAPP_CALL,
    PRIME_CALL,
    PRIME_MSG,
    VOLUME_UP,
    VOLUME_DOWN,
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,
    WIFI_ON,
    WIFI_OFF,
    BLUETOOTH_ON,
    BLUETOOTH_OFF,
    CHECK_BATTERY,
    GET_TIME,
    GET_DATE,
    MUSIC_PLAY,
    MUSIC_STOP,
    MUSIC_NEXT,
    MUSIC_PREV,
    MUSIC_PAUSE,
    MUSIC_RESUME,
    SET_ALARM,
    SET_REMINDER,
    GET_LOCATION,
    SEARCH_NEARBY,
    READ_NOTIFICATIONS,
    TAKE_PHOTO,
    VIDEO_RECORD,
    ADD_EXPENSE,
    START_GAME,
    GO_HOME,
    GO_BACK,
    SCREENSHOT,
    UNKNOWN
}