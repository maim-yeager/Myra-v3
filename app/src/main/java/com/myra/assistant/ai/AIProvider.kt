package com.myra.assistant.ai

interface AIProvider {
    var onAudioReceived: ((ByteArray) -> Unit)?
    var onInputTranscript: ((String) -> Unit)?
    var onOutputTranscript: ((String) -> Unit)?
    var onTurnComplete: (() -> Unit)?
    var onConnected: (() -> Unit)?
    var onDisconnected: (() -> Unit)?
    var onError: ((String) -> Unit)?

    fun connect()
    fun disconnect()
    fun sendText(text: String)
    fun sendAudio(audioData: ByteArray)
    fun sendAudioCommit() {}
    fun interrupt()
    fun isConnected(): Boolean
}
