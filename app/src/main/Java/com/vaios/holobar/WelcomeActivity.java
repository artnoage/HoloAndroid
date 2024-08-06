package com.vaios.holobar;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        EditText nameEditText = findViewById(R.id.nameEditText);
        Button submitButton = findViewById(R.id.submitButton);

        submitButton.setOnClickListener(v -> {
            String name = nameEditText.getText().toString();
            if (!name.isEmpty()) {
                Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
                intent.putExtra("player_name", name);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(WelcomeActivity.this, "Please enter your name", Toast.LENGTH_SHORT).show();
            }
        });
    }
}