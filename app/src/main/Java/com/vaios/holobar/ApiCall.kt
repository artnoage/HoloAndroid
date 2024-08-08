package com.vaios.holobar

import android.content.Context
import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.*
import java.nio.charset.StandardCharsets

class ApiCall(private val context: Context, private val geminiApiKey: String) {
    companion object {
        private const val API_BASE_URL = "https://fastapi.metaskepsis.com"
        private const val TAG = "ApiCall"
        private const val MY_SOCKET_TIMEOUT_MS = 30000 // 30 seconds
        private const val MY_MAX_RETRIES = 3
    }

    private val requestQueue: RequestQueue = Volley.newRequestQueue(context)

    init {
        initializeZeroHistory()
    }

    private fun initializeZeroHistory() {
        val zeroHistoryFile = File(context.filesDir, "zero_history.pickle")
        if (!zeroHistoryFile.exists()) {
            try {
                context.assets.open("zero_history.pickle").use { input ->
                    FileOutputStream(zeroHistoryFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Zero history file initialized: ${zeroHistoryFile.absolutePath}")
            } catch (e: IOException) {
                Log.e(TAG, "Error initializing zero_history.pickle", e)
            }
        } else {
            Log.d(TAG, "Zero history file already exists: ${zeroHistoryFile.absolutePath}")
        }
    }

    interface ApiKeyValidationCallback {
        fun onSuccess(status: String, message: String)
        fun onError(error: String)
    }

    interface ChatApiCallback {
        fun onSuccess(narration: String, status: String)
        fun onError(error: String)
    }

    fun checkApiKey(apiKey: String, callback: ApiKeyValidationCallback) {
        val url = "$API_BASE_URL/check_api_key/"

        val jsonBody = JSONObject().apply {
            put("gemini_api_key", apiKey)
        }

        val request = object : VolleyJsonObjectRequest(
            Method.POST, url, jsonBody,
            { response ->
                val status = response.getString("status")
                val message = response.getString("message")
                callback.onSuccess(status, message)
            },
            { error ->
                val errorMessage = when (error) {
                    is TimeoutError -> "Request timed out. Please check your internet connection."
                    is NoConnectionError -> "No internet connection. Please check your network settings."
                    is AuthFailureError -> "Authentication failure. Please check your API key."
                    is ServerError -> "Server error. Please try again later."
                    else -> error.message ?: "Unknown error occurred"
                }
                callback.onError("API key validation failed: $errorMessage")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return mutableMapOf("Content-Type" to "application/json")
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            MY_SOCKET_TIMEOUT_MS,
            MY_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        requestQueue.add(request)
    }

    fun talkToAgents(text: String, speakerId: Int, callback: ChatApiCallback) {
        Log.d(TAG, "Preparing to send text to API. Text length: ${text.length} characters, Speaker ID: $speakerId")

        val historyFile = getHistoryFile()

        if (!historyFile.exists() || !historyFile.canRead()) {
            Log.e(TAG, "History file does not exist or is not readable: ${historyFile.absolutePath}")
            callback.onError("History file is not accessible")
            return
        }

        Log.d(TAG, "Using history file: ${historyFile.name} from path: ${historyFile.absolutePath}")

        val multipartRequest = object : MultipartRequest(
            Method.POST,
            "$API_BASE_URL/talk_to_agents/",
            { response ->
                handleApiResponse(response, callback)
            },
            { error ->
                handleApiError(error, callback)
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf(
                    "agent_number" to speakerId.toString(),
                    "gemini_api_key" to geminiApiKey,
                    "text" to text
                )
            }

            override fun getByteData(): MutableMap<String, DataPart> {
                val params = HashMap<String, DataPart>()
                params["history_file"] = DataPart(historyFile.name, historyFile.readBytes(), "application/octet-stream")
                return params
            }
        }

        multipartRequest.retryPolicy = DefaultRetryPolicy(
            MY_SOCKET_TIMEOUT_MS,
            MY_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        requestQueue.add(multipartRequest)
        Log.d(TAG, "Request added to queue with custom timeout and retry policy")
    }

    fun speakToAgents(audioData: ByteArray, speakerId: Int, callback: ChatApiCallback) {
        Log.d(TAG, "Preparing to send audio to API. Audio size: ${audioData.size} bytes, Speaker ID: $speakerId")

        val historyFile = getHistoryFile()

        if (!historyFile.exists() || !historyFile.canRead()) {
            Log.e(TAG, "History file does not exist or is not readable: ${historyFile.absolutePath}")
            callback.onError("History file is not accessible")
            return
        }

        Log.d(TAG, "Using history file: ${historyFile.name} from path: ${historyFile.absolutePath}")

        val multipartRequest = object : MultipartRequest(
            Method.POST,
            "$API_BASE_URL/speak_to_agents/",
            { response ->
                handleApiResponse(response, callback)
            },
            { error ->
                handleApiError(error, callback)
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf(
                    "agent_number" to speakerId.toString(),
                    "gemini_api_key" to geminiApiKey
                )
            }

            override fun getByteData(): MutableMap<String, DataPart> {
                val params = HashMap<String, DataPart>()
                params["audio_file"] = DataPart("audio.raw", audioData, "application/octet-stream")
                params["history_file"] = DataPart(historyFile.name, historyFile.readBytes(), "application/octet-stream")
                return params
            }
        }

        multipartRequest.retryPolicy = DefaultRetryPolicy(
            MY_SOCKET_TIMEOUT_MS,
            MY_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        requestQueue.add(multipartRequest)
        Log.d(TAG, "Request added to queue with custom timeout and retry policy")
    }

    private fun getHistoryFile(): File {
        return File(context.filesDir, "updated_history.pickle").let { file ->
            if (!file.exists() || !file.canRead()) {
                Log.d(TAG, "Updated history file not found or not readable. Attempting to use zero_history.pickle")
                File(context.filesDir, "zero_history.pickle")
            } else {
                file
            }
        }
    }

    private fun handleApiResponse(response: NetworkResponse, callback: ChatApiCallback) {
        try {
            val responseBody = String(response.data, StandardCharsets.UTF_8)
            Log.d(TAG, "API Response body: $responseBody")
            val jsonResponse = JSONObject(responseBody)
            val narration = jsonResponse.getString("narration")
            val updatedHistory = jsonResponse.getString("updated_history")
            val status = jsonResponse.getString("status")
            saveUpdatedHistory(updatedHistory)
            Log.d(TAG, "Narration received and history updated")
            Log.d(TAG, "Status: $status")
            callback.onSuccess(narration, status)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing response: ${Log.getStackTraceString(e)}")
            callback.onError("Error processing response: ${e.message}")
        }
    }

    private fun handleApiError(error: VolleyError, callback: ChatApiCallback) {
        Log.e(TAG, "Error in API call: ${Log.getStackTraceString(error)}")
        error.networkResponse?.let { response ->
            Log.e(TAG, "Error status code: ${response.statusCode}")
            Log.e(TAG, "Error body: ${String(response.data)}")
        }
        val errorMessage = when (error) {
            is TimeoutError -> "Request timed out. Please check your internet connection and try again."
            is NoConnectionError -> "No internet connection. Please check your network settings and try again."
            is AuthFailureError -> "Authentication failure. Please check your API key and try again."
            is ServerError -> "Server error. Please try again later."
            else -> error.message ?: "Unknown error occurred"
        }
        callback.onError("API call failed: $errorMessage")
    }

    @Throws(IOException::class)
    private fun saveUpdatedHistory(updatedHistory: String) {
        val historyFile = File(context.filesDir, "updated_history.pickle")
        historyFile.writeText(updatedHistory, StandardCharsets.UTF_8)
        Log.d(TAG, "Updated history saved to: ${historyFile.absolutePath}")
    }

    inner class DataPart(private val fileName: String, private val data: ByteArray, private val type: String) {
        fun getFileName(): String = fileName
        fun getContent(): ByteArray = data
        fun getMimeType(): String = type
    }

    abstract class MultipartRequest(
        method: Int,
        url: String,
        private val responseListener: Response.Listener<NetworkResponse>,
        errorListener: Response.ErrorListener
    ) : Request<NetworkResponse>(method, url, errorListener) {

        abstract fun getByteData(): MutableMap<String, DataPart>?

        override fun getBodyContentType(): String {
            return "multipart/form-data;boundary=$BOUNDARY"
        }

        @Throws(AuthFailureError::class)
        override fun getBody(): ByteArray {
            val bos = ByteArrayOutputStream()
            val dos = DataOutputStream(bos)

            try {
                // Add string params
                for ((key, value) in params ?: emptyMap()) {
                    buildTextPart(dos, key, value)
                }

                // Add data byte params
                for ((key, value) in getByteData() ?: emptyMap()) {
                    buildDataPart(dos, key, value)
                }

                // Close multipart form data after adding all parts
                dos.writeBytes("--$BOUNDARY--$LINE_END")

                return bos.toByteArray()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return ByteArray(0)
        }

        @Throws(IOException::class)
        private fun buildTextPart(dataOutputStream: DataOutputStream, parameterName: String, parameterValue: String) {
            dataOutputStream.writeBytes("--$BOUNDARY$LINE_END")
            dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"$parameterName\"$LINE_END")
            dataOutputStream.writeBytes("Content-Type: text/plain; charset=UTF-8$LINE_END")
            dataOutputStream.writeBytes(LINE_END)
            dataOutputStream.writeBytes("$parameterValue$LINE_END")
        }

        @Throws(IOException::class)
        private fun buildDataPart(dataOutputStream: DataOutputStream, parameterName: String, dataFile: DataPart) {
            dataOutputStream.writeBytes("--$BOUNDARY$LINE_END")
            dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"$parameterName\"; filename=\"${dataFile.getFileName()}\"$LINE_END")
            if (dataFile.getMimeType().isNotBlank()) {
                dataOutputStream.writeBytes("Content-Type: ${dataFile.getMimeType()}$LINE_END")
            }
            dataOutputStream.writeBytes(LINE_END)

            val fileInputStream = ByteArrayInputStream(dataFile.getContent())
            var bytesAvailable = fileInputStream.available()
            val maxBufferSize = 1024 * 1024
            var bufferSize = minOf(bytesAvailable, maxBufferSize)
            val buffer = ByteArray(bufferSize)

            var bytesRead = fileInputStream.read(buffer, 0, bufferSize)
            while (bytesRead > 0) {
                dataOutputStream.write(buffer, 0, bufferSize)
                bytesAvailable = fileInputStream.available()
                bufferSize = minOf(bytesAvailable, maxBufferSize)
                bytesRead = fileInputStream.read(buffer, 0, bufferSize)
            }

            dataOutputStream.writeBytes(LINE_END)
        }

        override fun parseNetworkResponse(response: NetworkResponse): Response<NetworkResponse> {
            return Response.success(response, HttpHeaderParser.parseCacheHeaders(response))
        }

        override fun deliverResponse(response: NetworkResponse) {
            responseListener.onResponse(response)
        }

        companion object {
            private const val LINE_END = "\r\n"
            private val BOUNDARY = "apicall" + System.currentTimeMillis()
        }
    }

    private open class VolleyJsonObjectRequest(
        method: Int,
        url: String,
        private val jsonRequest: JSONObject?,
        private val listener: Response.Listener<JSONObject>,
        errorListener: Response.ErrorListener
    ) : Request<JSONObject>(method, url, errorListener) {

        override fun getBodyContentType(): String {
            return "application/json; charset=utf-8"
        }

        @Throws(AuthFailureError::class)
        override fun getBody(): ByteArray {
            return jsonRequest?.toString()?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        }

        override fun parseNetworkResponse(response: NetworkResponse): Response<JSONObject> {
            return try {
                val jsonString = String(response.data, StandardCharsets.UTF_8)
                val result = JSONObject(jsonString)
                Response.success(result, HttpHeaderParser.parseCacheHeaders(response))
            } catch (e: Exception) {
                Response.error(ParseError(e))
            }
        }

        override fun deliverResponse(response: JSONObject) {
            listener.onResponse(response)
        }
    }
}