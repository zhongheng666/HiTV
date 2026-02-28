package com.htlac.hitv.core.player

import android.content.Context
import android.util.Log
import android.view.View
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
import androidx.media3.ui.PlayerView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Media3Player @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PlayerController {

    private val TAG = "HiTV_Debug"

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _errorMessage = MutableStateFlow("")
    override val errorMessage: StateFlow<String> = _errorMessage

    private val _debugInfo = MutableStateFlow("初始化中...")
    override val debugInfo: StateFlow<String> = _debugInfo

    private var currentResolution = "未知"
    private var currentDecoder = "未知"

    private val player: ExoPlayer

    init {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)
            .setDefaultRequestProperties(mapOf("User-Agent" to "Mozilla/5.0"))

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 50000, 500, 500)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .setLoadControl(loadControl)
            .build().apply {
                addAnalyticsListener(object : AnalyticsListener {
                    override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
                        currentResolution = "${videoSize.width} x ${videoSize.height}"
                        Log.d(TAG, "📺 视频分辨率改变: $currentResolution")
                        updateDebugInfo()
                    }
                    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                        currentDecoder = decoderName
                        Log.d(TAG, "⚙️ 视频解码器初始化: $decoderName")
                        if (decoderName.contains("goldfish", ignoreCase = true)) {
                            Log.w(TAG, "⚠️ 警告：检测到模拟器 (goldfish) 解码器！极大概率会导致开播几秒后卡死！")
                        }
                        updateDebugInfo()
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
                            Player.STATE_IDLE, Player.STATE_ENDED -> _playbackState.value = PlaybackState.IDLE
                        }
                        updateDebugInfo()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "💀 播放发生致命错误！错误代码: ${error.errorCodeName}", error)
                        _errorMessage.value = "错误代码: ${error.errorCodeName}\n详细: ${error.cause?.message}"
                        _playbackState.value = PlaybackState.ERROR
                        updateDebugInfo()
                    }
                })
            }
    }

    private fun updateDebugInfo() {
        val stateStr = when (_playbackState.value) {
            PlaybackState.BUFFERING -> "缓冲中"
            PlaybackState.PLAYING -> "流畅播放"
            PlaybackState.IDLE -> "空闲"
            PlaybackState.ERROR -> "严重错误"
        }
        _debugInfo.value = "内核: Media3 (ExoPlayer)\n状态: $stateStr\n分辨率: $currentResolution\n解码器: $currentDecoder"
    }

    override fun getPlayerView(context: Context): View {
        return PlayerView(context).apply {
            player = this@Media3Player.player
            useController = false
        }
    }

    override fun play(url: String) {
        _errorMessage.value = ""
        currentResolution = "解析中..."
        currentDecoder = "分配中..."
        updateDebugInfo()

        Log.d(TAG, "-----------------------------------------")
        Log.d(TAG, "🚀 [Media3] 准备开始极速加载链接: $url")

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

    // 【核心新增】：挂起播放，清空占用的媒体资源，但不销毁引擎本身
    override fun stop() {
        player.stop()
        player.clearMediaItems()
        _playbackState.value = PlaybackState.IDLE
    }

    override fun release() { player.release() }
}