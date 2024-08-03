package com.vaios.holobar;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import ai.onnxruntime.OrtException;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String VITS_MODEL_FILE = "vits_model.onnx";
    private static final String PHONEMIZER_MODEL_FILE = "phonemizer_model.onnx";
    private static final String GEMINI_API_KEY = "AIzaSyBzw4vQsyQFcpA9hr4ncge4BYZW0CcT_ng";

    private VitsOnnxSynthesizer synthesizer;
    private Tokenizer tokenizer;
    private ApiCall apiCall;

    private EditText inputText;
    private Spinner speakerSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: Initializing MainActivity");

        inputText = findViewById(R.id.input_text);
        speakerSpinner = findViewById(R.id.speaker_spinner);
        Button processButton = findViewById(R.id.process_button);

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
    }

    private void initializeModels() {
        Log.d(TAG, "Initializing models");
        try {
            synthesizer = new VitsOnnxSynthesizer(this, VITS_MODEL_FILE);
            Log.d(TAG, "VITS synthesizer initialized");

            // Removed unused config and tokenToIdx

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

    private void processUserInput(String input, int speakerId) {
        Log.d(TAG, "Processing user input: '" + input + "' for speaker ID: " + speakerId);
        Toast.makeText(this, "Processing input...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                // Tokenize and phonemize input if needed
                List<Integer> inputTokens = tokenizer.textToIds(input);
                Log.d(TAG, "Input tokens: " + inputTokens);
    
                // Text to audio
                float[] inputAudio = synthesizer.tts(input, speakerId);
                Log.d(TAG, "Generated audio length: " + inputAudio.length + " samples");
    
                // Save input audio
                File inputAudioFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "input_audio.wav");
                AudioProcessor.saveWav(inputAudio, inputAudioFile.getAbsolutePath(), synthesizer.getSampleRate());
                Log.d(TAG, "Saved input audio to: " + inputAudioFile.getAbsolutePath());
    
                // Play the audio before sending
                runOnUiThread(() -> {
                    Log.d(TAG, "Playing generated audio");
                    Toast.makeText(this, "Playing generated audio...", Toast.LENGTH_SHORT).show();
                    AudioProcessor.playAudio(inputAudio, synthesizer.getSampleRate());
                });
    
                // Wait for audio to finish playing (you might want to adjust this)
                Thread.sleep((long) inputAudio.length * 1000L / synthesizer.getSampleRate() + 500L);
    
                // Send audio to FastAPI and get text response
                Log.d(TAG, "Sending audio to API");
                apiCall.sendAudioToApi(inputAudioFile, speakerId, new ApiCall.ApiCallCallback() {
                    @Override
                    public void onSuccess(String apiResponseText, String status) {
                        Log.d(TAG, "API Response received: " + apiResponseText);
                        Log.d(TAG, "API Status: " + status);
                        // Process the response
                        List<String> textChunks = Arrays.asList(apiResponseText.split("(?<=[.!?])\\s+"));
                        Log.d(TAG, "Split text into " + textChunks.size() + " chunks");
    
                        // Tokenize and phonemize response if needed
                        List<Integer> responseTokens = tokenizer.textToIds(apiResponseText);
                        Log.d(TAG, "Response tokens: " + responseTokens);
    
                        // Synthesize each chunk
                        List<float[]> audioChunks = new ArrayList<>();
                        for (String chunk : textChunks) {
                            try {
                                float[] chunkAudio = synthesizer.tts(chunk, speakerId);
                                audioChunks.add(chunkAudio);
                                Log.d(TAG, "Synthesized chunk: " + chunk.substring(0, Math.min(chunk.length(), 50)) + "...");
                            } catch (Exception e) {
                                Log.e(TAG, "Error synthesizing audio chunk", e);
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error synthesizing audio: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                return;
                            }
                        }
    
                        // Concatenate all audio chunks
                        float[] responseAudio = concatenateAudio(audioChunks);
                        Log.d(TAG, "Concatenated response audio length: " + responseAudio.length + " samples");
    
                        // Save the final output audio
                        File outputFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "output_audio.wav");
                        try {
                            AudioProcessor.saveWav(responseAudio, outputFile.getAbsolutePath(), synthesizer.getSampleRate());
                            Log.d(TAG, "Saved output audio to: " + outputFile.getAbsolutePath());
                        } catch (IOException e) {
                            Log.e(TAG, "Error saving output audio", e);
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error saving audio: " + e.getMessage(), Toast.LENGTH_LONG).show());
                            return;
                        }
    
                        // Play the audio
                        runOnUiThread(() -> {
                            Log.d(TAG, "Playing response audio");
                            Toast.makeText(MainActivity.this, "Audio processing complete", Toast.LENGTH_SHORT).show();
                            AudioProcessor.playAudio(responseAudio, synthesizer.getSampleRate());
                        });
                    }
    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "API call error: " + error);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error communicating with the API: " + error, Toast.LENGTH_LONG).show());
                    }
                });
    
            } catch (IOException | OrtException | InterruptedException e) {
                Log.e(TAG, "Error processing input", e);
                runOnUiThread(() -> Toast.makeText(this, "Error processing input: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private float[] concatenateAudio(List<float[]> audioChunks) {
        int totalLength = audioChunks.stream().mapToInt(chunk -> chunk.length).sum();
        float[] result = new float[totalLength];
        int currentIndex = 0;
        for (float[] chunk : audioChunks) {
            System.arraycopy(chunk, 0, result, currentIndex, chunk.length);
            currentIndex += chunk.length;
        }
        Log.d(TAG, "Concatenated " + audioChunks.size() + " audio chunks into " + totalLength + " samples");
        return result;
    }
}