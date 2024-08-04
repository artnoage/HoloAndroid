package com.vaios.holobar;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import ai.onnxruntime.OrtException;
import android.Manifest;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String VITS_MODEL_FILE = "vits_model.onnx";
    private static final String PHONEMIZER_MODEL_FILE = "phonemizer_model.onnx";
    private static final String GEMINI_API_KEY = "AIzaSyBzw4vQsyQFcpA9hr4ncge4BYZW0CcT_ng";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int SAMPLE_RATE = 22050;

    private VitsOnnxSynthesizer synthesizer;
    private Tokenizer tokenizer;
    private ApiCall apiCall;

    private EditText inputText;
    private Spinner speakerSpinner;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private final List<Short> recordedData = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: Initializing MainActivity");

        inputText = findViewById(R.id.input_text);
        speakerSpinner = findViewById(R.id.speaker_spinner);
        Button processButton = findViewById(R.id.process_button);
        Button recordButton = findViewById(R.id.record_button);

        // Set up speaker spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.speaker_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        speakerSpinner.setAdapter(adapter);

        initializeModels();

        processButton.setOnClickListener(v -> {
            String text = inputText.getText().toString().trim();
            int speakerId = speakerSpinner.getSelectedItemPosition();
            if (!text.isEmpty()) {
                Log.d(TAG, "Process button clicked. Text: " + text + ", Speaker ID: " + speakerId);
                processUserInput(text, speakerId);
            } else {
                Log.w(TAG, "Process button clicked with empty input");
                Toast.makeText(MainActivity.this, "Input was empty. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
        recordButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
                processRecordedAudio();
            } else {
                if (checkPermission()) {
                    startRecording();
                } else {
                    requestPermission();
                }
            }
        });
    }

    private void initializeModels() {
        Log.d(TAG, "Initializing models");
        try {
            synthesizer = new VitsOnnxSynthesizer(this, VITS_MODEL_FILE);
            Log.d(TAG, "VITS synthesizer initialized");

            Phonemizer.initialize(getAssets(), PHONEMIZER_MODEL_FILE);
            Log.d(TAG, "Phonemizer initialized");

            tokenizer = new Tokenizer(this);
            Log.d(TAG, "Tokenizer initialized");

            apiCall = new ApiCall(this, GEMINI_API_KEY);
            Log.d(TAG, "ApiCall initialized");

        } catch (IOException | OrtException e) {
            Log.e(TAG, "Error initializing models", e);
            Toast.makeText(this, "Error initializing models: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    private void startRecording() {
        if (!checkPermission()) {
            Log.w(TAG, "Attempted to start recording without permission");
            Toast.makeText(this, "Record audio permission is required", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Starting audio recording");
        recordedData.clear();
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            audioRecord.startRecording();
            isRecording = true;

            new Thread(() -> {
                short[] buffer = new short[bufferSize];
                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, bufferSize);
                    for (int i = 0; i < read; i++) {
                        recordedData.add(buffer[i]);
                    }
                }
            }).start();

            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when starting recording", e);
            Toast.makeText(this, "Error: Permission denied for audio recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        Log.d(TAG, "Stopping audio recording");
        isRecording = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping audio recording", e);
            } finally {
                audioRecord = null;
            }
        }
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
    }

    private void processRecordedAudio() {
        Log.d(TAG, "Processing recorded audio");
        float[] audioFloat = new float[recordedData.size()];
        for (int i = 0; i < recordedData.size(); i++) {
            audioFloat[i] = (float) recordedData.get(i) / 32768.0f;
        }

        // Play the recorded audio
        AudioProcessor.playAudio(audioFloat, SAMPLE_RATE);

        int speakerId = speakerSpinner.getSelectedItemPosition();

        // Send audio to API
        sendAudioToApi(audioFloat, speakerId);
    }

    private void processUserInput(String input, int speakerId) {
        Log.d(TAG, "Processing user input: '" + input + "' for speaker ID: " + speakerId);
        Toast.makeText(this, "Processing input...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                // Tokenize input if needed
                List<Integer> inputTokens = tokenizer.textToIds(input);
                Log.d(TAG, "Input tokens: " + inputTokens);

                // Text to audio
                float[] inputAudio = synthesizer.tts(input, speakerId);
                Log.d(TAG, "Generated audio length: " + inputAudio.length + " samples");

                // Play the audio before sending
                runOnUiThread(() -> {
                    Log.d(TAG, "Playing generated audio");
                    Toast.makeText(this, "Playing generated audio...", Toast.LENGTH_SHORT).show();
                    AudioProcessor.playAudio(inputAudio, SAMPLE_RATE);
                });

                // Wait for audio to finish playing (you might want to adjust this)
                Thread.sleep((long) inputAudio.length * 1000L / SAMPLE_RATE + 500L);

                // Send audio to API
                sendAudioToApi(inputAudio, speakerId);

            } catch (OrtException | InterruptedException e) {
                Log.e(TAG, "Error processing input", e);
                runOnUiThread(() -> Toast.makeText(this, "Error processing input: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void sendAudioToApi(float[] audio, int speakerId) {
        Log.d(TAG, "Sending audio to API. Length: " + audio.length + ", Speaker ID: " + speakerId);
        Toast.makeText(this, "Processing audio...", Toast.LENGTH_SHORT).show();

        apiCall.sendAudioToApi(audio, speakerId, SAMPLE_RATE, new ApiCall.ApiCallCallback() {
            @Override
            public void onSuccess(@NonNull String narration, @NonNull String status) {
                Log.d(TAG, "API Response received: " + narration);
                Log.d(TAG, "API Status: " + status);

                // Process and play the response
                String[] textChunks = narration.split("(?<=[.!?])\\s+");
                for (String chunk : textChunks) {
                    try {
                        float[] chunkAudio = synthesizer.tts(chunk, speakerId);
                        runOnUiThread(() -> AudioProcessor.playAudio(chunkAudio, SAMPLE_RATE));
                        Thread.sleep((long) chunkAudio.length * 1000L / SAMPLE_RATE);
                    } catch (Exception e) {
                        Log.e(TAG, "Error synthesizing or playing audio chunk", e);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error synthesizing audio: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }
            }

            @Override
            public void onError(@NonNull String error) {
                Log.e(TAG, "API call error: " + error);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error communicating with the API: " + error, Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Record audio permission granted");
                Toast.makeText(this, "Record audio permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "Record audio permission denied");
                Toast.makeText(this, "Record audio permission is required for recording functionality", Toast.LENGTH_LONG).show();
            }
        }
    }
}