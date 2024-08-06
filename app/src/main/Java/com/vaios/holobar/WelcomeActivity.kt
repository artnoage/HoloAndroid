package com.vaios.holobar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val nameEditText: EditText = findViewById(R.id.nameEditText)
        val submitButton: Button = findViewById(R.id.submitButton)

        submitButton.setOnClickListener {
            val name = nameEditText.text.toString()
            if (name.isNotEmpty()) {
                validateApiKeyAndProceed(name)
            } else {
                Toast.makeText(this@WelcomeActivity, "Please enter your name", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateApiKeyAndProceed(name: String) {
        val apiKey = loadApiKey()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "API key not found", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val textToAudio = TextToAudio(this@WelcomeActivity)
                val isValid = testApiKey(textToAudio)
                withContext(Dispatchers.Main) {
                    if (isValid) {
                        val intent = Intent(this@WelcomeActivity, MainActivity::class.java)
                        intent.putExtra("player_name", name)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@WelcomeActivity, "Invalid API key. Please check your configuration.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@WelcomeActivity, "Error validating API key: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadApiKey(): String {
        return try {
            assets.open("gemini_api_key.txt").bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun testApiKey(textToAudio: TextToAudio): Boolean {
        return withContext(Dispatchers.IO) {
            var isValid = false
            val testText = "Hello, this is a test."
            val latch = CountDownLatch(1)

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