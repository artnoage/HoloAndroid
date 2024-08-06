package com.vaios.holobar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.CountDownLatch

class WelcomeActivity : AppCompatActivity() {

    private lateinit var apiKeyEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var apiKeyLinkTextView: TextView
    private lateinit var proceedButton: Button

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

        // Check if API key exists in assets and update UI accordingly
        if (apiKeyExistsInAssets()) {
            apiKeyEditText.setText(loadApiKeyFromAssets())
            proceedButton.setText(R.string.proceed)
        } else {
            proceedButton.setText(R.string.submit)
        }
    }

    private fun validateApiKeyAndProceed() {
        lifecycleScope.launch(Dispatchers.Main) {
            statusTextView.setText(R.string.validating_api_key)
            proceedButton.isEnabled = false

            val newApiKey = apiKeyEditText.text.toString().trim()
            val existingApiKey = loadApiKeyFromAssets()

            val apiKeyToTest = when {
                newApiKey.isNotBlank() -> newApiKey
                existingApiKey.isNotBlank() -> existingApiKey
                else -> ""
            }

            if (apiKeyToTest.isEmpty()) {
                statusTextView.setText(R.string.no_key_provided)
                proceedButton.isEnabled = true
                return@launch
            }

            val isValid = withContext(Dispatchers.IO) {
                testApiKey(apiKeyToTest)
            }

            if (isValid) {
                statusTextView.setText(R.string.response_received)
                // Proceed to MainActivity
                val intent = Intent(this@WelcomeActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                statusTextView.setText(R.string.invalid_api_key)
                proceedButton.isEnabled = true
            }
        }
    }

    private fun apiKeyExistsInAssets(): Boolean {
        return try {
            assets.open("gemini_api_key.txt").use { it.available() > 0 }
        } catch (e: IOException) {
            false
        }
    }

    private fun loadApiKeyFromAssets(): String {
        return try {
            assets.open("gemini_api_key.txt").bufferedReader().use { it.readText().trim() }
        } catch (e: IOException) {
            ""
        }
    }

    private suspend fun testApiKey(apiKey: String): Boolean {
        return withContext(Dispatchers.IO) {
            var isValid = false
            val testText = getString(R.string.test_message)
            val latch = CountDownLatch(1)

            val textToAudio = TextToAudio(this@WelcomeActivity, apiKey)
            textToAudio.processText(testText, 0, object : TextToAudio.TextToAudioCallback {
                override fun onSuccess(narration: String, status: String, audioData: FloatArray) {
                    isValid = true
                    latch.countDown()
                }

                override fun onError(error: String) {
                    isValid = false
                    latch.countDown()
                }
            })

            latch.await()
            isValid
        }
    }
}