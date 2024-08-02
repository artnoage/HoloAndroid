package com.vaios.holobar;
import com.vaios.holobar.R;
import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import ai.onnxruntime.OrtException;

public class MainActivity extends Activity {
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

        inputText = findViewById(R.id.input_text);
        speakerSpinner = findViewById(R.id.speaker_spinner);
        Button processButton = findViewById(R.id.process_button);

        // Set up speaker spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.speaker_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        speakerSpinner.setAdapter(adapter);

        initializeModels();

        processButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = inputText.getText().toString().trim();
                int speakerId = speakerSpinner.getSelectedItemPosition();
                if (!text.isEmpty()) {
                    processUserInput(text, speakerId);
                } else {
                    Toast.makeText(MainActivity.this, "Input was empty. Please try again.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initializeModels() {
        try {
            synthesizer = new VitsOnnxSynthesizer(this, VITS_MODEL_FILE);

            Properties config = new Properties();
            try (InputStream configStream = getAssets().open("tokenizer_config.properties")) {
                config.load(configStream);
            }

            HashMap<String, Integer> tokenToIdx = new HashMap<>();
            // Set up tokenToIdx map (you'll need to implement this based on your requirements)

            Phonemizer phonemizer = new Phonemizer(config, tokenToIdx);
            Phonemizer.initialize(getAssets(), PHONEMIZER_MODEL_FILE);

            tokenizer = new Tokenizer(this);

            apiCall = new ApiCall(this, GEMINI_API_KEY);
        } catch (IOException | OrtException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing models: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private List<String> splitTextIntoChunks(String text) {
        return Arrays.asList(text.split("(?<=[.!?])\\s+"));
    }

    private float[] concatenateAudio(List<float[]> audioChunks) {
        int totalLength = 0;
        for (float[] chunk : audioChunks) {
            totalLength += chunk.length;
        }
        float[] result = new float[totalLength];
        int currentIndex = 0;
        for (float[] chunk : audioChunks) {
            System.arraycopy(chunk, 0, result, currentIndex, chunk.length);
            currentIndex += chunk.length;
        }
        return result;
    }

    private void processUserInput(String input, int speakerId) {
        Toast.makeText(this, "Processing input...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                // Tokenize and phonemize input if needed
                List<Integer> inputTokens = tokenizer.textToIds(input);
                // Use inputTokens if needed in your processing pipeline

                // Text to audio
                float[] inputAudio = synthesizer.tts(input, speakerId);

                // Save input audio
                File inputAudioFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "input_audio.wav");
                AudioProcessor.saveWav(inputAudio, inputAudioFile.getAbsolutePath(), synthesizer.getSampleRate());

                // Send audio to FastAPI and get text response
                apiCall.sendAudioToApi(inputAudioFile, speakerId, new ApiCall.ApiCallCallback() {
                    @Override
                    public void onSuccess(String apiResponseText) {
                        // Process the response
                        List<String> textChunks = splitTextIntoChunks(apiResponseText);

                        // Tokenize and phonemize response if needed
                        List<Integer> responseTokens = tokenizer.textToIds(apiResponseText);
                        // Use responseTokens if needed in your processing pipeline

                        // Synthesize each chunk
                        List<float[]> audioChunks = new ArrayList<>();
                        for (String chunk : textChunks) {
                            try {
                                float[] chunkAudio = synthesizer.tts(chunk, speakerId);
                                audioChunks.add(chunkAudio);
                            } catch (Exception e) {
                                e.printStackTrace();
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error synthesizing audio: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                return;
                            }
                        }

                        // Concatenate all audio chunks
                        float[] responseAudio = concatenateAudio(audioChunks);

                        // Save the final output audio
                        File outputFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "output_audio.wav");
                        try {
                            AudioProcessor.saveWav(responseAudio, outputFile.getAbsolutePath(), synthesizer.getSampleRate());
                        } catch (IOException e) {
                            e.printStackTrace();
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error saving audio: " + e.getMessage(), Toast.LENGTH_LONG).show());
                            return;
                        }

                        // Play the audio
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Audio processing complete", Toast.LENGTH_SHORT).show();
                            AudioProcessor.playAudio(responseAudio, synthesizer.getSampleRate());
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error communicating with the API: " + error, Toast.LENGTH_LONG).show());
                    }
                });

            } catch (IOException | OrtException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error processing input: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}