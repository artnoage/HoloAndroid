package com.vaios.holobar;

import android.content.Context;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ApiCall {
    private static final String API_URL = "https://fastapi.metaskepsis.com/talk_to_agents/";
    private static final String TAG = "ApiCall";
    private final String geminiApiKey;
    private final RequestQueue requestQueue;
    private final Context context;

    // Timeout constants
    private static final int MY_SOCKET_TIMEOUT_MS = 30000; // 30 seconds
    private static final int MY_MAX_RETRIES = 2;

    /**
     * Constructor for ApiCall class
     * @param context Application context
     * @param geminiApiKey API key for Gemini
     */
    public ApiCall(Context context, String geminiApiKey) {
        this.context = context;
        this.geminiApiKey = geminiApiKey;
        this.requestQueue = Volley.newRequestQueue(context);
        initializeZeroHistory();
    }

    /**
     * Initializes the zero history file if it doesn't exist.
     * This is crucial for the game's state management when starting fresh.
     */
    private void initializeZeroHistory() {
        File zeroHistoryFile = new File(context.getFilesDir(), "zero_history.pickle");
        if (!zeroHistoryFile.exists()) {
            try (InputStream is = context.getAssets().open("zero_history.pickle");
                 FileOutputStream fos = new FileOutputStream(zeroHistoryFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                Log.d(TAG, "Zero history file initialized: " + zeroHistoryFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e(TAG, "Error initializing zero_history.pickle", e);
            }
        } else {
            Log.d(TAG, "Zero history file already exists: " + zeroHistoryFile.getAbsolutePath());
        }
    }

    /**
     * Interface for API call callbacks
     */
    public interface ApiCallCallback {
        void onSuccess(String narration, String status);
        void onError(String error);
    }

    /**
     * Sends audio data to the API
     * @param audio Float array of audio data
     * @param speakerId ID of the speaker
     * @param sampleRate Sample rate of the audio
     * @param callback Callback for API response
     */
    public void sendAudioToApi(float[] audio, int speakerId, int sampleRate, ApiCallCallback callback) {
        Log.d(TAG, "Preparing to send audio to API. Audio length: " + audio.length + " samples, Speaker ID: " + speakerId);

        // Get the history file
        File historyFile = new File(context.getFilesDir(), "updated_history.pickle");
        if (!historyFile.exists() || !historyFile.canRead()) {
            Log.d(TAG, "Updated history file not found or not readable. Attempting to use zero_history.pickle");
            historyFile = new File(context.getFilesDir(), "zero_history.pickle");
            if (!historyFile.exists() || !historyFile.canRead()) {
                Log.e(TAG, "Zero history file does not exist or is not readable: " + historyFile.getAbsolutePath());
                callback.onError("History file is not accessible");
                return;
            }
        }
        Log.d(TAG, "Using history file: " + historyFile.getName() + " from path: " + historyFile.getAbsolutePath());

        MultipartRequest multipartRequest = new MultipartRequest(Request.Method.POST, API_URL,
                response -> {
                    try {
                        String responseBody = new String(response.data);
                        Log.d(TAG, "API Response body: " + responseBody);
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String narration = jsonResponse.getString("narration");
                        String updatedHistory = jsonResponse.getString("updated_history");
                        String status = jsonResponse.getString("status");
                        saveUpdatedHistory(updatedHistory);
                        Log.d(TAG, "Narration received and history updated");
                        Log.d(TAG, "Status: " + status);
                        callback.onSuccess(narration, status);
                    } catch (JSONException | IOException e) {
                        Log.e(TAG, "Error processing response: " + Log.getStackTraceString(e));
                        callback.onError("Error processing response: " + e.getMessage());
                    }
                },
                error -> {
                    Log.e(TAG, "Error in API call: " + Log.getStackTraceString(error));
                    if (error.networkResponse != null) {
                        Log.e(TAG, "Error status code: " + error.networkResponse.statusCode);
                        Log.e(TAG, "Error body: " + new String(error.networkResponse.data));
                    }
                    String errorMessage = "API call failed: ";
                    if (error instanceof com.android.volley.TimeoutError) {
                        errorMessage += "Request timed out. Please check your internet connection and try again.";
                    } else if (error instanceof com.android.volley.NoConnectionError) {
                        errorMessage += "No internet connection. Please check your network settings and try again.";
                    } else if (error instanceof com.android.volley.AuthFailureError) {
                        errorMessage += "Authentication failure. Please check your API key and try again.";
                    } else if (error instanceof com.android.volley.ServerError) {
                        errorMessage += "Server error. Please try again later.";
                    } else {
                        errorMessage += error.getMessage();
                    }
                    callback.onError(errorMessage);
                });

        multipartRequest.addStringPart("agent_number", String.valueOf(speakerId));
        multipartRequest.addStringPart("gemini_api_key", geminiApiKey);
        multipartRequest.addWavAudioPart("audio_file", audio, sampleRate);
        multipartRequest.addHistoryFilePart("history_file", historyFile);

        multipartRequest.setRetryPolicy(new DefaultRetryPolicy(
                MY_SOCKET_TIMEOUT_MS,
                MY_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        requestQueue.add(multipartRequest);
        Log.d(TAG, "Request added to queue with custom timeout and retry policy");
    }

    /**
     * Saves the updated history received from the API
     * This is crucial for maintaining the game state across interactions
     * @param updatedHistory Base64 encoded updated history string
     * @throws IOException If there's an error writing to the file
     */
    private void saveUpdatedHistory(String updatedHistory) throws IOException {
        File historyFile = new File(context.getFilesDir(), "updated_history.pickle");
        try (FileOutputStream fos = new FileOutputStream(historyFile)) {
            fos.write(updatedHistory.getBytes(StandardCharsets.UTF_8));
        }
        Log.d(TAG, "Updated history saved to: " + historyFile.getAbsolutePath());
    }

    /**
     * Inner class for handling multipart requests
     */
    private static class MultipartRequest extends Request<NetworkResponse> {
        private final Response.Listener<NetworkResponse> mListener;
        private final Map<String, String> mStringParts = new HashMap<>();
        private final Map<String, AudioData> mAudioParts = new HashMap<>();
        private final Map<String, File> mFileParts = new HashMap<>();
        private final String boundary = "apicall" + System.currentTimeMillis();

        public MultipartRequest(int method, String url,
                                Response.Listener<NetworkResponse> listener,
                                Response.ErrorListener errorListener) {
            super(method, url, errorListener);
            this.mListener = listener;
        }

        public void addStringPart(String name, String value) {
            mStringParts.put(name, value);
        }

        public void addWavAudioPart(String name, float[] audio, int sampleRate) {
            mAudioParts.put(name, new AudioData(audio, sampleRate));
        }

        public void addHistoryFilePart(String name, File file) {
            mFileParts.put(name, file);
        }

        @Override
        public String getBodyContentType() {
            return "multipart/form-data;boundary=" + boundary;
        }

        @Override
        public byte[] getBody() {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                for (Map.Entry<String, String> entry : mStringParts.entrySet()) {
                    buildTextPart(bos, entry.getKey(), entry.getValue());
                }

                for (Map.Entry<String, AudioData> entry : mAudioParts.entrySet()) {
                    buildAudioPart(bos, entry.getKey(), entry.getValue());
                }

                for (Map.Entry<String, File> entry : mFileParts.entrySet()) {
                    buildHistoryFilePart(bos, entry.getKey(), entry.getValue());
                }

                bos.write(("--" + boundary + "--\r\n").getBytes());
            } catch (IOException e) {
                Log.e(TAG, "Error creating multipart request body: " + Log.getStackTraceString(e));
            }
            return bos.toByteArray();
        }

        private void buildTextPart(ByteArrayOutputStream bos, String parameterName, String parameterValue) throws IOException {
            bos.write(("--" + boundary + "\r\n").getBytes());
            bos.write(("Content-Disposition: form-data; name=\"" + parameterName + "\"\r\n\r\n").getBytes());
            bos.write((parameterValue + "\r\n").getBytes());
        }

        private void buildAudioPart(ByteArrayOutputStream bos, String parameterName, AudioData audioData) throws IOException {
            bos.write(("--" + boundary + "\r\n").getBytes());
            bos.write(("Content-Disposition: form-data; name=\"" + parameterName + "\"; filename=\"audio.wav\"\r\n").getBytes());
            bos.write(("Content-Type: audio/wav\r\n\r\n").getBytes());

            byte[] wavBytes = floatArrayToWav(audioData.audio, audioData.sampleRate);
            bos.write(wavBytes);
            bos.write("\r\n".getBytes());
        }

        private void buildHistoryFilePart(ByteArrayOutputStream bos, String parameterName, File file) throws IOException {
            bos.write(("--" + boundary + "\r\n").getBytes());
            bos.write(("Content-Disposition: form-data; name=\"" + parameterName + "\"; filename=\"" + file.getName() + "\"\r\n").getBytes());
            bos.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes());

            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            bos.write("\r\n".getBytes());
            fileInputStream.close();
        }

        /**
         * Converts float array to WAV byte array
         * @param audio Float array of audio data
         * @param sampleRate Sample rate of the audio
         * @return Byte array of WAV data
         */
        private byte[] floatArrayToWav(float[] audio, int sampleRate) {
            byte[] audioData = new byte[audio.length * 2];
            int bytesPerSample = 2;
            int numChannels = 1; // Mono
            int byteRate = sampleRate * numChannels * bytesPerSample;
            int blockAlign = numChannels * bytesPerSample;

            for (int i = 0; i < audio.length; i++) {
                short val = (short) (audio[i] * 32767);
                audioData[i * 2] = (byte) (val & 0x00FF);
                audioData[i * 2 + 1] = (byte) ((val & 0xFF00) >>> 8);
            }

            ByteBuffer buffer = ByteBuffer.allocate(44 + audioData.length);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // WAV header
            buffer.put("RIFF".getBytes());
            buffer.putInt(36 + audioData.length);
            buffer.put("WAVE".getBytes());
            buffer.put("fmt ".getBytes());
            buffer.putInt(16); // Sub-chunk size
            buffer.putShort((short) 1); // PCM audio format
            buffer.putShort((short) numChannels);
            buffer.putInt(sampleRate);
            buffer.putInt(byteRate);
            buffer.putShort((short) blockAlign);
            buffer.putShort((short) (bytesPerSample * 8)); // Bits per sample
            buffer.put("data".getBytes());
            buffer.putInt(audioData.length);
            buffer.put(audioData);

            return buffer.array();
        }

        @Override
        protected Response<NetworkResponse> parseNetworkResponse(NetworkResponse response) {
            return Response.success(response, HttpHeaderParser.parseCacheHeaders(response));
        }

        @Override
        protected void deliverResponse(NetworkResponse response) {
            mListener.onResponse(response);
        }

        private static class AudioData {
            float[] audio;
            int sampleRate;

            AudioData(float[] audio, int sampleRate) {
                this.audio = audio;
                this.sampleRate = sampleRate;
            }
        }
    }
}