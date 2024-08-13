package com.vaios.holobar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : Activity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val SAMPLE_RATE = 22050
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val DEBUG_MODE = false // Set to false in production
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false
    private var recordedAudioData: ByteArray? = null

    private lateinit var processText: ProcessText
    private lateinit var apiCall: ApiCall
    private lateinit var speakerSpinner: Spinner
    private lateinit var startListeningButton: Button
    private lateinit var backgroundImage: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: ScrollView
    private lateinit var responseContainer: LinearLayout
    private lateinit var nextSongButton: Button
    private lateinit var restartButton: Button
    private lateinit var textInput: EditText
    private lateinit var sendTextButton: Button
    private lateinit var muteButton: Button

    private lateinit var audioPlaybackManager: AudioPlaybackManager
    private lateinit var processAndResponseManager: ProcessAndResponseManager

    private var isMuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate: Initializing MainActivity")

        initializeViews()
        setupSpeakerSpinner()
        setupButtons()

        val apiKey = loadApiKey()
        apiCall = ApiCall(this, apiKey)
        processText = ProcessText(this, apiKey)

        audioPlaybackManager = AudioPlaybackManager(this)
        processAndResponseManager = ProcessAndResponseManager(
            this,
            apiCall,
            processText
        )
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
        muteButton = findViewById(R.id.mute_button)
    }

    private fun loadApiKey(): String {
        return try {
            File(filesDir, "gemini_api_key.txt").readText().trim()
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
                clearResponseContainer()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun setupButtons() {
        startListeningButton.setOnClickListener {
            if (checkPermission()) {
                if (!isRecording) {
                    startRecording()
                } else {
                    stopRecording()
                }
            } else {
                requestPermission()
            }
        }

        nextSongButton.setOnClickListener { audioPlaybackManager.nextSong() }

        restartButton.setOnClickListener {
            deleteUpdatedHistory()
            clearResponseContainer()
        }

        sendTextButton.setOnClickListener {
            val text = textInput.text.toString()
            if (text.isNotEmpty()) {
                processInput(text)
                textInput.text.clear()
            } else {
                Toast.makeText(this, getString(R.string.empty_input_message), Toast.LENGTH_SHORT).show()
            }
        }

        muteButton.setOnClickListener {
            isMuted = !isMuted
            updateMuteButtonUI()
        }
    }

    private fun updateMuteButtonUI() {
        if (isMuted) {
            muteButton.text = getString(R.string.unmute)
            muteButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        } else {
            muteButton.text = getString(R.string.mute)
            muteButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }

    private fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size")
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
            return
        }

        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized")
            return
        }

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            val audioBuffer = ByteArray(bufferSize)
            val outputStream = ByteArrayOutputStream()
            while (isRecording) {
                val readSize = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                if (readSize > 0) {
                    outputStream.write(audioBuffer, 0, readSize)
                }
            }
            recordedAudioData = outputStream.toByteArray()
            Log.d(TAG, "Recording stopped. Bytes read: ${recordedAudioData?.size}")

            if (DEBUG_MODE) {
                runOnUiThread {
                    playDebugAudio()
                }
            } else {
                runOnUiThread {
                    processInput(recordedAudioData!!)
                }
            }
        }

        recordingThread?.start()
        Log.d(TAG, "Recording started")
        startListeningButton.text = getString(R.string.stop_talking)
        startListeningButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
    }

    private fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingThread?.join()
        Log.d(TAG, "Recording stopped")
        startListeningButton.text = getString(R.string.start_talking)
        startListeningButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
    }

    private fun playDebugAudio() {
        recordedAudioData?.let { audioData ->
            Log.d(TAG, "Playing debug audio. Size: ${audioData.size} bytes")

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(audioData.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(audioData, 0, audioData.size)
            audioTrack.play()

            // Wait for playback to finish
            Thread {
                Thread.sleep(audioData.size * 1000L / (SAMPLE_RATE * 2))
                audioTrack.release()
                runOnUiThread {
                    processInput(audioData)
                }
            }.start()

            // Save debug audio file
            saveAudioToFile(audioData)
        }
    }

    private fun saveAudioToFile(audioData: ByteArray) {
        val fileName = "debug_audio_${System.currentTimeMillis()}.pcm"
        val file = File(externalCacheDir, fileName)
        try {
            FileOutputStream(file).use { it.write(audioData) }
            Log.d(TAG, "Audio saved to file: ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Error saving audio file", e)
        }
    }

    private fun processInput(input: Any) {
        val selectedSpeakerId = speakerSpinner.selectedItemPosition

        if (input is ByteArray) {
            Log.d(TAG, "Processing audio. Size: ${input.size} bytes")
        } else {
            Log.d(TAG, "Processing text: $input")
        }

        disableInputs()
        progressBar.visibility = View.VISIBLE

        val responseTextView = createNewResponseTextView()
        processAndResponseManager.process(input, selectedSpeakerId, responseTextView, isMuted) {
            runOnUiThread {
                enableInputs()
                progressBar.visibility = View.GONE
            }
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
                clearResponseContainer()
            } else {
                Toast.makeText(this, R.string.history_delete_failed, Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Failed to delete updated history file: ${historyFile.absolutePath}")
            }
        } else {
            Toast.makeText(this, R.string.history_file_not_exist, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Updated history file does not exist: ${historyFile.absolutePath}")
            clearResponseContainer()
        }
    }

    private fun clearResponseContainer() {
        responseContainer.removeAllViews()
    }

    private fun disableInputs() {
        startListeningButton.isEnabled = false
        speakerSpinner.isEnabled = false
        textInput.isEnabled = false
        sendTextButton.isEnabled = false
        muteButton.isEnabled = false
    }

    private fun enableInputs() {
        startListeningButton.isEnabled = true
        speakerSpinner.isEnabled = true
        textInput.isEnabled = true
        sendTextButton.isEnabled = true
        muteButton.isEnabled = true
    }

    private fun checkPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Record audio permission granted")
                Toast.makeText(this, R.string.record_permission_granted, Toast.LENGTH_SHORT).show()
                startRecording()
            } else {
                Log.w(TAG, "Record audio permission denied")
                Toast.makeText(this, R.string.record_permission_required, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) {
            stopRecording()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            stopRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlaybackManager.release()
        processAndResponseManager.cancel()
        if (isRecording) {
            stopRecording()
        }
    }
}