package com.myra.assistant.ai

import android.util.Base64
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAILiveClient(
    private val apiKey: String,
    private val systemPrompt: String,
    private val voice: String = "nova"
) : AIProvider {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var isConnected = false
    private var sessionRenewJob: Job? = null
    private var keepAliveJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var responseId: String? = null
    private var itemId: String? = null

    override var onAudioReceived: ((ByteArray) -> Unit)? = null
    override var onInputTranscript: ((String) -> Unit)? = null
    override var onOutputTranscript: ((String) -> Unit)? = null
    override var onTurnComplete: (() -> Unit)? = null
    override var onConnected: (() -> Unit)? = null
    override var onDisconnected: (() -> Unit)? = null
    override var onError: ((String) -> Unit)? = null

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isConnected = true
            sendSessionUpdate()
            startSessionRenewTimer()
            startKeepAlive()
            onConnected?.invoke()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            parseMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isConnected = false
            stopTimers()
            onDisconnected?.invoke()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            isConnected = false
            stopTimers()
            onError?.invoke(t.message ?: "Connection failed")
            onDisconnected?.invoke()
        }
    }

    override fun connect() {
        val url = "wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-12-17"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "realtime=v1")
            .build()
        webSocket = client.newWebSocket(request, webSocketListener)
    }

    override fun disconnect() {
        stopTimers()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        isConnected = false
    }

    override fun sendText(text: String) {
        if (!isConnected) return

        val messageId = "msg_${System.currentTimeMillis()}"
        itemId = messageId

        val createItem = JSONObject().apply {
            put("type", "conversation.item.create")
            put("item", JSONObject().apply {
                put("type", "message")
                put("role", "user")
                put("content", JSONArray().put(JSONObject().apply {
                    put("type", "input_text")
                    put("text", text)
                }))
            })
        }
        webSocket?.send(createItem.toString())

        val responseCreate = JSONObject().apply {
            put("type", "response.create")
        }
        webSocket?.send(responseCreate.toString())
    }

    override fun sendAudio(audioData: ByteArray) {
        if (!isConnected) return
        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        val message = JSONObject().apply {
            put("type", "input_audio_buffer.append")
            put("audio", base64Audio)
        }
        webSocket?.send(message.toString())
    }

    override fun sendAudioCommit() {
        if (!isConnected) return
        val message = JSONObject().apply {
            put("type", "input_audio_buffer.commit")
        }
        webSocket?.send(message.toString())
    }

    override fun interrupt() {
        if (!isConnected) return
        val message = JSONObject().apply {
            put("type", "response.cancel")
        }
        webSocket?.send(message.toString())
    }

    override fun isConnected(): Boolean = isConnected

    private fun sendSessionUpdate() {
        val sessionUpdate = JSONObject().apply {
            put("type", "session.update")
            put("session", JSONObject().apply {
                put("modalities", JSONArray().put("text").put("audio"))
                put("instructions", systemPrompt)
                put("voice", voice)
                put("input_audio_format", "pcm16")
                put("output_audio_format", "pcm16")
                put("input_audio_transcription", JSONObject().apply {
                    put("enabled", true)
                })
                put("temperature", 0.9)
            })
        }
        webSocket?.send(sessionUpdate.toString())
    }

    private fun parseMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")

            when (type) {
                "response.audio.delta" -> {
                    val delta = json.optString("delta")
                    if (delta.isNotEmpty()) {
                        val decoded = Base64.decode(delta, Base64.DEFAULT)
                        onAudioReceived?.invoke(decoded)
                    }
                }
                "response.audio_transcript.delta" -> {
                    val delta = json.optString("delta")
                    if (delta.isNotEmpty()) {
                        onOutputTranscript?.invoke(delta)
                    }
                }
                "input_audio_buffer.speech_started" -> {
                    // User started speaking
                }
                "input_audio_buffer.speech_stopped" -> {
                    sendAudioCommit()
                }
                "conversation.item.created" -> {
                    val item = json.optJSONObject("item")
                    if (item != null) {
                        val content = item.optJSONArray("content")
                        if (content != null && content.length() > 0) {
                            for (i in 0 until content.length()) {
                                val c = content.getJSONObject(i)
                                if (c.optString("type") == "input_audio") {
                                    val transcript = c.optString("transcript")
                                    if (transcript.isNotEmpty()) {
                                        onInputTranscript?.invoke(transcript)
                                    }
                                }
                            }
                        }
                    }
                }
                "response.done" -> {
                    onTurnComplete?.invoke()
                }
                "error" -> {
                    val error = json.optString("message")
                    onError?.invoke(error)
                }
            }
        } catch (e: Exception) {
            onError?.invoke("Parse error: ${e.message}")
        }
    }

    private fun startSessionRenewTimer() {
        sessionRenewJob?.cancel()
        sessionRenewJob = scope.launch {
            delay(540_000) // 9 minutes
            if (isConnected) {
                disconnect()
                delay(3000)
                connect()
            }
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isConnected) {
                delay(8000) // 8 seconds
                if (isConnected) {
                    // Send minimal audio buffer to keep connection alive
                    val emptyMessage = JSONObject().apply {
                        put("type", "input_audio_buffer.append")
                        put("audio", "")
                    }
                    webSocket?.send(emptyMessage.toString())
                }
            }
        }
    }

    private fun stopTimers() {
        sessionRenewJob?.cancel()
        keepAliveJob?.cancel()
    }
}