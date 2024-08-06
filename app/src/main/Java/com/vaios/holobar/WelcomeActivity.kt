package com.vaios.holobar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val nameEditText: EditText = findViewById(R.id.nameEditText)
        val submitButton: Button = findViewById(R.id.submitButton)

        submitButton.setOnClickListener {
            val name = nameEditText.text.toString()
            if (name.isNotEmpty()) {
                val intent = Intent(this@WelcomeActivity, MainActivity::class.java)
                intent.putExtra("player_name", name)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this@WelcomeActivity, "Please enter your name", Toast.LENGTH_SHORT).show()
            }
        }
    }
}