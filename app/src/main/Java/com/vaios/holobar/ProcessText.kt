package com.vaios.holobar

import android.content.Context
import android.util.Log
import java.io.IOException
import ai.onnxruntime.OrtException
import kotlinx.coroutines.*

class ProcessText(private val context: Context, private val apiKey: String) {
    companion object {
        private const val TAG = "ProcessText"
        private const val VITS_MODEL_FILE = "vits_model.onnx"
        private const val PHONEMIZER_MODEL_FILE = "phonemizer_model.onnx"

        // Mapping between API speaker IDs and VITS model speaker IDs
        private val SPEAKER_ID_MATRIX = arrayOf(
            intArrayOf(0, 10),
            intArrayOf(1, 73),
            intArrayOf(2, 6),
            intArrayOf(3, 91),
            intArrayOf(4, 97)
        )
    }

    private lateinit var synthesizer: VitsOnnxSynthesizer
    private lateinit var apiCall: ApiCall
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    init {
        initializeModels()
    }

    private fun initializeModels() {
        Log.d(TAG, "Initializing models")
        try {
            // Initialize VITS synthesizer
            synthesizer = VitsOnnxSynthesizer(context, VITS_MODEL_FILE)
            Log.d(TAG, "VITS synthesizer initialized")

            // Initialize Phonemizer
            Phonemic.initialize(context.assets, PHONEMIZER_MODEL_FILE)
            Log.d(TAG, "Phonemizer initialized")

            // Initialize ApiCall
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


    fun splitText(text: String): List<String> {
        // If the text is short enough, return it as a single piece
        if (text.length <= 160) return listOf(text)

        val midpoint = text.length / 2
        // Find the next sentence-ending punctuation after the midpoint
        val splitIndex = text.substring(midpoint).indexOfFirst { it in ".!?" }

        return if (splitIndex == -1) {
            // If no suitable split point is found, return the whole text
            listOf(text)
        } else {
            // Split the text at the found punctuation
            val actualSplitIndex = midpoint + splitIndex + 1
            listOf(text.substring(0, actualSplitIndex), text.substring(actualSplitIndex).trim())
        }
    }

    fun synthesizeAudio(textPiece: String, speakerId: Int, callback: (FloatArray) -> Unit) {
        coroutineScope.launch {
            try {
                val audioData = withContext(Dispatchers.Default) {
                    val ttsSpeakerId = mapSpeakerId(speakerId)
                    synthesizer.tts(textPiece, ttsSpeakerId.toLong())
                }
                withContext(Dispatchers.Main) {
                    callback(audioData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error synthesizing audio", e)
                withContext(Dispatchers.Main) {
                    callback(FloatArray(0))  // Empty array to indicate error
                }
            }
        }
    }

    private fun mapSpeakerId(apiSpeakerId: Int): Int {
        // Map the API speaker ID to the corresponding VITS model speaker ID
        for (mapping in SPEAKER_ID_MATRIX) {
            if (mapping[0] == apiSpeakerId) {
                return mapping[1]
            }
        }
        // If no mapping is found, return the first VITS model speaker ID
        return SPEAKER_ID_MATRIX[0][1]
    }

}