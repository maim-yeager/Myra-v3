package com.myra.assistant.ai

import android.util.Base64
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private val apiKey: String,
    private val model: String,
    private val systemPrompt: String,
    private val voiceName: String = "Aoede"
) : AIProvider {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var isConnected = false
    private var sessionRenewJob: Job? = null
    private var keepAliveJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val emptyAudioChunk = ByteArray(1024) { 0 }

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
            sendSetupMessage()
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
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()
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
        val message = JSONObject().apply {
            put("client_content", JSONObject().apply {
                put("turns", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", text)
                    }))
                }))
                put("turn_complete", true)
            })
        }
        webSocket?.send(message.toString())
    }

    override fun sendAudio(audioData: ByteArray) {
        if (!isConnected) return
        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        val message = JSONObject().apply {
            put("realtime_input", JSONObject().apply {
                put("media_chunks", JSONArray().put(JSONObject().apply {
                    put("mime_type", "audio/pcm;rate=16000")
                    put("data", base64Audio)
                }))
            })
        }
        webSocket?.send(message.toString())
    }

    override fun interrupt() {
        if (!isConnected) return
        val message = JSONObject().apply {
            put("client_content", JSONObject().apply {
                put("turns", JSONArray())
                put("turn_complete", true)
            })
        }
        webSocket?.send(message.toString())
    }

    override fun isConnected(): Boolean = isConnected

    private fun sendSetupMessage() {
        val setupMessage = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", model)
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", systemPrompt)
                    }))
                })
                put("generation_config", JSONObject().apply {
                    put("response_modalities", JSONArray().put("AUDIO"))
                    put("speech_config", JSONObject().apply {
                        put("voice_config", JSONObject().apply {
                            put("prebuilt_voice_config", JSONObject().apply {
                                put("voice_name", voiceName)
                            })
                        })
                    })
                    put("temperature", 0.9)
                })
                put("output_audio_transcription", JSONObject())
                put("input_audio_transcription", JSONObject())
            })
        }
        webSocket?.send(setupMessage.toString())
    }

    private fun parseMessage(text: String) {
        try {
            val json = JSONObject(text)
            val serverContent = json.optJSONObject("serverContent") ?: return

            val modelTurn = serverContent.optJSONObject("modelTurn")
            if (modelTurn != null) {
                val parts = modelTurn.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val audioData = inlineData.optString("data")
                            if (audioData.isNotEmpty()) {
                                val decoded = Base64.decode(audioData, Base64.DEFAULT)
                                onAudioReceived?.invoke(decoded)
                            }
                        }
                    }
                }
            }

            val outputTranscription = serverContent.optJSONObject("outputTranscription")
            if (outputTranscription != null) {
                val t = outputTranscription.optString("text")
                if (t.isNotEmpty()) onInputTranscript?.invoke(t)
            }

            val inputTranscription = serverContent.optJSONObject("inputTranscription")
            if (inputTranscription != null) {
                val t = inputTranscription.optString("text")
                if (t.isNotEmpty()) onOutputTranscript?.invoke(t)
            }

            if (serverContent.optBoolean("turnComplete", false)) {
                onTurnComplete?.invoke()
            }
        } catch (e: Exception) {
            onError?.invoke("Parse error: ${e.message}")
        }
    }

    private fun startSessionRenewTimer() {
        sessionRenewJob?.cancel()
        sessionRenewJob = scope.launch {
            delay(540_000)
            if (isConnected) { disconnect(); delay(3000); connect() }
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isConnected) {
                delay(8000)
                if (isConnected) sendAudio(emptyAudioChunk)
            }
        }
    }

    private fun stopTimers() {
        sessionRenewJob?.cancel()
        keepAliveJob?.cancel()
    }
}
