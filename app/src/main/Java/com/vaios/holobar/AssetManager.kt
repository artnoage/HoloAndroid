package com.vaios.holobar

import android.content.Context
import android.util.Log
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File

class AssetManager(private val context: Context) {
    private val assetPackManager: AssetPackManager = AssetPackManagerFactory.getInstance(context)
    private val assetPackName = "ml_models"
    private val requiredFiles = listOf("vits_model.onnx", "phonemizer_model.onnx")
    private val tempAssetsFolder = "temp_assets"

    companion object {
        private const val TAG = "AssetManager"
    }

    fun areAssetsPresent(): Boolean {
        // First, check in the app's assets folder
        if (checkAssetsInAppFolder()) {
            Log.d(TAG, "Assets found in app's assets folder")
            return true
        }

        // If not found, check in the normal asset pack location
        val assetPackPath = getAssetPackPath()
        if (assetPackPath != null && checkFilesExist(assetPackPath)) {
            Log.d(TAG, "Assets found in normal location")
            return true
        }

        Log.d(TAG, "Assets not found in any location")
        return false
    }

    private fun checkAssetsInAppFolder(): Boolean {
        return requiredFiles.all { fileName ->
            try {
                context.assets.open("$tempAssetsFolder/$fileName").use { it.available() > 0 }
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun checkFilesExist(folderPath: String): Boolean {
        return requiredFiles.all { fileName ->
            val file = File(folderPath, fileName)
            val exists = file.exists()
            Log.d(TAG, "File $fileName exists in $folderPath: $exists")
            exists
        }
    }

    private fun getAssetPackPath(): String? {
        val location = assetPackManager.getPackLocation(assetPackName)
        val path = location?.assetsPath()
        Log.d(TAG, "Asset pack path: $path")
        return path
    }

    fun getAssetPath(fileName: String): String? {
        // First, check in the app's assets folder
        try {
            context.assets.open("$tempAssetsFolder/$fileName").use {
                Log.d(TAG, "Asset file found in app's assets folder: $tempAssetsFolder/$fileName")
                return "asset:///$tempAssetsFolder/$fileName"
            }
        } catch (e: Exception) {
            // File not found in assets, continue to next check
        }

        // If not found, check in the normal asset pack location
        val assetPackPath = getAssetPackPath()
        if (assetPackPath != null) {
            val filePath = "$assetPackPath/$fileName"
            if (File(filePath).exists()) {
                Log.d(TAG, "Asset file found in normal location: $filePath")
                return filePath
            }
        }

        Log.d(TAG, "Unable to find asset file: $fileName")
        return null
    }

    suspend fun downloadAssetPack(onProgress: (Float) -> Unit): Boolean = suspendCancellableCoroutine { continuation ->
        val listener = object : AssetPackStateUpdateListener {
            override fun onStateUpdate(state: AssetPackState) {
                when (state.status()) {
                    AssetPackStatus.PENDING -> {
                        Log.d(TAG, "Asset pack download pending")
                    }
                    AssetPackStatus.DOWNLOADING -> {
                        val progress = state.bytesDownloaded().toFloat() / state.totalBytesToDownload()
                        Log.d(TAG, "Asset pack downloading: ${progress * 100}%")
                        onProgress(progress)
                    }
                    AssetPackStatus.COMPLETED -> {
                        Log.d(TAG, "Asset pack download completed")
                        assetPackManager.unregisterListener(this)
                        continuation.resume(true)
                    }
                    AssetPackStatus.FAILED -> {
                        Log.e(TAG, "Asset pack download failed")
                        assetPackManager.unregisterListener(this)
                        continuation.resumeWithException(Exception("Asset pack download failed"))
                    }
                    else -> {
                        Log.d(TAG, "Asset pack status: ${state.status()}")
                    }
                }
            }
        }

        assetPackManager.registerListener(listener)
        continuation.invokeOnCancellation { assetPackManager.unregisterListener(listener) }

        Log.d(TAG, "Starting asset pack download")
        assetPackManager.fetch(listOf(assetPackName))
    }
}