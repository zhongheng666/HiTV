package com.htlac.hitv.core.player

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpvPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PlayerController, MPVLib.EventObserver {

    private val TAG = "HiTV_Debug"

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _errorMessage = MutableStateFlow("")
    override val errorMessage: StateFlow<String> = _errorMessage

    private val _debugInfo = MutableStateFlow("MPV C++ 内核初始化中...")
    override val debugInfo: StateFlow<String> = _debugInfo

    private var isMpvInitialized = false

    private var vCodec = "未知"
    private var aCodec = "未知"
    private var hwDec = "未知"
    private var currentVo = "未知"

    init {
        try {
            MPVLib.create(context)
            MPVLib.init()

            // 【核心配置：针对 302 电视流的完美适配】
            // 1. 强制使用 mediacodec_embed 这个唯一不会导致 Amlogic 崩溃的渲染器
            MPVLib.setOptionString("vo", "mediacodec_embed")
            MPVLib.setOptionString("hwdec", "mediacodec")
            MPVLib.setOptionString("hwdec-codecs", "all")

            // 2. 针对 302 流丢失 SPS 关键帧的问题，必须开启缓存！
            MPVLib.setOptionString("cache", "yes")
            MPVLib.setOptionString("demuxer-max-bytes", "15M") // 存 15M 在内存里
            MPVLib.setOptionString("demuxer-max-back-bytes", "5M")

            MPVLib.addObserver(this)

            MPVLib.observeProperty("video-format", 1)
            MPVLib.observeProperty("audio-codec-name", 1)
            MPVLib.observeProperty("hwdec-current", 1)
            MPVLib.observeProperty("current-vo", 1)

            isMpvInitialized = true
            Log.d(TAG, "✅ [真实MPV内核] 底层 C++ 引擎就绪！")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [真实MPV内核] 初始化失败！", e)
            _errorMessage.value = "MPV C++ 库加载失败"
        }
    }

    private fun updateDebugInfo() {
        val stateStr = when (_playbackState.value) {
            PlaybackState.BUFFERING -> "缓冲中"
            PlaybackState.PLAYING -> "暴力输出中"
            PlaybackState.IDLE -> "空闲"
            PlaybackState.ERROR -> "严重错误"
        }
        _debugInfo.value = """
            内核: 真实 MPV
            状态: $stateStr
            渲染: $currentVo
            硬解: $hwDec
            视轨: $vCodec
            音轨: $aCodec
        """.trimIndent()
    }

    override fun setSurface(surfaceView: SurfaceView?) {
        if (!isMpvInitialized) return
        if (surfaceView != null && surfaceView.holder.surface.isValid) {
            Log.d(TAG, "📺 [真实MPV] 绑定永生画布")
            MPVLib.attachSurface(surfaceView.holder.surface)
        } else {
            Log.d(TAG, "📺 [真实MPV] 解绑画布")
            MPVLib.detachSurface()
        }
    }

    override fun play(url: String) {
        if (!isMpvInitialized) return

        _errorMessage.value = ""
        vCodec = "探测中..."
        aCodec = "探测中..."
        hwDec = "探测中..."
        updateDebugInfo()
        _playbackState.value = PlaybackState.BUFFERING

        Log.e(TAG, "=========================================")
        Log.e(TAG, "🚀 [真实MPV] 暴力加载: $url")
        MPVLib.command(arrayOf("stop"))
        MPVLib.command(arrayOf("loadfile", url))
    }

    override fun pause() { if (isMpvInitialized) MPVLib.setPropertyBoolean("pause", true) }
    override fun resume() { if (isMpvInitialized) MPVLib.setPropertyBoolean("pause", false) }

    override fun stop() {
        Log.d(TAG, "⏹ [真实MPV内核] 挂起播放")
        if (isMpvInitialized) MPVLib.command(arrayOf("stop"))
        _playbackState.value = PlaybackState.IDLE
    }

    override fun release() { stop() }

    override fun eventProperty(property: String, value: String) {
        when (property) {
            "video-format" -> { vCodec = value; Log.e(TAG, "🎥 [MPV 探针] 视频编码: $value") }
            "audio-codec-name" -> { aCodec = value; Log.e(TAG, "🎵 [MPV 探针] 音频编码: $value") }
            "hwdec-current" -> {
                hwDec = value
                Log.e(TAG, "⚙️ [MPV 探针] 硬件解码状态: $value")
            }
            "current-vo" -> { currentVo = value; Log.e(TAG, "🖥 [MPV 探针] 视频渲染器: $value") }
        }
        updateDebugInfo()
    }

    override fun eventProperty(property: String, value: Boolean) {}
    override fun eventProperty(property: String, value: Long) {}
    override fun eventProperty(property: String, value: Double) {}
    override fun eventProperty(property: String) {}

    override fun event(eventId: Int) {
        when (eventId) {
            7 -> {
                Log.d(TAG, "⏳ [真实MPV] 开始读取文件/流...")
                _playbackState.value = PlaybackState.BUFFERING
            }
            8 -> {
                Log.d(TAG, "▶️ [真实MPV] 流媒体加载完毕！")
                _playbackState.value = PlaybackState.PLAYING
            }
            9 -> {
                Log.e(TAG, "💀 [真实MPV] 流媒体结束或断流！")
                _playbackState.value = PlaybackState.IDLE
            }
        }
        updateDebugInfo()
    }
}