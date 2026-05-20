package com.myra.assistant.features

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build

class MusicController(private val context: Context) {

    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaController: MediaController? = null

    init {
        try {
            mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            mediaSessionManager?.addOnActiveSessionsChangedListener({
                updateMediaController()
            }, ComponentName(context, ""))
            updateMediaController()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateMediaController() {
        try {
            val controllers = mediaSessionManager?.getActiveSessions(ComponentName(context, ""))
            mediaController = controllers?.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playPause() {
        mediaController?.transportControls?.playPause()
    }

    fun play() {
        mediaController?.transportControls?.play()
    }

    fun pause() {
        mediaController?.transportControls?.pause()
    }

    fun stop() {
        mediaController?.transportControls?.stop()
    }

    fun nextTrack() {
        mediaController?.transportControls?.skipToNext()
    }

    fun previousTrack() {
        mediaController?.transportControls?.skipToPrevious()
    }

    fun setVolume(level: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val adjustedLevel = (level * max / 100).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, adjustedLevel, 0)
    }

    fun adjustVolume(delta: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVolume = (current + delta).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    }

    fun getCurrentVolume(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return (current * 100 / max)
    }

    fun isPlaying(): Boolean {
        return mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING
    }
}