package com.vaios.holobar

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.graphics.Color

class ProcessAndResponseManager(
    private val context: Context,
    private val apiCall: ApiCall,
    private val processText: ProcessText
) {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val audioTextChannel = Channel<Triple<String, FloatArray?, TextView>>(Channel.UNLIMITED)
    private var audioTrack: AudioTrack? = null
    private var pieceCount = 0

    init {
        startAudioTextPlayer()
    }

    private fun startAudioTextPlayer() {
        scope.launch {
            try {
                for (triple in audioTextChannel) {
                    val (text, audio, textView) = triple
                    streamText(text, textView)
                    audio?.let { playResponseAudio(it) }
                    pieceCount++
                }
            } finally {
                if (pieceCount == 2) {
                    delay(500) // Add a small delay when we have exactly two pieces
                }
                releaseAudioTrack()
                pieceCount = 0
            }
        }
    }

    fun process(input: Any, selectedSpeakerId: Int, responseTextView: TextView, isMuted: Boolean, onComplete: () -> Unit) {
        scope.launch {
            try {
                val narration = getApiResponse(input, selectedSpeakerId)
                val textPieces = processText.splitText(narration)
                if (isMuted) {
                    processTextPiecesOnly(textPieces, responseTextView)
                } else {
                    processTextPieces(textPieces, selectedSpeakerId, responseTextView)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    responseTextView.text = context.getString(R.string.error_message, e.message)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    private suspend fun getApiResponse(input: Any, selectedSpeakerId: Int): String =
        suspendCancellableCoroutine { continuation ->
            val callback = object : ApiCall.ChatApiCallback {
                override fun onSuccess(narration: String, status: String) {
                    continuation.resume(narration)
                }

                override fun onError(error: String) {
                    continuation.resumeWithException(Exception(error))
                }
            }

            when (input) {
                is ByteArray -> apiCall.speakToAgents(input, selectedSpeakerId, callback)
                is String -> apiCall.talkToAgents(input, selectedSpeakerId, callback)
                else -> continuation.resumeWithException(IllegalArgumentException("Invalid input type"))
            }
        }

    private suspend fun processTextPieces(textPieces: List<String>, speakerId: Int, responseTextView: TextView) {
        for (piece in textPieces) {
            val audioData = synthesizeAudio(piece, speakerId)
            audioTextChannel.send(Triple(piece, audioData, responseTextView))
        }
    }

    private suspend fun processTextPiecesOnly(textPieces: List<String>, responseTextView: TextView) {
        for (piece in textPieces) {
            audioTextChannel.send(Triple(piece, null, responseTextView))
        }
    }

    private suspend fun synthesizeAudio(text: String, speakerId: Int): FloatArray =
        suspendCancellableCoroutine { continuation ->
            processText.synthesizeAudio(text, speakerId) { audioData ->
                continuation.resume(audioData)
            }
        }

    private suspend fun streamText(text: String, textView: TextView) {
        val words = text.split(" ")
        for (word in words) {
            withContext(Dispatchers.Main) {
                val spannable = SpannableString("$word ")
                spannable.setSpan(ForegroundColorSpan(Color.RED), 0, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                textView.append(spannable)
                scrollToBottom(textView)
            }
            delay(50) // Adjust delay as needed
        }
    }

    private suspend fun playResponseAudio(audioData: FloatArray) {
        withContext(Dispatchers.IO) {
            try {
                ensureAudioTrackInitialized()
                audioTrack?.let { track ->
                    track.play()
                    var bytesWritten = 0
                    while (bytesWritten < audioData.size) {
                        val result = track.write(audioData, bytesWritten, audioData.size - bytesWritten, AudioTrack.WRITE_BLOCKING)
                        if (result > 0) {
                            bytesWritten += result
                        } else {
                            println("Error writing to AudioTrack: $result")
                            break
                        }
                    }
                    track.stop()
                }
                delay(100) // Small delay after each audio piece
            } catch (e: Exception) {
                println("Error playing audio: ${e.message}")
                e.printStackTrace()
                releaseAudioTrack()
            }
        }
    }

    private fun ensureAudioTrackInitialized() {
        if (audioTrack == null || audioTrack?.state == AudioTrack.STATE_UNINITIALIZED) {
            releaseAudioTrack()
            val bufferSize = AudioTrack.getMinBufferSize(
                22050,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(22050)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
        }
    }

    private fun releaseAudioTrack() {
        audioTrack?.release()
        audioTrack = null
    }

    private fun scrollToBottom(textView: TextView) {
        val scrollView = findScrollView(textView)
        scrollView?.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun findScrollView(view: View): ScrollView? {
        var parent = view.parent
        while (parent != null) {
            if (parent is ScrollView) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    fun cancel() {
        job.cancel()
        audioTextChannel.close()
        releaseAudioTrack()
    }
}