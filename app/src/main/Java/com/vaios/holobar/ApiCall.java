package com.vaios.holobar;
import android.content.Context;
import android.util.Log;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ApiCall {
    private static final String API_URL = "http://35.224.101.18:8000/talk_to_agents/";
    private static final String TAG = "ApiCall";
    private final String geminiApiKey;
    private final RequestQueue requestQueue;
    private final Context context;

    public ApiCall(Context context, String geminiApiKey) {
        this.context = context;
        this.geminiApiKey = geminiApiKey;
        this.requestQueue = Volley.newRequestQueue(context);
        initializeZeroHistory();
    }

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
            } catch (IOException e) {
                Log.e(TAG, "Error initializing zero_history.pickle", e);
            }
        }
    }

    public interface ApiCallCallback {
        void onSuccess(String narration);
        void onError(String error);
    }

    public void sendAudioToApi(File audioFile, int speakerId, ApiCallCallback callback) {
        MultipartRequest multipartRequest = new MultipartRequest(Request.Method.POST, API_URL,
                response -> {
                    try {
                        String responseBody = new String(response.data);
                        Log.d(TAG, "Response body: " + responseBody);
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String narration = jsonResponse.getString("narration");
                        String updatedHistory = jsonResponse.getString("updated_history");
                        saveUpdatedHistory(updatedHistory);
                        callback.onSuccess(narration);
                    } catch (JSONException | IOException e) {
                        Log.e(TAG, "Error processing response", e);
                        callback.onError("Error processing response: " + e.getMessage());
                    }
                },
                error -> {
                    Log.e(TAG, "Error in API call", error);
                    callback.onError("API call failed: " + error.getMessage());
                }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("agent_number", String.valueOf(speakerId));
                params.put("gemini_api_key", geminiApiKey);
                return params;
            }

            @Override
            protected Map<String, DataPart> getByteData() {
                Map<String, DataPart> params = new HashMap<>();
                try {
                    params.put("audio_file", new DataPart("audio.wav", readFileToBytes(audioFile), "audio/wav"));

                    File historyFile = new File(context.getFilesDir(), "updated_history.pickle");
                    if (!historyFile.exists()) {
                        historyFile = new File(context.getFilesDir(), "zero_history.pickle");
                    }
                    params.put("history_file", new DataPart(historyFile.getName(), readFileToBytes(historyFile), "application/octet-stream"));
                } catch (IOException e) {
                    Log.e(TAG, "Error reading files", e);
                }
                return params;
            }
        };

        requestQueue.add(multipartRequest);
    }

    private void saveUpdatedHistory(String updatedHistory) throws IOException {
        File historyFile = new File(context.getFilesDir(), "updated_history.pickle");
        try (FileOutputStream fos = new FileOutputStream(historyFile)) {
            fos.write(updatedHistory.getBytes());
        }
        Log.d(TAG, "Updated history saved to: " + historyFile.getAbsolutePath());
    }

    private byte[] readFileToBytes(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(bytes);
        }
        return bytes;
    }

    public class MultipartRequest extends Request<NetworkResponse> {
        private final Response.Listener<NetworkResponse> mListener;
        private final Response.ErrorListener mErrorListener;
        private final Map<String, String> mStringParts;
        private final Map<String, DataPart> mDataParts;

        public MultipartRequest(int method, String url,
                                Response.Listener<NetworkResponse> listener,
                                Response.ErrorListener errorListener) {
            super(method, url, errorListener);
            this.mListener = listener;
            this.mErrorListener = errorListener;
            this.mStringParts = new HashMap<>();
            this.mDataParts = new HashMap<>();
        }

        @Override
        protected Map<String, String> getParams() {
            return mStringParts;
        }


        protected Map<String, DataPart> getByteData() {
            return mDataParts;
        }

        @Override
        protected void deliverResponse(NetworkResponse response) {
            mListener.onResponse(response);
        }

        @Override
        protected Response<NetworkResponse> parseNetworkResponse(NetworkResponse response) {
            return Response.success(response, HttpHeaderParser.parseCacheHeaders(response));
        }

        @Override
        public void deliverError(VolleyError error) {
            mErrorListener.onErrorResponse(error);
        }
    }

    public class DataPart {
        private String fileName;
        private byte[] content;
        private String type;

        public DataPart(String name, byte[] data, String mimeType) {
            fileName = name;
            content = data;
            type = mimeType;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getContent() {
            return content;
        }

        public String getType() {
            return type;
        }
    }
}