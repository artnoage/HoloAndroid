package com.vaios.holobar; // Replace with your actual package name

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;

import androidx.appcompat.app.AppCompatActivity;

public class SplashVideoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_video);

        CustomVideoView videoView = findViewById(R.id.videoView);

        // Set the path of your video file
        String videoPath = "android.resource://" + getPackageName() + "/" + R.raw.video;
        Uri uri = Uri.parse(videoPath);

        videoView.setVideoURI(uri);
        videoView.start();

        // Add a completion listener to start the next activity when the video ends
        videoView.setOnCompletionListener(mp -> startNextActivity());

        // Add a touch listener to allow skipping the video
        videoView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.performClick();
                startNextActivity();
                return true;
            }
            return false;
        });
    }

    private void startNextActivity() {
        Intent intent = new Intent(SplashVideoActivity.this, WelcomeActivity.class);
        startActivity(intent);
        finish();
    }
}