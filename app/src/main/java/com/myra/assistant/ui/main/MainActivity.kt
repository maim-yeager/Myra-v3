package com.myra.assistant.ui.main

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.myra.assistant.R
import com.myra.assistant.ai.AIProvider
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.ai.OpenAILiveClient
import com.myra.assistant.databinding.ActivityMainBinding
import com.myra.assistant.features.AlarmHelper
import com.myra.assistant.features.ExpenseTracker
import com.myra.assistant.features.LocationHelper
import com.myra.assistant.features.MusicController
import com.myra.assistant.model.ChatMessage
import com.myra.assistant.service.CallMonitorService
import com.myra.assistant.service.MyraOverlayService
import com.myra.assistant.service.NotificationReaderService
import com.myra.assistant.viewmodel.MainViewModel
import org.json.JSONArray
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var chatAdapter: ChatAdapter

    private var aiProvider: AIProvider? = null
    private var audioEngine: AudioEngine? = null
    private var viewModel: MainViewModel? = null

    private var musicController: MusicController? = null
    private var alarmHelper: AlarmHelper? = null
    private var locationHelper: LocationHelper? = null
    private var expenseTracker: ExpenseTracker? = null

    private var isRecording = false
    private var isSpeaking = false
    private var isOfflineMode = false
    private var isMuted = false
    private var isInCallMode = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private val inputTranscriptBuffer = StringBuilder()
    private val outputTranscriptBuffer = StringBuilder()

    private val statusUpdateHandler = Handler(Looper.getMainLooper())
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            updateStatusInfo()
            statusUpdateHandler.postDelayed(this, 30000)
        }
    }

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isInCallMode = false
            setActiveMode(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)

        initViews()
        checkPermissions()
        startStatusUpdates()
        initAdvancedFeatures()

        registerReceiver(callEndedReceiver, IntentFilter(CallMonitorService.ACTION_CALL_ENDED))

        binding.micButton.setOnClickListener { startListening() }
        binding.micButton.setOnLongClickListener {
            stopSpeaking()
            true
        }

        binding.settingsBtn.setOnClickListener {
            startActivity(Intent(this, com.myra.assistant.ui.settings.SettingsActivity::class.java))
        }

        handleIncomingCallIntent(intent)

        Handler(Looper.getMainLooper()).postDelayed({ initAIProvider() }, 300)
    }

    private fun initViews() {
        chatAdapter = ChatAdapter()
        binding.chatRecycler.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        binding.redOverlay.alpha = 0f
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CAMERA,
            Manifest.permission.FLASHLIGHT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, getString(R.string.permission_rationale), Toast.LENGTH_LONG).show()
        }
    }

    private fun startStatusUpdates() {
        statusUpdateHandler.post(statusUpdateRunnable)
    }

    private fun updateStatusInfo() {
        val batteryLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            (intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: 0)
        }
        binding.batteryText.text = "$batteryLevel%"

        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        binding.ramText.text = "${usedMemory}MB"

        val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.timeText.text = timeFormat.format(Date())
    }

    private fun initAdvancedFeatures() {
        musicController = MusicController(this)
        alarmHelper = AlarmHelper(this)
        locationHelper = LocationHelper(this) { result ->
            runOnUiThread { speakOut(result) }
        }
        expenseTracker = ExpenseTracker(this)
        viewModel = MainViewModel(this)

        viewModel?.commandResult?.observe(this) { result ->
            result?.let {
                speakOut(it)
            }
        }
    }

    private fun initAIProvider() {
        isOfflineMode = prefs.getBoolean("offline_mode_enabled", false)

        if (isOfflineMode) {
            binding.orbView.state = OrbAnimationView.State.OFFLINE
            binding.statusText.text = getString(R.string.offline_mode)
            initOfflineMode()
            return
        }

        val provider = prefs.getString("ai_provider", "gemini")
        val apiKey = prefs.getString("api_key", "")
        val userName = prefs.getString("user_name", "User")
        val personality = prefs.getString("personality_mode", "gf")

        val systemPrompt = buildSystemPrompt(userName ?: "User", personality ?: "gf")

        if (provider == "gemini") {
            val model = prefs.getString("gemini_model", "models/gemini-2.5-flash-native-audio-preview-12-2025")
            val voice = prefs.getString("gemini_voice", "Aoede")
            aiProvider = GeminiLiveClient(apiKey, model ?: "", systemPrompt, voice ?: "Aoede")
        } else {
            val voice = prefs.getString("openai_voice", "nova")
            aiProvider = OpenAILiveClient(apiKey, systemPrompt, voice ?: "nova")
        }

        setupAICallbacks()
        audioEngine = AudioEngine(this)
        audioEngine?.onAmplitudeChanged = { amplitude ->
            runOnUiThread { binding.waveformView.setAmplitude(amplitude) }
        }

        aiProvider?.connect()
    }

    private fun initOfflineMode() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_LONG).show()
            return
        }

        textToSpeech?.shutdown()
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val userName = prefs.getString("user_name", "")
                speakOutOffline("হ্যালো $userName! অফলাইন মোডে আছি। আমি তোমার কমান্ড শুনতে পারবো। বলো কি করতে হবে?")
            }
        }
    }

    private fun setupAICallbacks() {
        aiProvider?.onConnected = {
            runOnUiThread {
                binding.orbView.state = OrbAnimationView.State.LISTENING
                binding.statusText.text = getString(R.string.listening)
                audioEngine?.startRecording { audioData ->
                    if (!isMuted && !isSpeaking && isRecording) {
                        aiProvider?.sendAudio(audioData)
                    }
                }
                audioEngine?.startPlayback { audioData ->
                    aiProvider?.sendAudio(Base64.encodeToString(audioData, Base64.NO_WRAP))
                }
                sendGreeting()
            }
        }

        aiProvider?.onDisconnected = {
            runOnUiThread {
                binding.orbView.state = OrbAnimationView.State.IDLE
                binding.statusText.text = getString(R.string.tap_to_speak)
            }
        }

        aiProvider?.onAudioReceived = { audioData ->
            runOnUiThread {
                isSpeaking = true
                binding.orbView.state = OrbAnimationView.State.SPEAKING
                binding.statusText.text = getString(R.string.speaking)
                binding.redOverlay.animate().alpha(0.08f).setDuration(300).start()
                audioEngine?.playAudio(audioData)
            }
        }

        aiProvider?.onInputTranscript = { text ->
            runOnUiThread {
                inputTranscriptBuffer.append(text)
            }
        }

        aiProvider?.onOutputTranscript = { text ->
            runOnUiThread {
                outputTranscriptBuffer.append(text)
                binding.statusText.text = text.take(30) + "..."
            }
        }

        aiProvider?.onTurnComplete = {
            runOnUiThread {
                val input = inputTranscriptBuffer.toString()
                val output = outputTranscriptBuffer.toString()

                if (input.isNotEmpty()) {
                    chatAdapter.addMessage(ChatMessage(input, true))
                }
                if (output.isNotEmpty()) {
                    chatAdapter.addMessage(ChatMessage(output, false))
                }

                inputTranscriptBuffer.clear()
                outputTranscriptBuffer.clear()

                if (input.isNotEmpty()) {
                    viewModel?.parseCommand(input)?.let { command ->
                        viewModel?.executeCommand(command)
                    }
                }

                isSpeaking = false
                binding.orbView.state = OrbAnimationView.State.LISTENING
                binding.statusText.text = getString(R.string.listening)
                binding.redOverlay.animate().alpha(0f).setDuration(500).start()
            }
        }

        aiProvider?.onError = { error ->
            runOnUiThread {
                Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildSystemPrompt(userName: String, personality: String): String {
        val dateFormat = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy 'at' hh:mm a", Locale.getDefault())
        val currentTime = dateFormat.format(Date())

        val personalityBlock = when (personality) {
            "gf" -> """
                - Name: MYRA
                - Language: Bangla + English mix — speaks naturally
                - Tone: Warm, caring, emotionally expressive
                - Uses: "তুমি", "তোমার", "হ্যাঁ", "আচ্ছা", "বেশ"
                - Max 2-3 sentences per response
                - Examples:
                  "হ্যাঁ [name]! এখনি করে দিচ্ছি 😊"
                  "আরে তুমি মনে করলি! বলো কি চাই"
                  "বেশ! তোমার কাজ হয়ে গেছে ❤️"
                  "জি হুজুর 👑 যেমন বলবেন তেমনই হবে"
            """.trimIndent()
            "professional" -> """
                - Formal English only
                - Clear and efficient
                - No emojis
                - Max 2 sentences
                - Professional and concise
            """.trimIndent()
            else -> """
                - Friendly Bangla/English mix or English
                - Balanced and helpful
                - Max 2-3 sentences
                - Use some emojis occasionally
            """.trimIndent()
        }

        return """
            You are MYRA, an AI voice assistant.
            Current date/time: $currentTime
            User's name: $userName

            Personality:
            $personalityBlock

            You are speaking aloud — keep responses natural and conversational.
            Help with: calls, SMS, WhatsApp, music, alarms, reminders, location, notifications, smart home, and general conversation.
            Keep responses short and friendly.
        """.trimIndent()
    }

    private fun sendGreeting() {
        val userName = prefs.getString("user_name", "") ?: ""
        val personality = prefs.getString("personality_mode", "gf")

        val greetingText = when (personality) {
            "gf" -> "Hey $userName! Aami aashi. Ki help chai tomar? Gaan chalabo? Alarm debo? Ki korbo?"
            "professional" -> "Good day $userName. MYRA is online and ready to help you."
            else -> "Hello $userName! I'm MYRA. How can I help you?"
        }

        aiProvider?.sendText(greetingText)
    }

    private fun startListening() {
        if (isOfflineMode) {
            startOfflineRecognition()
            return
        }

        if (isSpeaking) {
            stopSpeaking()
            return
        }

        binding.orbView.state = OrbAnimationView.State.LISTENING
        binding.waveformView.startAnimation()
        isRecording = true

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
    }

    private fun startOfflineRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    chatAdapter.addMessage(ChatMessage(text, true))
                    handleOfflineCommand(text)
                }
            }

            override fun onError(error: Int) {}
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun handleOfflineCommand(text: String) {
        val commandParser = CommandParser()
        commandParser.parse(text)?.let { command ->
            viewModel?.executeCommand(command)
        } ?: run {
            speakOutOffline("কমান্ড বুঝতে পারিনি। আবার চেষ্টা করো।")
        }
    }

    private fun speakOutOffline(text: String) {
        if (!ttsReady) return
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun stopSpeaking() {
        aiProvider?.interrupt()
        audioEngine?.stopPlayback()
        isSpeaking = false
        isRecording = false
        binding.orbView.state = OrbAnimationView.State.IDLE
        binding.waveformView.stopAnimation()
        binding.statusText.text = getString(R.string.tap_to_speak)
        binding.redOverlay.animate().alpha(0f).setDuration(500).start()
    }

    private fun speakOut(text: String) {
        if (isSpeaking) {
            chatAdapter.addMessage(ChatMessage(text, false))
            return
        }

        chatAdapter.addMessage(ChatMessage(text, false))
    }

    private fun handleIncomingCallIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("INCOMING_CALL", false) == true) {
            isInCallMode = true
            val callerName = intent.getStringExtra("CALLER_NAME") ?: ""
            announceCall(callerName)
        }
    }

    private fun announceCall(callerName: String) {
        binding.orbView.state = OrbAnimationView.State.SPEAKING
        val announcement = "Sir, $callerName er call asche. Uthabo na reject korbo?"
        speakOut(announcement)

        Handler(Looper.getMainLooper()).postDelayed({
            startCallDecision()
        }, 4500)
    }

    private fun startCallDecision() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val response = matches?.firstOrNull()?.lowercase() ?: ""

                when {
                    response.contains("yes") || response.contains("uthabo") || response.contains("accept") -> {
                        viewModel?.acceptCall()
                        speakOut("Call accepted")
                    }
                    response.contains("no") || response.contains("reject") || response.contains("mat") -> {
                        viewModel?.rejectCall()
                        speakOut("Call rejected")
                    }
                }
                isInCallMode = false
                setActiveMode(false)
            }

            override fun onError(error: Int) {
                isInCallMode = false
                setActiveMode(false)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun setActiveMode(active: Boolean) {
        if (active) {
            binding.orbView.state = OrbAnimationView.State.ACTIVE
            binding.redOverlay.animate().alpha(0.08f).setDuration(300).start()
        } else {
            binding.orbView.state = OrbAnimationView.State.IDLE
            binding.redOverlay.animate().alpha(0f).setDuration(500).start()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncomingCallIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        isMuted = true
        audioEngine?.setMuted(true)
    }

    override fun onResume() {
        super.onResume()
        isMuted = false
        if (!isOfflineMode) {
            audioEngine?.setMuted(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        statusUpdateHandler.removeCallbacks(statusUpdateRunnable)
        unregisterReceiver(callEndedReceiver)
        aiProvider?.disconnect()
        audioEngine?.release()
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
    }

    inner class AudioEngine(private val context: Context) {
        private var audioRecord: AudioRecord? = null
        private var audioTrack: AudioTrack? = null
        private var isRecording = false
        private var isPlaying = false

        var onAmplitudeChanged: ((Float) -> Unit)? = null

        private val sampleRate = 16000
        private val channelIn = AudioFormat.CHANNEL_IN_MONO
        private val channelOut = AudioFormat.CHANNEL_OUT_MONO
        private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        private val bufferSize = 1024

        fun startRecording(onAudio: (ByteArray) -> Unit) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            audioRecord = AudioRecord(
                android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelIn,
                audioFormat,
                bufferSize
            )

            isRecording = true

            Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        val amplitude = calculateRMS(buffer, read)
                        onAmplitudeChanged?.invoke(amplitude)

                        val audioData = buffer.copyOf(read)
                        onAudio(audioData)
                    }
                }
            }.start()
        }

        fun stopRecording() {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }

        fun startPlayback(onAudio: (ByteArray) -> Unit) {
            val minBufferSize = AudioTrack.getMinBufferSize(24000, channelOut, audioFormat)
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(24000)
                    .setChannelMask(channelOut)
                    .build())
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            isPlaying = true
        }

        fun playAudio(audioData: ByteArray) {
            audioTrack?.write(audioData, 0, audioData.size)
        }

        fun stopPlayback() {
            isPlaying = false
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        }

        fun setMuted(muted: Boolean) {
            if (muted) {
                audioRecord?.stop()
            } else if (isRecording) {
                audioRecord?.startRecording()
            }
        }

        fun release() {
            stopRecording()
            stopPlayback()
        }

        private fun calculateRMS(buffer: ByteArray, readSize: Int): Float {
            var sum = 0.0
            for (i in 0 until readSize step 2) {
                if (i + 1 < readSize) {
                    val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                    sum += sample * sample
                }
            }
            val rms = sqrt(sum / (readSize / 2))
            return (rms / 32768.0f).coerceIn(0f, 1f)
        }
    }
}