package com.htlac.hitv.core.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackState {
    IDLE, BUFFERING, PLAYING, ERROR
}

interface PlayerController {
    val player: Player
    val playbackState: StateFlow<PlaybackState>

    // 新增：专门用于对外暴露极其详细的底层错误原因
    val errorMessage: StateFlow<String>

    fun play(url: String)
    fun pause()
    fun resume()
    fun release()
}