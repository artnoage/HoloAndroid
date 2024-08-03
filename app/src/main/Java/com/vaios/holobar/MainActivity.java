package com.vaios.holobar;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import java.io.IOException;
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
                    AudioProcessor.playAudio(inputAudio, synthesizer.getSampleRate());
                });

                // Wait for audio to finish playing (you might want to adjust this)
                Thread.sleep((long) inputAudio.length * 1000L / synthesizer.getSampleRate() + 500L);

                // Send audio to FastAPI and get text response
                Log.d(TAG, "Sending audio to API");
                apiCall.sendAudioToApi(inputAudio, speakerId, synthesizer.getSampleRate(), new ApiCall.ApiCallCallback() {
                    @Override
                    public void onSuccess(String narration, String status) {
                        // Handle success
                        Log.d(TAG, "API Response received: " + narration);
                        Log.d(TAG, "API Status: " + status);
                        // Process the response
                        List<String> textChunks = Arrays.asList(narration.split("(?<=[.!?])\\s+"));
                        Log.d(TAG, "Split text into " + textChunks.size() + " chunks");

                        // Synthesize and play each chunk
                        for (String chunk : textChunks) {
                            try {
                                float[] chunkAudio = synthesizer.tts(chunk, speakerId);
                                Log.d(TAG, "Synthesized chunk: " + chunk.substring(0, Math.min(chunk.length(), 50)) + "...");

                                // Play each chunk as it's produced
                                runOnUiThread(() -> AudioProcessor.playAudio(chunkAudio, synthesizer.getSampleRate()));

                                // Wait for the chunk to finish playing before synthesizing the next one
                                Thread.sleep((long) chunkAudio.length * 1000L / synthesizer.getSampleRate());
                            } catch (Exception e) {
                                Log.e(TAG, "Error synthesizing or playing audio chunk", e);
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error synthesizing audio: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                return;
                            }
                        }

                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Audio processing complete", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "API call error: " + error);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error communicating with the API: " + error, Toast.LENGTH_LONG).show());
                    }
                });

            } catch (OrtException | InterruptedException e) {
                Log.e(TAG, "Error processing input", e);
                runOnUiThread(() -> Toast.makeText(this, "Error processing input: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}