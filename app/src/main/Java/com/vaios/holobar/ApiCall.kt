package com.vaios.holobar

import android.content.Context
import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.JsonObjectRequest
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

        val request = JsonObjectRequest(
            Request.Method.POST, url, jsonBody,
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
        ).apply {
            retryPolicy = DefaultRetryPolicy(
                MY_SOCKET_TIMEOUT_MS,
                MY_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        }

        requestQueue.add(request)
    }

    fun sendTextToApi(text: String, speakerId: Int, callback: ChatApiCallback) {
        Log.d(TAG, "Preparing to send text to API. Text length: ${text.length} characters, Speaker ID: $speakerId")

        val historyFile = File(context.filesDir, "updated_history.pickle").let { file ->
            if (!file.exists() || !file.canRead()) {
                Log.d(TAG, "Updated history file not found or not readable. Attempting to use zero_history.pickle")
                File(context.filesDir, "zero_history.pickle")
            } else {
                file
            }
        }

        if (!historyFile.exists() || !historyFile.canRead()) {
            Log.e(TAG, "History file does not exist or is not readable: ${historyFile.absolutePath}")
            callback.onError("History file is not accessible")
            return
        }

        Log.d(TAG, "Using history file: ${historyFile.name} from path: ${historyFile.absolutePath}")

        val multipartRequest = MultipartRequest(
            Request.Method.POST,
            "$API_BASE_URL/talk_to_agents/",
            { response ->
                try {
                    val responseBody = String(response.data)
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
            },
            { error ->
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
        ).apply {
            addStringPart("agent_number", speakerId.toString())
            addStringPart("gemini_api_key", geminiApiKey)
            addStringPart("text", text)
            addHistoryFilePart("history_file", historyFile)
            retryPolicy = DefaultRetryPolicy(
                MY_SOCKET_TIMEOUT_MS,
                MY_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        }

        requestQueue.add(multipartRequest)
        Log.d(TAG, "Request added to queue with custom timeout and retry policy")
    }

    @Throws(IOException::class)
    private fun saveUpdatedHistory(updatedHistory: String) {
        val historyFile = File(context.filesDir, "updated_history.pickle")
        historyFile.writeText(updatedHistory, StandardCharsets.UTF_8)
        Log.d(TAG, "Updated history saved to: ${historyFile.absolutePath}")
    }

    private inner class MultipartRequest(
        method: Int,
        url: String,
        private val listener: Response.Listener<NetworkResponse>,
        errorListener: Response.ErrorListener
    ) : Request<NetworkResponse>(method, url, errorListener) {

        private val stringParts = mutableMapOf<String, String>()
        private val fileParts = mutableMapOf<String, File>()
        private val boundary = "apicall${System.currentTimeMillis()}"

        fun addStringPart(name: String, value: String) {
            stringParts[name] = value
        }

        fun addHistoryFilePart(name: String, file: File) {
            fileParts[name] = file
        }

        override fun getBodyContentType(): String = "multipart/form-data;boundary=$boundary"

        override fun getBody(): ByteArray {
            val bos = ByteArrayOutputStream()
            try {
                for ((key, value) in stringParts) {
                    buildTextPart(bos, key, value)
                }

                for ((key, file) in fileParts) {
                    buildHistoryFilePart(bos, key, file)
                }

                bos.write("--$boundary--\r\n".toByteArray())
            } catch (e: IOException) {
                Log.e(TAG, "Error creating multipart request body: ${Log.getStackTraceString(e)}")
            }
            return bos.toByteArray()
        }

        @Throws(IOException::class)
        private fun buildTextPart(bos: ByteArrayOutputStream, parameterName: String, parameterValue: String) {
            bos.write("--$boundary\r\n".toByteArray())
            bos.write("Content-Disposition: form-data; name=\"$parameterName\"\r\n\r\n".toByteArray())
            bos.write("$parameterValue\r\n".toByteArray())
        }

        @Throws(IOException::class)
        private fun buildHistoryFilePart(bos: ByteArrayOutputStream, parameterName: String, file: File) {
            bos.write("--$boundary\r\n".toByteArray())
            bos.write("Content-Disposition: form-data; name=\"$parameterName\"; filename=\"${file.name}\"\r\n".toByteArray())
            bos.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())

            FileInputStream(file).use { fileInputStream ->
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                    bos.write(buffer, 0, bytesRead)
                }
            }
            bos.write("\r\n".toByteArray())
        }

        override fun parseNetworkResponse(response: NetworkResponse): Response<NetworkResponse> {
            return Response.success(response, HttpHeaderParser.parseCacheHeaders(response))
        }

        override fun deliverResponse(response: NetworkResponse) {
            listener.onResponse(response)
        }
    }
}