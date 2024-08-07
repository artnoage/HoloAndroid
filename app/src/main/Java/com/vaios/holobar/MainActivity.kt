package com.vaios.holobar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : Activity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val SAMPLE_RATE = 22050
    }

    private lateinit var processText: ProcessText
    private lateinit var speakerSpinner: Spinner
    private lateinit var startListeningButton: Button
    private lateinit var backgroundImage: ImageView
    private lateinit var speechRecognitionManager: SpeechRecognitionManager
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: ScrollView
    private lateinit var responseContainer: LinearLayout

    private var mediaPlayer: MediaPlayer? = null
    private var currentSongIndex = 0
    private val songs = intArrayOf(R.raw.song1, R.raw.song2, R.raw.song3, R.raw.song4)
    private lateinit var nextSongButton: Button
    private lateinit var restartButton: Button

    private lateinit var textInput: EditText
    private lateinit var sendTextButton: Button

    private var isListening = false

    private var currentAudioData: FloatArray? = null
    private var nextAudioData: FloatArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate: Initializing MainActivity")

        initializeViews()
        setupSpeakerSpinner()
        setupButtons()

        val apiKey = loadApiKey()
        processText = ProcessText(this, apiKey)

        speechRecognitionManager = SpeechRecognitionManager(this)
        initializeMediaPlayer()
    }

    private fun initializeViews() {
        speakerSpinner = findViewById(R.id.speaker_spinner)
        startListeningButton = findViewById(R.id.start_listening_button)
        backgroundImage = findViewById(R.id.background_image)
        nextSongButton = findViewById(R.id.next_song_button)
        restartButton = findViewById(R.id.restart_button)
        progressBar = findViewById(R.id.progress_bar)
        textInput = findViewById(R.id.text_input)
        sendTextButton = findViewById(R.id.send_text_button)
        scrollView = findViewById(R.id.scroll_view)
        responseContainer = findViewById(R.id.response_container)
    }

    private fun loadApiKey(): String {
        return try {
            assets.open("gemini_api_key.txt").bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading API key: ${e.message}")
            ""
        }
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
        startListeningButton.setOnClickListener {
            if (checkPermission()) {
                if (!isListening) {
                    startListening()
                } else {
                    stopListening()
                }
            } else {
                requestPermission()
            }
        }

        nextSongButton.setOnClickListener { playNextSong() }
        restartButton.setOnClickListener { deleteUpdatedHistory() }

        sendTextButton.setOnClickListener {
            val text = textInput.text.toString()
            if (text.isNotEmpty()) {
                processRecognizedText(text)
                textInput.text.clear()
            } else {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun startListening() {
        isListening = true
        startListeningButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        startListeningButton.text = getString(R.string.stop_listening)

        speechRecognitionManager.startListening { recognizedText ->
            isListening = false
            startListeningButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            startListeningButton.text = getString(R.string.start_listening)

            if (recognizedText != null) {
                processRecognizedText(recognizedText)
            } else {
                Toast.makeText(this, "Speech recognition failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopListening() {
        isListening = false
        speechRecognitionManager.stopListening()
        startListeningButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        startListeningButton.text = getString(R.string.start_listening)
    }

    private fun checkPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
    }

    private fun processRecognizedText(text: String) {
        val selectedSpeakerId = speakerSpinner.selectedItemPosition
        Log.d(TAG, "Processing text: $text")

        disableInputs()
        progressBar.visibility = View.VISIBLE
        mediaPlayer?.pause()

        processText.processText(text, selectedSpeakerId, object : ProcessText.ProcessTextCallback {
            override fun onPieceReady(text: String, audioData: FloatArray, isLastPiece: Boolean) {
                runOnUiThread {
                    val responseTextView = createNewResponseTextView()
                    streamText(text, responseTextView)

                    if (currentAudioData == null) {
                        currentAudioData = audioData
                        playCurrentAudio()
                    } else {
                        nextAudioData = audioData
                    }

                    if (isLastPiece) {
                        progressBar.visibility = View.GONE
                    }
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error: $error", Toast.LENGTH_SHORT).show()
                    enableInputs()
                    progressBar.visibility = View.GONE
                    mediaPlayer?.start()
                }
            }
        })
    }

    private fun playCurrentAudio() {
        currentAudioData?.let { audioData ->
            Thread {
                playAudio(audioData)
                runOnUiThread {
                    if (nextAudioData != null) {
                        currentAudioData = nextAudioData
                        nextAudioData = null
                        playCurrentAudio()
                    } else {
                        currentAudioData = null
                        enableInputs()
                        mediaPlayer?.start()
                    }
                }
            }.start()
        }
    }

    private fun playAudio(audio: FloatArray) {
        Log.d(TAG, "Playing audio. Length: ${audio.size}")
        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .build().apply {
                    setVolume(0.6f)
                    play()
                    write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING)
                    stop()
                    release()
                }
            Log.d(TAG, "Audio playback completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio: ${e.message}")
        }
    }

    private fun createNewResponseTextView(): TextView {
        val newTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setTextIsSelectable(true)
        }
        responseContainer.addView(newTextView)
        return newTextView
    }

    private fun streamText(text: String, textView: TextView) {
        textView.text = ""

        val words = text.split(" ")
        var currentIndex = 0

        val handler = Handler(Looper.getMainLooper())
        val textStreamer = object : Runnable {
            override fun run() {
                if (currentIndex < words.size) {
                    textView.append(words[currentIndex] + " ")
                    currentIndex++
                    handler.postDelayed(this, 100) // Adjust delay as needed
                    scrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        }

        handler.post(textStreamer)
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

    private fun disableInputs() {
        startListeningButton.isEnabled = false
        speakerSpinner.isEnabled = false
        textInput.isEnabled = false
        sendTextButton.isEnabled = false
    }

    private fun enableInputs() {
        startListeningButton.isEnabled = true
        speakerSpinner.isEnabled = true
        textInput.isEnabled = true
        sendTextButton.isEnabled = true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Record audio permission granted")
                Toast.makeText(this, R.string.record_permission_granted, Toast.LENGTH_SHORT).show()
                startListening()
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
        speechRecognitionManager.destroy()
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }
}