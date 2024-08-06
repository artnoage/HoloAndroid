package com.vaios.holobar 

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity

class SplashVideoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_video)

        val videoView: CustomVideoView = findViewById(R.id.videoView)

        // Set the path of your video file
        val videoPath = "android.resource://$packageName/${R.raw.video}"
        val uri = Uri.parse(videoPath)

        videoView.setVideoURI(uri)
        videoView.start()

        // Add a completion listener to start the next activity when the video ends
        videoView.setOnCompletionListener { startNextActivity() }

        // Add a touch listener to allow skipping the video
        videoView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                v.performClick()
                startNextActivity()
                true
            } else {
                false
            }
        }
    }

    private fun startNextActivity() {
        val intent = Intent(this@SplashVideoActivity, WelcomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}