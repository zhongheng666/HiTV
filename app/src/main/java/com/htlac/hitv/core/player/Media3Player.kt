package com.htlac.hitv.core.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Media3Player @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PlayerController {

    // 【核心修改 1：全局统一定义 Debug 标签】
    private val TAG = "HiTV_Debug"

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _errorMessage = MutableStateFlow("")
    override val errorMessage: StateFlow<String> = _errorMessage

    private val _debugInfo = MutableStateFlow("初始化中...")
    val debugInfo: StateFlow<String> = _debugInfo

    private var currentResolution = "未知"
    private var currentDecoder = "未知"
    private var currentBitrate = "未知"

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(10000)
        .setReadTimeoutMs(10000)
        .setDefaultRequestProperties(
            mapOf("User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        )

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(1000, 50000, 500, 500)
        .build()

    private val renderersFactory = DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

    override val player: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
        .setLoadControl(loadControl)
        .build().apply {

            // 【核心修改 2：废弃官方傲娇的 Logger，自己手写核心拦截，确保输出到 HiTV_Debug】
            addAnalyticsListener(object : AnalyticsListener {
                override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
                    currentResolution = "${videoSize.width} x ${videoSize.height}"
                    Log.d(TAG, "📺 视频分辨率改变: $currentResolution")
                    updateDebugInfo()
                }

                override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                    currentDecoder = decoderName
                    Log.d(TAG, "⚙️ 视频解码器初始化: $decoderName")
                    if (decoderName.contains("goldfish")) {
                        Log.w(TAG, "⚠️ 警告：检测到模拟器 (goldfish) 解码器！极大概率会导致开播几秒后卡死！请尽量在真机测试。")
                    }
                    updateDebugInfo()
                }

                override fun onDownstreamFormatChanged(eventTime: AnalyticsListener.EventTime, mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData) {
                    val bitrate = mediaLoadData.trackFormat?.bitrate ?: -1
                    currentBitrate = if (bitrate > 0) "${bitrate / 1000} kbps" else "动态码率"
                    updateDebugInfo()
                }

                override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                    // 模拟器卡死前通常会疯狂丢帧，我们将它打印出来
                    Log.e(TAG, "❌ 严重警告：丢帧！在 ${elapsedMs}ms 内丢了 $droppedFrames 帧！")
                }
            })

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            _playbackState.value = PlaybackState.BUFFERING
                            Log.d(TAG, "⏳ 播放器状态: 缓冲中 (BUFFERING)")
                        }
                        Player.STATE_READY -> {
                            _playbackState.value = PlaybackState.PLAYING
                            Log.d(TAG, "▶️ 播放器状态: 准备就绪，开始播放 (READY)")
                        }
                        Player.STATE_IDLE -> _playbackState.value = PlaybackState.IDLE
                        Player.STATE_ENDED -> _playbackState.value = PlaybackState.IDLE
                    }
                    updateDebugInfo()
                }

                override fun onPlayerError(error: PlaybackException) {
                    val errorCode = error.errorCodeName
                    val deepCause = error.cause?.message ?: "未知网络或解码错误"
                    val detail = "错误代码: $errorCode\n详细原因: $deepCause"
                    Log.e(TAG, "💀 播放发生致命错误！\n$detail", error)
                    _errorMessage.value = detail
                    _playbackState.value = PlaybackState.ERROR
                    updateDebugInfo()
                }
            })
        }

    private fun updateDebugInfo() {
        val stateStr = when (_playbackState.value) {
            PlaybackState.BUFFERING -> "缓冲中"
            PlaybackState.PLAYING -> "流畅播放"
            PlaybackState.IDLE -> "空闲"
            PlaybackState.ERROR -> "严重错误"
        }
        _debugInfo.value = """
            状态: $stateStr
            分辨率: $currentResolution
            解码器: $currentDecoder
            码率估算: $currentBitrate
        """.trimIndent()
    }

    override fun play(url: String) {
        _errorMessage.value = ""
        currentResolution = "解析中..."
        currentDecoder = "分配中..."
        updateDebugInfo()

        Log.d(TAG, "-----------------------------------------")
        Log.d(TAG, "🚀 准备开始极速加载链接: $url")

        val isLikelyHls = url.contains(".m3u8", ignoreCase = true) || !url.substringAfterLast("/").contains(".")
        val mediaItemBuilder = MediaItem.Builder().setUri(url)
        if (isLikelyHls) {
            Log.d(TAG, "🎯 判定为 HLS 流，强制设置为 APPLICATION_M3U8")
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        player.playWhenReady = true
    }

    override fun pause() { player.pause() }
    override fun resume() { player.play() }
    override fun release() { player.release() }
}