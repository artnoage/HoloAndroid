package com.vaios.holobar

import android.content.Context
import android.util.Log
import java.io.IOException
import ai.onnxruntime.OrtException

class TextToAudio(private val context: Context) {
    companion object {
        private const val TAG = "TextToAudio"
        private const val VITS_MODEL_FILE = "vits_model.onnx"
        private const val PHONEMIZER_MODEL_FILE = "phonemizer_model.onnx"
        private const val API_KEY_FILE = "gemini_api_key.txt"

        // Hardcoded matrix for speaker ID mapping
        private val SPEAKER_ID_MATRIX = arrayOf(
                intArrayOf(0, 79),
                intArrayOf(1, 90),
                intArrayOf(2, 33),
                intArrayOf(3, 109),
                intArrayOf(4, 100)
        )
    }

    private lateinit var synthesizer: VitsOnnxSynthesizer
    private lateinit var apiCall: ApiCall

    init {
        initializeModels()
    }

    private fun initializeModels() {
        Log.d(TAG, "Initializing models")
        try {
            synthesizer = VitsOnnxSynthesizer(context, VITS_MODEL_FILE)
            Log.d(TAG, "VITS synthesizer initialized")

            Phonemizer.initialize(context.assets, PHONEMIZER_MODEL_FILE)
            Log.d(TAG, "Phonemizer initialized")

            val apiKey = loadApiKey()
            apiCall = ApiCall(context, apiKey)
            Log.d(TAG, "ApiCall initialized")

        } catch (e: IOException) {
            Log.e(TAG, "Error initializing models", e)
            throw RuntimeException("Error initializing models: ${e.message}")
        } catch (e: OrtException) {
            Log.e(TAG, "Error initializing models", e)
            throw RuntimeException("Error initializing models: ${e.message}")
        }
    }

    private fun loadApiKey(): String {
        return try {
            context.assets.open(API_KEY_FILE).bufferedReader().use { it.readText().trim() }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading API key", e)
            throw RuntimeException("Error loading API key: ${e.message}")
        }
    }

    fun processText(text: String, speakerId: Int, callback: TextToAudioCallback) {
        Log.d(TAG, "Processing text. Text length: ${text.length}, Speaker ID: $speakerId")

        apiCall.sendTextToApi(text, speakerId, object : ApiCall.ApiCallCallback {
            override fun onSuccess(narration: String, status: String) {
                Log.d(TAG, "API Response received: $narration")
                Log.d(TAG, "API Status: $status")

                try {
                    // Map the API speaker ID to the TTS speaker ID
                    val ttsSpeakerId = mapSpeakerId(speakerId)
                    val responseAudio = synthesizer.tts(narration, ttsSpeakerId)
                    callback.onSuccess(narration, status, responseAudio)
                } catch (e: Exception) {
                    Log.e(TAG, "Error synthesizing audio response", e)
                    callback.onError("Error synthesizing audio: ${e.message}")
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "API call error: $error")
                callback.onError(error)
            }
        })
    }

    private fun mapSpeakerId(apiSpeakerId: Int): Int {
        for (mapping in SPEAKER_ID_MATRIX) {
            if (mapping[0] == apiSpeakerId) {
                return mapping[1]
            }
        }
        // Default to the first TTS speaker ID if no mapping is found
        return SPEAKER_ID_MATRIX[0][1]
    }

    interface TextToAudioCallback {
        fun onSuccess(narration: String, status: String, audioData: FloatArray)
        fun onError(error: String)
    }
}