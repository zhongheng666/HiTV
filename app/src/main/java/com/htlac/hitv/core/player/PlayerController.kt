package com.htlac.hitv.core.player

import android.view.SurfaceView
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackState {
    IDLE, BUFFERING, PLAYING, ERROR
}

interface PlayerController {
    val playbackState: StateFlow<PlaybackState>
    val errorMessage: StateFlow<String>
    val debugInfo: StateFlow<String>

    fun play(url: String)
    fun pause()
    fun resume()
    fun stop()
    fun release()

    // 【核心架构突变：不再索要 View，而是接受唯一的永生画布】
    fun setSurface(surfaceView: SurfaceView?)
}