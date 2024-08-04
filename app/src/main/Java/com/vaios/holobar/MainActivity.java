package com.vaios.holobar;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int SAMPLE_RATE = 22050;

    private AudioToAudio audioToAudio;
    private Spinner speakerSpinner;
    private Button recordButton;
    private boolean isRecording = false;
    private AudioRecord audioRecord;
    private final List<Short> recordedData = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: Initializing MainActivity");

        speakerSpinner = findViewById(R.id.speaker_spinner);
        recordButton = findViewById(R.id.record_button);

        // Set up speaker spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.speaker_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        speakerSpinner.setAdapter(adapter);

        try {
            audioToAudio = new AudioToAudio(this);
        } catch (RuntimeException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

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

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        audioRecord.startRecording();
        isRecording = true;
        recordButton.setText("Stop Recording");

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
        recordButton.setText("Start Recording");
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
    }

    private void processRecordedAudio() {
        Log.d(TAG, "Processing recorded audio");
        float[] audioFloat = new float[recordedData.size()];
        for (int i = 0; i < recordedData.size(); i++) {
            audioFloat[i] = (float) recordedData.get(i) / 32768.0f;
        }

        int selectedSpeakerId = speakerSpinner.getSelectedItemPosition();
        Toast.makeText(this, "Processing audio...", Toast.LENGTH_SHORT).show();

        // Disable the record button while processing
        recordButton.setEnabled(false);

        // Hardcoded map of speakers
        Map<Integer, Integer> speakerMap = new HashMap<Integer, Integer>() {{
            put(0, 104); // Speaker 1 mapped to 104
            put(1, 105); // Speaker 2 mapped to 105
            put(2, 106); // Speaker 3 mapped to 106
            put(3, 107); // Speaker 4 mapped to 107
            put(4, 108); // Speaker 5 mapped to 108
        }};

        audioToAudio.processAudio(audioFloat, selectedSpeakerId, new ApiCall.ApiCallCallback() {
            @Override
            public void onSuccess(@NonNull String narration, @NonNull String status) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Response received, playing audio...", Toast.LENGTH_SHORT).show();
                    try {
                        // Map the selected speaker ID to the actual speaker ID for the synthesizer
                        int mappedSpeakerId = speakerMap.getOrDefault(selectedSpeakerId, 104); // Default to 104 if not found

                        // Get the synthesized audio from the AudioToAudio class
                        float[] responseAudio = audioToAudio.getSynthesizer().tts(narration, mappedSpeakerId);
                        // Play the synthesized audio
                        playAudio(responseAudio, SAMPLE_RATE);
                    } catch (Exception e) {
                        Log.e(TAG, "Error synthesizing or playing audio", e);
                        Toast.makeText(MainActivity.this, "Error playing audio: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    // Re-enable the record button after processing
                    recordButton.setEnabled(true);
                });
            }

            @Override
            public void onError(@NonNull String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                    // Re-enable the record button on error
                    recordButton.setEnabled(true);
                });
            }
        });
    }

    private void playAudio(float[] audio, int sampleRate) {
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        AudioTrack audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();

        audioTrack.play();
        audioTrack.write(audio, 0, audio.length, AudioTrack.WRITE_BLOCKING);
        audioTrack.stop();
        audioTrack.release();
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