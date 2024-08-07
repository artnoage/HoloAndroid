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

        proceedButton.setText(R.string.proceed)
    }

    private fun validateApiKeyAndProceed() {
        lifecycleScope.launch(Dispatchers.Main) {
            statusTextView.setText(R.string.validating_api_key)
            proceedButton.isEnabled = false

            val inputApiKey = apiKeyEditText.text.toString().trim()
            val fileApiKey = loadApiKeyFromFile()

            val apiKeyToTest = when {
                inputApiKey.isNotBlank() -> inputApiKey
                fileApiKey.isNotBlank() -> fileApiKey
                else -> ""
            }

            if (apiKeyToTest.isEmpty()) {
                statusTextView.setText(R.string.no_key_exists)
                proceedButton.isEnabled = true
                return@launch
            }

            val isValid = withContext(Dispatchers.IO) {
                testApiKey(apiKeyToTest)
            }

            if (isValid) {
                if (inputApiKey.isNotBlank() && inputApiKey != fileApiKey) {
                    saveApiKeyToFile(inputApiKey)
                }
                statusTextView.setText(R.string.response_received)
                val intent = Intent(this@WelcomeActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                statusTextView.setText(R.string.invalid_api_key)
                proceedButton.isEnabled = true
            }
        }
    }

    private fun loadApiKeyFromFile(): String {
        return try {
            assets.open("gemini_api_key.txt").bufferedReader().use { it.readText().trim() }
        } catch (e: IOException) {
            ""
        }
    }

    private fun saveApiKeyToFile(apiKey: String) {
        try {
            openFileOutput("gemini_api_key.txt", Context.MODE_PRIVATE).use {
                it.write(apiKey.toByteArray())
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private suspend fun testApiKey(apiKey: String): Boolean {
        return withContext(Dispatchers.IO) {
            var isValid = false
            val testText = getString(R.string.test_message)
            val latch = CountDownLatch(1)

            val processText = ProcessText(this@WelcomeActivity, apiKey)
            processText.processText(testText, 0, object : ProcessText.ProcessTextCallback {
                override fun onPieceReady(text: String, audioData: FloatArray, isLastPiece: Boolean) {
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