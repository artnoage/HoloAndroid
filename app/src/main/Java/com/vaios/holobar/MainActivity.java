package com.vaios.holobar;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int SAMPLE_RATE = 22050;

    private TextToAudio audioToAudio;
    private Spinner speakerSpinner;
    private Button recordButton;
    private ImageView backgroundImage;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: Initializing MainActivity");

        speakerSpinner = findViewById(R.id.speaker_spinner);
        recordButton = findViewById(R.id.record_button);
        backgroundImage = findViewById(R.id.background_image);

        // Set up speaker spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.speaker_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        speakerSpinner.setAdapter(adapter);

        speakerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateBackgroundImage(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        try {
            audioToAudio = new TextToAudio(this);
        } catch (RuntimeException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        recordButton.setOnClickListener(v -> {
            if (checkPermission()) {
                toggleListening();
            } else {
                requestPermission();
            }
        });

        initializeSpeechRecognizer();
    }

    private void initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    processRecognizedText(recognizedText);
                }
            }

            @Override
            public void onReadyForSpeech(Bundle params) {}

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                isListening = false;
                updateRecordButtonUI();
            }

            @Override
            public void onError(int error) {
                isListening = false;
                updateRecordButtonUI();
                Toast.makeText(MainActivity.this, "Error in speech recognition: " + error, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void toggleListening() {
        if (!isListening) {
            startListening();
        } else {
            stopListening();
        }
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizer.startListening(intent);
        isListening = true;
        updateRecordButtonUI();
    }

    private void stopListening() {
        speechRecognizer.stopListening();
        isListening = false;
        updateRecordButtonUI();
    }

    private void updateRecordButtonUI() {
        runOnUiThread(() -> {
            if (isListening) {
                recordButton.setText("Stop Listening");
                // You can also change the button's appearance here
            } else {
                recordButton.setText("Start Listening");
                // Reset the button's appearance
            }
        });
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    private void processRecognizedText(String text) {
        int selectedSpeakerId = speakerSpinner.getSelectedItemPosition();
        Toast.makeText(this, "Processing text...", Toast.LENGTH_SHORT).show();

        // Disable the record button while processing
        recordButton.setEnabled(false);

        audioToAudio.processText(text, selectedSpeakerId, new TextToAudio.TextToAudioCallback() {
            @Override
            public void onSuccess(String narration, String status, float[] audioData) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Response received, playing audio...", Toast.LENGTH_SHORT).show();
                    playAudio(audioData, SAMPLE_RATE);
                    // Re-enable the record button after processing
                    recordButton.setEnabled(true);
                });
            }

            @Override
            public void onError(String error) {
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

    private void updateBackgroundImage(int position) {
        int backgroundResourceId;
        switch (position) {
            case 0:
                backgroundResourceId = R.drawable.background_speaker1;
                break;
            case 1:
                backgroundResourceId = R.drawable.background_speaker2;
                break;
            case 2:
                backgroundResourceId = R.drawable.background_speaker3;
                break;
            case 3:
                backgroundResourceId = R.drawable.background_speaker4;
                break;
            case 4:
                backgroundResourceId = R.drawable.background_speaker5;
                break;
            default:
                backgroundResourceId = R.drawable.default_background;
                break;
        }
        backgroundImage.setImageResource(backgroundResourceId);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Record audio permission granted");
                Toast.makeText(this, "Record audio permission granted", Toast.LENGTH_SHORT).show();
                toggleListening();
            } else {
                Log.w(TAG, "Record audio permission denied");
                Toast.makeText(this, "Record audio permission is required for speech recognition", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}