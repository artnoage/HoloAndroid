package com.vaios.holobar
import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.*

class AudioPlaybackManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val songs = listOf(R.raw.song1, R.raw.song2, R.raw.song3, R.raw.song4)
    private var currentSongIndex = 0
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    init {
        startPlayback()
    }

    private fun startPlayback() {
        coroutineScope.launch {
            while (true) {
                playSong(songs[currentSongIndex])
                delay(100) // Small delay to prevent rapid song changes
            }
        }
    }

    private suspend fun playSong(songResId: Int) {
        withContext(Dispatchers.IO) {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, songResId).apply {
                setVolume(0.1f, 0.1f)
                isLooping = false
                start()
            }
            mediaPlayer?.setOnCompletionListener {
                coroutineScope.launch {
                    nextSong()
                }
            }
        }

        // Wait for the song to complete
        mediaPlayer?.let {
            while (it.isPlaying) {
                delay(500)
            }
        }
    }

    fun nextSong() {
        currentSongIndex = (currentSongIndex + 1) % songs.size
        mediaPlayer?.stop()
    }

    fun release() {
        coroutineScope.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}