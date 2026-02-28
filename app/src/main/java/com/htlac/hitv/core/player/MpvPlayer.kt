package com.htlac.hitv.core.player

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpvPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PlayerController, MPVLib.EventObserver { // 继承 MPV 事件观察者以获取真实状态

    private val TAG = "HiTV_Debug"

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _errorMessage = MutableStateFlow("")
    override val errorMessage: StateFlow<String> = _errorMessage

    private val _debugInfo = MutableStateFlow("MPV C++ 内核初始化中...")
    override val debugInfo: StateFlow<String> = _debugInfo

    private var surfaceView: SurfaceView? = null
    private var isMpvInitialized = false

    init {
        try {
            // 【核心魔法：唤醒底层的 C++ MPV 引擎】
            MPVLib.create(context)
            MPVLib.init()

            // 核心配置：开启硬件解码 (失败自动降级为软解)
            MPVLib.setOptionString("hwdec", "auto")
            MPVLib.setOptionString("vo", "gpu")
            // 针对直播流的抗延迟优化
            MPVLib.setOptionString("profile", "low-latency")

            // 监听底层事件
            MPVLib.addObserver(this)
            isMpvInitialized = true

            Log.d(TAG, "✅ [真实MPV内核] 底层 C++ 引擎初始化成功！")
            _debugInfo.value = "内核: 真实 MPV (libmpv)\n状态: 引擎就绪"
        } catch (e: Exception) {
            Log.e(TAG, "❌ [真实MPV内核] 初始化失败！", e)
            _errorMessage.value = "MPV C++ 库加载失败，可能是缺少对应架构的 .so 文件"
        }
    }

    override fun getPlayerView(context: Context): View {
        surfaceView = SurfaceView(context).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    if (isMpvInitialized) {
                        Log.d(TAG, "📺 [真实MPV] 画布绑定成功")
                        MPVLib.attachSurface(holder.surface)
                    }
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    if (isMpvInitialized) {
                        Log.d(TAG, "📺 [真实MPV] 画布解绑")
                        MPVLib.detachSurface()
                    }
                }
            })
        }
        return surfaceView!!
    }

    override fun play(url: String) {
        if (!isMpvInitialized) {
            _playbackState.value = PlaybackState.ERROR
            return
        }

        Log.d(TAG, "-----------------------------------------")
        Log.d(TAG, "🚀 [真实MPV内核] 准备暴力拉流: $url")

        _errorMessage.value = ""
        _debugInfo.value = "内核: 真实 MPV (libmpv)\n状态: C++ 引擎正在拉流..."
        _playbackState.value = PlaybackState.BUFFERING

        // 【最硬核的命令：直接向 C++ 层下达拉流指令】
        MPVLib.command(arrayOf("loadfile", url))
    }

    override fun pause() {
        if (isMpvInitialized) MPVLib.setPropertyBoolean("pause", true)
    }

    override fun resume() {
        if (isMpvInitialized) MPVLib.setPropertyBoolean("pause", false)
    }

    override fun stop() {
        Log.d(TAG, "⏹ [真实MPV内核] 挂起播放")
        if (isMpvInitialized) MPVLib.command(arrayOf("stop"))
        _playbackState.value = PlaybackState.IDLE
    }

    override fun release() {
        stop()
        // 通常作为单例，我们可以保留 MPV 的生命周期不彻底 destroy，以便重复使用
    }

    // ==========================================================
    // 实现 MPVLib.EventObserver，捕获底层抛出的极其精确的状态
    // ==========================================================

    override fun eventProperty(property: String, value: Boolean) {}
    override fun eventProperty(property: String, value: Long) {}
    override fun eventProperty(property: String, value: Double) {}
    override fun eventProperty(property: String, value: String) {}
    override fun eventProperty(property: String) {}

    override fun event(eventId: Int) {
        // 根据 MPV 官方文档，将核心事件映射到我们的 UI 状态机
        when (eventId) {
            7 -> { // MPV_EVENT_START_FILE
                Log.d(TAG, "⏳ [真实MPV] 开始读取文件/流...")
                _playbackState.value = PlaybackState.BUFFERING
            }
            8 -> { // MPV_EVENT_FILE_LOADED
                Log.d(TAG, "▶️ [真实MPV] 流媒体加载完毕，暴力出画！")
                _playbackState.value = PlaybackState.PLAYING

                // 尝试获取它到底是用软解还是硬解
                val hwdecActive = try { MPVLib.getPropertyString("hwdec-current") } catch (e: Exception) { "未知" }
                _debugInfo.value = "内核: 真实 MPV (libmpv)\n状态: 极致播放中\n解码模式: $hwdecActive"
            }
            9 -> { // MPV_EVENT_END_FILE
                Log.e(TAG, "💀 [真实MPV] 流媒体意外结束或读取失败！")
                // 注意：由于是直播源，END_FILE 通常意味着断流或者解析失败
                _playbackState.value = PlaybackState.IDLE
            }
        }
    }
}