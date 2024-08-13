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

class AssetManager(context: Context) {
    private val assetPackManager: AssetPackManager = AssetPackManagerFactory.getInstance(context)
    private val assetPackName = "ml_models"
    private val requiredFiles = listOf("vits_model.onnx", "phonemizer_model.onnx")

    companion object {
        private const val TAG = "AssetManager"
    }

    fun areAssetsPresent(): Boolean {
        val assetPackPath = getAssetPackPath()
        if (assetPackPath == null) {
            Log.d(TAG, "Asset pack not found")
            return false
        }

        Log.d(TAG, "Checking for required files in: $assetPackPath")
        return requiredFiles.all { fileName ->
            val file = File(assetPackPath, fileName)
            val exists = file.exists()
            Log.d(TAG, "File $fileName exists: $exists")
            exists
        }
    }

    fun getAssetPackPath(): String? {
        val location = assetPackManager.getPackLocation(assetPackName)
        val path = location?.assetsPath()
        Log.d(TAG, "Asset pack path: $path")
        return path
    }

    fun getAssetPath(fileName: String): String? {
        val assetPackPath = getAssetPackPath()
        return if (assetPackPath != null) {
            val filePath = "$assetPackPath/$fileName"
            Log.d(TAG, "Asset file path for $fileName: $filePath")
            filePath
        } else {
            Log.d(TAG, "Unable to get asset path for $fileName: Asset pack not found")
            null
        }
    }

    fun listAssetFiles(): List<String> {
        val assetPackPath = getAssetPackPath()
        return if (assetPackPath != null) {
            File(assetPackPath).walk().filter { it.isFile }.map { it.absolutePath }.toList().also {
                Log.d(TAG, "Asset files found: ${it.joinToString("\n")}")
            }
        } else {
            Log.d(TAG, "Unable to list asset files: Asset pack not found")
            emptyList()
        }
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