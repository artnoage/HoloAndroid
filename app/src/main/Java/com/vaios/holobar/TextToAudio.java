package com.vaios.holobar;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import ai.onnxruntime.OrtException;

public class TextToAudio {
    private static final String TAG = "TExtToAudio";
    private static final String VITS_MODEL_FILE = "vits_model.onnx";
    private static final String PHONEMIZER_MODEL_FILE = "phonemizer_model.onnx";
    private static final String GEMINI_API_KEY = "AIzaSyBzw4vQsyQFcpA9hr4ncge4BYZW0CcT_ng";

    private VitsOnnxSynthesizer synthesizer;
    private ApiCall apiCall;
    private final Context context;

    // Hardcoded matrix for speaker ID mapping
    private static final int[][] SPEAKER_ID_MATRIX = {
            {0, 79},
            {1, 90},
            {2, 33},
            {3, 109},
            {4, 100}
    };

    public TextToAudio(Context context) {
        this.context = context;
        initializeModels();
    }

    private void initializeModels() {
        Log.d(TAG, "Initializing models");
        try {
            synthesizer = new VitsOnnxSynthesizer(context, VITS_MODEL_FILE);
            Log.d(TAG, "VITS synthesizer initialized");

            Phonemizer.initialize(context.getAssets(), PHONEMIZER_MODEL_FILE);
            Log.d(TAG, "Phonemizer initialized");

            apiCall = new ApiCall(context, GEMINI_API_KEY);
            Log.d(TAG, "ApiCall initialized");

        } catch (IOException | OrtException e) {
            Log.e(TAG, "Error initializing models", e);
            throw new RuntimeException("Error initializing models: " + e.getMessage());
        }
    }

    public void processText(String text, int speakerId, TextToAudioCallback callback) {
        Log.d(TAG, "Processing text. Text length: " + text.length() + ", Speaker ID: " + speakerId);

        apiCall.sendTextToApi(text, speakerId, new ApiCall.ApiCallCallback() {
            @Override
            public void onSuccess(String narration, String status) {
                Log.d(TAG, "API Response received: " + narration);
                Log.d(TAG, "API Status: " + status);

                try {
                    // Map the API speaker ID to the TTS speaker ID
                    int ttsSpeakerId = mapSpeakerId(speakerId);
                    float[] responseAudio = synthesizer.tts(narration, ttsSpeakerId);
                    callback.onSuccess(narration, status, responseAudio);
                } catch (Exception e) {
                    Log.e(TAG, "Error synthesizing audio response", e);
                    callback.onError("Error synthesizing audio: " + e.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "API call error: " + error);
                callback.onError(error);
            }
        });
    }

    private int mapSpeakerId(int apiSpeakerId) {
        for (int[] mapping : SPEAKER_ID_MATRIX) {
            if (mapping[0] == apiSpeakerId) {
                return mapping[1];
            }
        }
        // Default to the first TTS speaker ID if no mapping is found
        return SPEAKER_ID_MATRIX[0][1];
    }


    // Custom callback interface for TextToAudio
    public interface TextToAudioCallback {
        void onSuccess(String narration, String status, float[] audioData);
        void onError(String error);
    }
}