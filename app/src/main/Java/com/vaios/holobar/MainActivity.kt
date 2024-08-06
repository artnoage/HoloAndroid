package com.vaios.holobar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.*

class MainActivity : Activity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val SAMPLE_RATE = 22050
    }

    private lateinit var audioToAudio: TextToAudio
    private lateinit var speakerSpinner: Spinner
    private lateinit var recordButton: Button
    private lateinit var backgroundImage: ImageView
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false

    private var mediaPlayer: MediaPlayer? = null
    private var currentSongIndex = 0
    private val songs = intArrayOf(R.raw.song1, R.raw.song2, R.raw.song3, R.raw.song4)
    private lateinit var nextSongButton: Button
    private lateinit var restartButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate: Initializing MainActivity")

        speakerSpinner = findViewById(R.id.speaker_spinner)
        recordButton = findViewById(R.id.record_button)
        backgroundImage = findViewById(R.id.background_image)
        nextSongButton = findViewById(R.id.next_song_button)
        restartButton = findViewById(R.id.restart_button)

        setupSpeakerSpinner()
        setupButtons()

        try {
            audioToAudio = TextToAudio(this)
        } catch (e: RuntimeException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeSpeechRecognizer()
        initializeMediaPlayer()
    }

    private fun setupSpeakerSpinner() {
        ArrayAdapter.createFromResource(
            this,
            R.array.speaker_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            speakerSpinner.adapter = adapter
        }

        speakerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateBackgroundImage(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun setupButtons() {
        recordButton.setOnClickListener {
            if (checkPermission()) {
                toggleListening()
            } else {
                requestPermission()
            }
        }

        nextSongButton.setOnClickListener { playNextSong() }
        restartButton.setOnClickListener { deleteUpdatedHistory() }
    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer.create(this, songs[currentSongIndex]).apply {
            isLooping = true
            setVolume(0.1f, 0.1f)
            start()
            setOnCompletionListener { playNextSong() }
        }
    }

    private fun playNextSong() {
        mediaPlayer?.apply {
            stop()
            release()
        }

        currentSongIndex = (currentSongIndex + 1) % songs.size
        mediaPlayer = MediaPlayer.create(this, songs[currentSongIndex]).apply {
            isLooping = true
            setVolume(0.1f, 0.1f)
            start()
        }
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.get(0)?.let { recognizedText ->
                        processRecognizedText(recognizedText)
                    }
                }

                override fun onReadyForSpeech(params: Bundle) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    updateRecordButtonUI()
                }

                override fun onError(error: Int) {
                    isListening = false
                    updateRecordButtonUI()
                    Toast.makeText(this@MainActivity, getString(R.string.speech_recognition_error, error), Toast.LENGTH_SHORT).show()
                }

                override fun onPartialResults(partialResults: Bundle) {}
                override fun onEvent(eventType: Int, params: Bundle) {}
            })
        }
    }

    private fun toggleListening() {
        if (!isListening) startListening() else stopListening()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer.startListening(intent)
        isListening = true
        updateRecordButtonUI()
    }

    private fun stopListening() {
        speechRecognizer.stopListening()
        isListening = false
        updateRecordButtonUI()
    }

    private fun updateRecordButtonUI() {
        runOnUiThread {
            recordButton.text = getString(if (isListening) R.string.stop_listening else R.string.start_listening)
        }
    }

    private fun checkPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
    }

    private fun processRecognizedText(text: String) {
        val selectedSpeakerId = speakerSpinner.selectedItemPosition
        Toast.makeText(this, R.string.processing_text, Toast.LENGTH_SHORT).show()

        recordButton.isEnabled = false

        audioToAudio.processText(text, selectedSpeakerId, object : TextToAudio.TextToAudioCallback {
            override fun onSuccess(narration: String, status: String, audioData: FloatArray) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, R.string.response_received, Toast.LENGTH_SHORT).show()
                    playAudio(audioData)
                    recordButton.isEnabled = true
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, getString(R.string.error_message, error), Toast.LENGTH_LONG).show()
                    recordButton.isEnabled = true
                }
            }
        })
    }

    private fun playAudio(audio: FloatArray) {
        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .build().apply {
                play()
                write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING)
                stop()
                release()
            }
    }

    private fun updateBackgroundImage(position: Int) {
        val backgroundResourceId = when (position) {
            0 -> R.drawable.background_speaker1
            1 -> R.drawable.background_speaker2
            2 -> R.drawable.background_speaker3
            3 -> R.drawable.background_speaker4
            4 -> R.drawable.background_speaker5
            else -> R.drawable.default_background
        }
        backgroundImage.setImageResource(backgroundResourceId)
    }

    private fun deleteUpdatedHistory() {
        val historyFile = File(filesDir, "updated_history.pickle")
        if (historyFile.exists()) {
            if (historyFile.delete()) {
                Toast.makeText(this, R.string.history_deleted_success, Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Updated history file deleted: ${historyFile.absolutePath}")
            } else {
                Toast.makeText(this, R.string.history_delete_failed, Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Failed to delete updated history file: ${historyFile.absolutePath}")
            }
        } else {
            Toast.makeText(this, R.string.history_file_not_exist, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Updated history file does not exist: ${historyFile.absolutePath}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Record audio permission granted")
                Toast.makeText(this, R.string.record_permission_granted, Toast.LENGTH_SHORT).show()
                toggleListening()
            } else {
                Log.w(TAG, "Record audio permission denied")
                Toast.makeText(this, R.string.record_permission_required, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (mediaPlayer == null) {
            initializeMediaPlayer()
        } else if (!mediaPlayer!!.isPlaying) {
            mediaPlayer?.start()
        }
    }

    override fun onStop() {
        super.onStop()
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }
}