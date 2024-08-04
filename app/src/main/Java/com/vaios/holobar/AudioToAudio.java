package com.vaios.holobar;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import ai.onnxruntime.OrtException;

public class AudioToAudio {
    private static final String TAG = "AudioToAudio";
    private static final int SAMPLE_RATE = 22050;
    private static final String VITS_MODEL_FILE = "vits_model.onnx";
    private static final String PHONEMIZER_MODEL_FILE = "phonemizer_model.onnx";
    private static final String GEMINI_API_KEY = "AIzaSyBzw4vQsyQFcpA9hr4ncge4BYZW0CcT_ng";

    private VitsOnnxSynthesizer synthesizer;
    private ApiCall apiCall;
    private final Context context;

    public AudioToAudio(Context context) {
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

    public void processAudio(float[] audio, int speakerId, ApiCall.ApiCallCallback callback) {
        Log.d(TAG, "Processing audio. Length: " + audio.length + ", Speaker ID: " + speakerId);
        sendAudioToApi(audio, speakerId, callback);
    }

    private void sendAudioToApi(float[] audio, int speakerId, ApiCall.ApiCallCallback callback) {
        Log.d(TAG, "Sending audio to API. Length: " + audio.length + ", Speaker ID: " + speakerId);

        apiCall.sendAudioToApi(audio, speakerId, SAMPLE_RATE, new ApiCall.ApiCallCallback() {
            @Override
            public void onSuccess(String narration, String status) {
                Log.d(TAG, "API Response received: " + narration);
                Log.d(TAG, "API Status: " + status);

                try {
                    float[] responseAudio = synthesizer.tts(narration, speakerId);
                    callback.onSuccess(narration, status);
                    // The actual playing of audio will be handled in MainActivity
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

    // Added getter method for synthesizer
    public VitsOnnxSynthesizer getSynthesizer() {
        return synthesizer;
    }
}