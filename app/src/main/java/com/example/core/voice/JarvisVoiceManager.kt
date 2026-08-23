package com.example.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.core.model.AssistantLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class JarvisVoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechAmplitude = MutableStateFlow(0f)
    val speechAmplitude: StateFlow<Float> = _speechAmplitude.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private var currentLanguage = AssistantLanguage.ENGLISH

    var onSpeechResultCallback: ((String) -> Unit)? = null

    init {
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            setLanguage(currentLanguage)
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    startSpeakingAnimation()
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _speechAmplitude.value = 0f
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _speechAmplitude.value = 0f
                }
            })
        }
    }

    fun setLanguage(language: AssistantLanguage) {
        currentLanguage = language
        if (!isTtsReady || textToSpeech == null) return

        val locale = when (language) {
            AssistantLanguage.ENGLISH -> Locale.US
            AssistantLanguage.TAMIL -> Locale("ta", "IN")
            AssistantLanguage.TANGLISH -> Locale("ta", "IN")
        }

        val result = textToSpeech?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to English if specific Tamil voice engine is absent on device
            textToSpeech?.setLanguage(Locale.US)
        }
    }

    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
    }

    fun startListening(onResult: (String) -> Unit) {
        stopSpeaking()
        onSpeechResultCallback = onResult
        _recognizedText.value = ""

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            // Fallback simulated input for emulators without speech recognizer service
            simulateVoiceInput("JARVIS, give me a full system briefing and latest AI research.", onResult)
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                when (currentLanguage) {
                    AssistantLanguage.ENGLISH -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    AssistantLanguage.TAMIL -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
                    AssistantLanguage.TANGLISH -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US", "ta-IN"))
                    }
                }
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {
                    _isListening.value = true
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
                    _speechAmplitude.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                    _speechAmplitude.value = 0f
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _speechAmplitude.value = 0f
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _speechAmplitude.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        _recognizedText.value = text
                        onSpeechResultCallback?.invoke(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        _recognizedText.value = text
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            _isListening.value = false
            simulateVoiceInput("JARVIS, analyze system diagnostics and memory matrix.", onResult)
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isListening.value = false
        _speechAmplitude.value = 0f
    }

    fun speak(text: String, utteranceId: String = "jarvis_speech_${System.currentTimeMillis()}") {
        if (!isTtsReady || textToSpeech == null) return
        stopSpeaking()

        // Clean out formatting asterisks, markdown, brackets for smooth TTS
        val cleanedText = text
            .replace("**", "")
            .replace("#", "")
            .replace("`", "")
            .replace("\\[.*?\\]".toRegex(), "")
            .replace("https?://\\S+".toRegex(), "web link")

        textToSpeech?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isSpeaking.value = false
        _speechAmplitude.value = 0f
    }

    private fun startSpeakingAnimation() {
        coroutineScope.launch {
            while (_isSpeaking.value) {
                _speechAmplitude.value = (0.3f + Math.random().toFloat() * 0.65f)
                delay(80)
            }
            _speechAmplitude.value = 0f
        }
    }

    private fun simulateVoiceInput(sampleText: String, onResult: (String) -> Unit) {
        _isListening.value = true
        coroutineScope.launch {
            for (i in 1..6) {
                _speechAmplitude.value = (0.3f + Math.random().toFloat() * 0.5f)
                delay(120)
            }
            _isListening.value = false
            _speechAmplitude.value = 0f
            _recognizedText.value = sampleText
            onResult(sampleText)
        }
    }

    fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
