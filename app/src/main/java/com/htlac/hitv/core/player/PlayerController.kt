package com.htlac.hitv.core.player

import android.content.Context
import android.view.View
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

    // 【核心新增：让引擎停止但不销毁，供热切时使用】
    fun stop()

    fun release()
    fun getPlayerView(context: Context): View
}