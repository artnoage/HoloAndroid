package com.vaios.holobar;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
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
import java.io.File;
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

    private MediaPlayer mediaPlayer;
    private int currentSongIndex = 0;
    private final int[] songs = {R.raw.song1, R.raw.song2, R.raw.song3, R.raw.song4};
    private Button nextSongButton;
    private Button restartButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: Initializing MainActivity");

        speakerSpinner = findViewById(R.id.speaker_spinner);
        recordButton = findViewById(R.id.record_button);
        backgroundImage = findViewById(R.id.background_image);
        nextSongButton = findViewById(R.id.next_song_button);
        restartButton = findViewById(R.id.restart_button);

        setupSpeakerSpinner();
        setupButtons();

        try {
            audioToAudio = new TextToAudio(this);
        } catch (RuntimeException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initializeSpeechRecognizer();
        initializeMediaPlayer();
    }

    private void setupSpeakerSpinner() {
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
    }

    private void setupButtons() {
        recordButton.setOnClickListener(v -> {
            if (checkPermission()) {
                toggleListening();
            } else {
                requestPermission();
            }
        });

        nextSongButton.setOnClickListener(v -> playNextSong());
        restartButton.setOnClickListener(v -> deleteUpdatedHistory());
    }

    private void initializeMediaPlayer() {
        mediaPlayer = MediaPlayer.create(this, songs[currentSongIndex]);
        mediaPlayer.setLooping(true);

        float volume = 0.10f;
        mediaPlayer.setVolume(volume, volume);

        mediaPlayer.start();

        mediaPlayer.setOnCompletionListener(mp -> playNextSong());
    }

    private void playNextSong() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }

        currentSongIndex = (currentSongIndex + 1) % songs.length;
        mediaPlayer = MediaPlayer.create(this, songs[currentSongIndex]);
        mediaPlayer.setLooping(true);

        float volume = 0.10f;
        mediaPlayer.setVolume(volume, volume);

        mediaPlayer.start();
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
                Toast.makeText(MainActivity.this, getString(R.string.speech_recognition_error, error), Toast.LENGTH_SHORT).show();
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
                recordButton.setText(R.string.stop_listening);
            } else {
                recordButton.setText(R.string.start_listening);
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
        Toast.makeText(this, R.string.processing_text, Toast.LENGTH_SHORT).show();

        recordButton.setEnabled(false);

        audioToAudio.processText(text, selectedSpeakerId, new TextToAudio.TextToAudioCallback() {
            @Override
            public void onSuccess(String narration, String status, float[] audioData) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, R.string.response_received, Toast.LENGTH_SHORT).show();
                    playAudio(audioData);
                    recordButton.setEnabled(true);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, getString(R.string.error_message, error), Toast.LENGTH_LONG).show();
                    recordButton.setEnabled(true);
                });
            }
        });
    }

    private void playAudio(float[] audio) {
        int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        AudioTrack audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SAMPLE_RATE)
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

    private void deleteUpdatedHistory() {
        File historyFile = new File(getFilesDir(), "updated_history.pickle");
        if (historyFile.exists()) {
            boolean deleted = historyFile.delete();
            if (deleted) {
                Toast.makeText(this, R.string.history_deleted_success, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Updated history file deleted: " + historyFile.getAbsolutePath());
            } else {
                Toast.makeText(this, R.string.history_delete_failed, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Failed to delete updated history file: " + historyFile.getAbsolutePath());
            }
        } else {
            Toast.makeText(this, R.string.history_file_not_exist, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Updated history file does not exist: " + historyFile.getAbsolutePath());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Record audio permission granted");
                Toast.makeText(this, R.string.record_permission_granted, Toast.LENGTH_SHORT).show();
                toggleListening();
            } else {
                Log.w(TAG, "Record audio permission denied");
                Toast.makeText(this, R.string.record_permission_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer == null) {
            initializeMediaPlayer();
        } else if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}