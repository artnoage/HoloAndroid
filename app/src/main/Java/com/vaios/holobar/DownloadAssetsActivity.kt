package com.vaios.holobar

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadAssetsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_assets)

        downloadAssets()
    }

    private fun downloadAssets() {
        CoroutineScope(Dispatchers.IO).launch {
            // TODO: Implement asset downloading logic here
            // This is where you'd put your code to download the extra assets

            // For now, we'll just simulate a delay
            kotlinx.coroutines.delay(2000)

            // After downloading, move to the WelcomeActivity
            runOnUiThread {
                startActivity(Intent(this@DownloadAssetsActivity, WelcomeActivity::class.java))
                finish()
            }
        }
    }
}
