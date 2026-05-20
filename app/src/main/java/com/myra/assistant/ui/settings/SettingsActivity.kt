package com.myra.assistant.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.myra.assistant.R
import com.myra.assistant.databinding.ActivitySettingsBinding
import com.myra.assistant.model.PrimeContact
import com.myra.assistant.service.AccessibilityHelperService

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var contactsAdapter: PrimeContactAdapter

    private val geminiModels = listOf(
        "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "models/gemini-2.0-flash-live-001",
        "models/gemini-2.5-flash-preview-native-audio-dialog"
    )

    private val geminiModelLabels = listOf(
        "Native Audio (Human Voice) - Default",
        "Flash Live (Fast)",
        "Pro Audio Dialog"
    )

    private val geminiVoices = listOf("Aoede", "Charon", "Kore", "Fenrir", "Puck", "Leda", "Orus", "Zephyr")
    private val openAIVoices = listOf("nova", "alloy", "echo", "fable", "onyx", "shimmer")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)

        loadSettings()
        setupViews()
        updateAccessibilityStatus()
    }

    private fun loadSettings() {
        val provider = prefs.getString("ai_provider", "gemini")
        if (provider == "gemini") {
            binding.radioGemini.isChecked = true
        } else {
            binding.radioOpenAI.isChecked = true
        }

        binding.apiKeyInput.setText(prefs.getString("api_key", ""))
        binding.userNameInput.setText(prefs.getString("user_name", ""))
        binding.wakeWordInput.setText(prefs.getString("wake_word", "Myra"))

        binding.offlineSwitch.isChecked = prefs.getBoolean("offline_mode_enabled", false)
        binding.notificationSwitch.isChecked = prefs.getBoolean("notification_reader_enabled", false)

        val personality = prefs.getString("personality_mode", "gf")
        when (personality) {
            "gf" -> binding.radioGF.isChecked = true
            "professional" -> binding.radioProfessional.isChecked = true
            "assistant" -> binding.radioAssistant.isChecked = true
        }
    }

    private fun setupViews() {
        binding.providerGroup.setOnCheckedChangeListener { _, checkedId ->
            val isGemini = checkedId == R.id.radioGemini
            updateSpinners(if (isGemini) "gemini" else "openai")
        }

        updateSpinners(prefs.getString("ai_provider", "gemini") ?: "gemini")

        binding.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {}
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {}
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        contactsAdapter = PrimeContactAdapter(
            getPrimeContacts(),
            { contact -> deleteContact(contact) }
        )
        binding.contactsRecycler.layoutManager = LinearLayoutManager(this)
        binding.contactsRecycler.adapter = contactsAdapter

        binding.addContactBtn.setOnClickListener { showAddContactDialog() }

        binding.accessibilityStatus.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.saveBtn.setOnClickListener { saveSettings() }
    }

    private fun updateSpinners(provider: String) {
        if (provider == "gemini") {
            val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, geminiModelLabels)
            modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.modelSpinner.adapter = modelAdapter

            val savedModel = prefs.getString("gemini_model", geminiModels[0])
            val modelIndex = geminiModels.indexOf(savedModel).coerceAtLeast(0)
            binding.modelSpinner.setSelection(modelIndex)

            val voiceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, geminiVoices)
            voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.voiceSpinner.adapter = voiceAdapter

            val savedVoice = prefs.getString("gemini_voice", "Aoede")
            val voiceIndex = geminiVoices.indexOf(savedVoice).coerceAtLeast(0)
            binding.voiceSpinner.setSelection(voiceIndex)
        } else {
            val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("gpt-4o-realtime-preview-2024-12-17"))
            modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.modelSpinner.adapter = modelAdapter

            val voiceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, openAIVoices)
            voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.voiceSpinner.adapter = voiceAdapter

            val savedVoice = prefs.getString("openai_voice", "nova")
            val voiceIndex = openAIVoices.indexOf(savedVoice).coerceAtLeast(0)
            binding.voiceSpinner.setSelection(voiceIndex)
        }
    }

    private fun getPrimeContacts(): List<PrimeContact> {
        val json = prefs.getString("prime_contacts_json", "[]")
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                PrimeContact(obj.getString("name"), obj.getString("number"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun savePrimeContacts(contacts: List<PrimeContact>) {
        val array = org.json.JSONArray()
        contacts.forEach { contact ->
            val obj = org.json.JSONObject().apply {
                put("name", contact.name)
                put("number", contact.number)
            }
            array.put(obj)
        }
        prefs.edit().putString("prime_contacts_json", array.toString()).apply()
    }

    private fun showAddContactDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.contactNameInput)
        val numberInput = dialogView.findViewById<android.widget.EditText>(R.id.contactNumberInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_contact)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { _, _ ->
                val name = nameInput.text.toString()
                val number = numberInput.text.toString()
                if (name.isNotEmpty() && number.isNotEmpty()) {
                    val contacts = getPrimeContacts().toMutableList()
                    contacts.add(PrimeContact(name, number))
                    savePrimeContacts(contacts)
                    contactsAdapter.updateContacts(contacts)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteContact(contact: PrimeContact) {
        val contacts = getPrimeContacts().toMutableList()
        contacts.remove(contact)
        savePrimeContacts(contacts)
        contactsAdapter.updateContacts(contacts)
    }

    private fun updateAccessibilityStatus() {
        val isEnabled = AccessibilityHelperService.isEnabled()
        binding.accessibilityStatus.text = if (isEnabled) {
            "✅ ${getString(R.string.enabled)}"
        } else {
            "❌ ${getString(R.string.disabled)}"
        }
        binding.accessibilityStatus.setTextColor(
            if (isEnabled) getColor(R.color.success) else getColor(R.color.primary_accent)
        )
    }

    private fun saveSettings() {
        val provider = if (binding.radioGemini.isChecked) "gemini" else "openai"
        prefs.edit()
            .putString("ai_provider", provider)
            .putString("api_key", binding.apiKeyInput.text.toString())
            .putString("user_name", binding.userNameInput.text.toString())
            .putString("wake_word", binding.wakeWordInput.text.toString())
            .putBoolean("offline_mode_enabled", binding.offlineSwitch.isChecked)
            .putBoolean("notification_reader_enabled", binding.notificationSwitch.isChecked)
            .apply()

        if (provider == "gemini") {
            prefs.edit()
                .putString("gemini_model", geminiModels[binding.modelSpinner.selectedItemPosition])
                .putString("gemini_voice", geminiVoices[binding.voiceSpinner.selectedItemPosition])
                .apply()
        } else {
            prefs.edit()
                .putString("openai_voice", openAIVoices[binding.voiceSpinner.selectedItemPosition])
                .apply()
        }

        val personality = when {
            binding.radioGF.isChecked -> "gf"
            binding.radioProfessional.isChecked -> "professional"
            else -> "assistant"
        }
        prefs.edit().putString("personality_mode", personality).apply()

        com.myra.assistant.service.NotificationReaderService.setEnabled(binding.notificationSwitch.isChecked)

        Toast.makeText(this, getString(R.string.restart_app), Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }
}