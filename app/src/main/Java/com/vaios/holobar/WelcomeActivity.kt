package com.vaios.holobar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import java.io.IOException

class WelcomeActivity : AppCompatActivity() {

    private lateinit var apiKeyEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var apiKeyLinkTextView: TextView
    private lateinit var proceedButton: Button
    private lateinit var apiCall: ApiCall

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        apiKeyEditText = findViewById(R.id.apiKeyEditText)
        statusTextView = findViewById(R.id.statusTextView)
        apiKeyLinkTextView = findViewById(R.id.apiKeyLinkTextView)
        proceedButton = findViewById(R.id.proceedButton)

        proceedButton.setOnClickListener {
            validateApiKeyAndProceed()
        }

        apiKeyLinkTextView.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev/gemini-api/docs/api-key"))
            startActivity(intent)
        }

        proceedButton.text = getString(R.string.proceed)

        // Load saved API key, if any
        val savedApiKey = loadApiKey()
        if (savedApiKey.isNotBlank()) {
            apiKeyEditText.setText(savedApiKey)
        }

        // Initialize ApiCall with an empty string, we'll set the actual key when validating
        apiCall = ApiCall(this, "")
    }

    private fun validateApiKeyAndProceed() {
        lifecycleScope.launch(Dispatchers.Main) {
            statusTextView.text = getString(R.string.validating_api_key)
            proceedButton.isEnabled = false

            val inputApiKey = apiKeyEditText.text.toString().trim()
            val savedApiKey = loadApiKey()
            val assetApiKey = loadApiKeyFromAssets()

            val apiKeyToTest = when {
                inputApiKey.isNotBlank() -> inputApiKey
                assetApiKey.isNotBlank() -> assetApiKey
                savedApiKey.isNotBlank() -> savedApiKey
                else -> ""
            }

            if (apiKeyToTest.isEmpty()) {
                statusTextView.text = getString(R.string.no_key_exists)
                proceedButton.isEnabled = true
                return@launch
            }

            apiCall.checkApiKey(apiKeyToTest, object : ApiCall.ApiKeyValidationCallback {
                override fun onSuccess(status: String, message: String) {
                    if (status == "valid") {
                        if (inputApiKey.isNotBlank() && inputApiKey != savedApiKey) {
                            saveApiKey(inputApiKey)
                        }
                        statusTextView.text = getString(R.string.api_key_valid)
                        val intent = Intent(this@WelcomeActivity, MainActivity::class.java)
                        intent.putExtra("API_KEY", apiKeyToTest)
                        startActivity(intent)
                        finish()
                    } else {
                        statusTextView.text = getString(R.string.invalid_api_key)
                        proceedButton.isEnabled = true
                    }
                }

                override fun onError(error: String) {
                    statusTextView.text = error
                    proceedButton.isEnabled = true
                }
            })
        }
    }

    private fun loadApiKey(): String {
        val sharedPref = getSharedPreferences("ApiKeyPref", Context.MODE_PRIVATE)
        return sharedPref.getString("api_key", "") ?: ""
    }

    private fun loadApiKeyFromAssets(): String {
        return try {
            assets.open("key.txt").bufferedReader().use { it.readText().trim() }
        } catch (e: IOException) {
            Log.d("WelcomeActivity", "No key.txt found in assets or error reading it: ${e.message}")
            ""
        }
    }

    private fun saveApiKey(apiKey: String) {
        val sharedPref = getSharedPreferences("ApiKeyPref", Context.MODE_PRIVATE)
        with (sharedPref.edit()) {
            putString("api_key", apiKey)
            apply()
        }
        Log.d("WelcomeActivity", "API key saved successfully")
    }
}