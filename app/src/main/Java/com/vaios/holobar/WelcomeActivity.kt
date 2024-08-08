package com.vaios.holobar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException

class WelcomeActivity : AppCompatActivity() {

    private lateinit var apiKeyEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var apiKeyLinkTextView: TextView
    private lateinit var proceedButton: Button
    private lateinit var apiCall: ApiCall

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val TAG = "WelcomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        initializeViews()
        setupListeners()

        // Initialize ApiCall with an empty string, we'll set the actual key when validating
        apiCall = ApiCall(this, "")
    }

    private fun initializeViews() {
        apiKeyEditText = findViewById<EditText>(R.id.apiKeyEditText).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        statusTextView = findViewById(R.id.statusTextView)
        apiKeyLinkTextView = findViewById(R.id.apiKeyLinkTextView)
        proceedButton = findViewById<Button>(R.id.proceedButton).apply {
            isEnabled = true
            text = getString(R.string.proceed)
        }
    }

    private fun setupListeners() {
        apiKeyLinkTextView.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev/gemini-api/docs/api-key"))
            startActivity(intent)
        }

        proceedButton.setOnClickListener {
            proceedButton.isEnabled = false
            validateApiKey()
        }
    }

    private fun validateApiKey() {
        coroutineScope.launch {
            val userInputKey = apiKeyEditText.text.toString().trim()

            if (userInputKey.isNotBlank()) {
                // Prioritize user input
                updateStatus(getString(R.string.validating_key))
                if (validateKeyWithApi(userInputKey)) {
                    saveApiKey(userInputKey)
                    proceedToMainActivity()
                    return@launch
                } else {
                    updateStatus(getString(R.string.invalid_api_key))
                    proceedButton.isEnabled = true
                    return@launch
                }
            }

            // If user input is empty, check other sources
            updateStatus(getString(R.string.checking_for_key))

            // Check for key in assets
            val assetKey = loadApiKeyFromAssets()
            if (assetKey.isNotBlank()) {
                if (validateKeyWithApi(assetKey)) {
                    saveApiKey(assetKey)
                    proceedToMainActivity()
                    return@launch
                }
            }

            // Check for saved key
            val savedKey = loadApiKey()
            if (savedKey.isNotBlank()) {
                if (validateKeyWithApi(savedKey)) {
                    proceedToMainActivity()
                    return@launch
                }
            }

            // No valid key found
            updateStatus(getString(R.string.no_valid_key_found))
            proceedButton.isEnabled = true
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun validateKeyWithApi(key: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                suspendCancellableCoroutine { continuation ->
                    apiCall.checkApiKey(key, object : ApiCall.ApiKeyValidationCallback {
                        override fun onSuccess(status: String, message: String) {
                            if (continuation.isActive) continuation.resume(status == "valid") {
                                // This is the onCancellation lambda
                                // Add any cancellation cleanup code here if needed
                            }
                        }
                        override fun onError(error: String) {
                            if (continuation.isActive) continuation.resume(false) {
                                // This is the onCancellation lambda
                                // Add any cancellation cleanup code here if needed
                            }
                        }
                    })
                }
            } catch (e: Exception) {
                updateStatus("Error validating key: ${e.message}")
                false
            }
        }
    }

    private fun loadApiKeyFromAssets(): String {
        return try {
            assets.open("key.txt").bufferedReader().use { it.readText().trim() }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading API key from assets: ${e.message}")
            ""
        }
    }

    private fun loadApiKey(): String {
        return try {
            File(filesDir, "gemini_api_key.txt").readText().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading API key: ${e.message}")
            ""
        }
    }

    private fun saveApiKey(apiKey: String) {
        try {
            File(filesDir, "gemini_api_key.txt").writeText(apiKey)
        } catch (e: IOException) {
            Log.e(TAG, "Error saving API key: ${e.message}")
        }
    }

    private fun proceedToMainActivity() {
        // Clear the API key from memory
        apiKeyEditText.text.clear()

        // Start MainActivity and finish WelcomeActivity
        val intent = Intent(this@WelcomeActivity, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusTextView.text = message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}