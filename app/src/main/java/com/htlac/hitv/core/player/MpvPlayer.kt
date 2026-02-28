package com.htlac.hitv.core.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpvPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) : PlayerController {

    private val TAG = "HiTV_Debug"

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _errorMessage = MutableStateFlow("")
    override val errorMessage: StateFlow<String> = _errorMessage

    private val _debugInfo = MutableStateFlow("备用底层内核初始化...")
    override val debugInfo: StateFlow<String> = _debugInfo

    private var mediaPlayer: MediaPlayer? = null
    private var surfaceView: SurfaceView? = null

    override fun getPlayerView(context: Context): View {
        surfaceView = SurfaceView(context).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    try { mediaPlayer?.setDisplay(holder) } catch (e: Exception) { Log.e(TAG, "MPV: 设置 Surface 失败", e) }
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    try { mediaPlayer?.setDisplay(null) } catch (e: Exception) {}
                }
            })
        }
        return surfaceView!!
    }

    override fun play(url: String) {
        Log.d(TAG, "-----------------------------------------")
        Log.d(TAG, "🚀 [MPV备用内核] 准备拉取链接: $url")

        _errorMessage.value = ""
        _debugInfo.value = "内核: 原生硬解 (MPV占位)\n状态: 缓冲拉流中..."
        _playbackState.value = PlaybackState.BUFFERING

        // 完全销毁重建 MediaPlayer，保证状态干净
        destroyPlayer()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setOnPreparedListener {
                    Log.d(TAG, "▶️ [MPV备用内核] 准备就绪，开始硬件解码播放！")
                    _playbackState.value = PlaybackState.PLAYING
                    _debugInfo.value = "内核: 原生硬解 (MPV占位)\n状态: 强制硬件解码播放中\n(注:如无画面说明该源不支持硬解)"
                    start()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "💀 [MPV备用内核] 致命错误！Error: $what, $extra")
                    _playbackState.value = PlaybackState.ERROR

                    if (extra == -2147483648) {
                        _errorMessage.value = "备用硬解芯片拒绝播放此格式 (错误码: -2147483648)。\n请切回 Media3 或更换直播源。"
                    } else {
                        _errorMessage.value = "备用内核抛出异常 (Error: $what, Extra: $extra)"
                    }
                    _debugInfo.value = "内核: 原生硬解\n状态: 崩溃断流"
                    true // 返回 true 阻止系统自动报错
                }

                setDataSource(url)
                prepareAsync()
            }
            // 如果表面已经准备好了，直接绑定
            surfaceView?.holder?.let { mediaPlayer?.setDisplay(it) }

        } catch (e: Exception) {
            Log.e(TAG, "💀 [MPV备用内核] 初始化崩溃！", e)
            _playbackState.value = PlaybackState.ERROR
            _errorMessage.value = "系统拉流失败: ${e.message}"
        }
    }

    override fun pause() { try { mediaPlayer?.pause() } catch (e: Exception) {} }
    override fun resume() { try { mediaPlayer?.start() } catch (e: Exception) {} }

    override fun stop() {
        Log.d(TAG, "⏹ [MPV备用内核] 挂起播放")
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset() // 必须 reset 才能再次使用
        } catch (e: Exception) {}
        _playbackState.value = PlaybackState.IDLE
    }

    override fun release() { destroyPlayer() }

    private fun destroyPlayer() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "MPV: 销毁异常", e)
        } finally {
            mediaPlayer = null
            _playbackState.value = PlaybackState.IDLE
        }
    }
}