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

        private val SPEAKER_ID_MATRIX = arrayOf(
            intArrayOf(0, 8),
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
            synthesizer = VitsOnnxSynthesizer(context, VITS_MODEL_FILE)
            Log.d(TAG, "VITS synthesizer initialized")

            Phonemic.initialize(context.assets, PHONEMIZER_MODEL_FILE)
            Log.d(TAG, "Phonemizer initialized")

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

    fun processText(text: String, speakerId: Int, callback: ProcessTextCallback) {
        Log.d(TAG, "Processing text. Text length: ${text.length}, Speaker ID: $speakerId")

        processTextOnly(text, speakerId, object : TextOnlyCallback {
            override fun onSuccess(narration: String, status: String) {
                val textPieces = splitText(narration)
                processTextPieces(textPieces, speakerId, callback)
            }

            override fun onError(error: String) {
                callback.onError(error)
            }
        })
    }

    private fun processTextOnly(text: String, speakerId: Int, callback: TextOnlyCallback) {
        Log.d(TAG, "Processing text only. Text length: ${text.length}, Speaker ID: $speakerId")

        apiCall.sendTextToApi(text, speakerId, object : ApiCall.ApiCallCallback {
            override fun onSuccess(narration: String, status: String) {
                Log.d(TAG, "API Response received: $narration")
                Log.d(TAG, "API Status: $status")
                callback.onSuccess(narration, status)
            }

            override fun onError(error: String) {
                Log.e(TAG, "API call error: $error")
                callback.onError(error)
            }
        })
    }

    private fun splitText(text: String): List<String> {
        val sentences = text.split(". ", "! ", "? ").filter { it.isNotEmpty() }
        if (sentences.size <= 2) return listOf(text)

        val midpoint = (sentences.size + 1) / 2
        val firstHalf = sentences.subList(0, midpoint).joinToString(". ") + "."
        val secondHalf = sentences.subList(midpoint, sentences.size).joinToString(". ") + "."
        return listOf(firstHalf, secondHalf)
    }

    private fun processTextPieces(textPieces: List<String>, speakerId: Int, callback: ProcessTextCallback) {
        coroutineScope.launch {
            textPieces.forEachIndexed { index, piece ->
                try {
                    val audioData = synthesizeAudio(piece, speakerId)
                    withContext(Dispatchers.Main) {
                        callback.onPieceReady(piece, audioData, index == textPieces.lastIndex)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        callback.onError("Error processing audio: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun synthesizeAudio(textPiece: String, speakerId: Int): FloatArray = withContext(Dispatchers.Default) {
        val ttsSpeakerId = mapSpeakerId(speakerId)
        synthesizer.tts(textPiece, ttsSpeakerId.toLong())
    }

    private fun mapSpeakerId(apiSpeakerId: Int): Int {
        for (mapping in SPEAKER_ID_MATRIX) {
            if (mapping[0] == apiSpeakerId) {
                return mapping[1]
            }
        }
        return SPEAKER_ID_MATRIX[0][1]
    }

    interface ProcessTextCallback {
        fun onPieceReady(text: String, audioData: FloatArray, isLastPiece: Boolean)
        fun onError(error: String)
    }

    interface TextOnlyCallback {
        fun onSuccess(narration: String, status: String)
        fun onError(error: String)
    }
}