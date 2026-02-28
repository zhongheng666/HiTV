package com.htlac.hitv.core.player

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
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

    private val TAG = "HiTV_Debug"

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _errorMessage = MutableStateFlow("")
    override val errorMessage: StateFlow<String> = _errorMessage

    private val _debugInfo = MutableStateFlow("初始化中...")
    override val debugInfo: StateFlow<String> = _debugInfo

    private var currentResolution = "未知"
    private var currentDecoder = "未知"
    private var videoCodecInfo = "未知"
    private var audioCodecInfo = "未知"

    private val player: ExoPlayer

    // 【核心新增】：保存当前播放地址，用于 302 失败后自愈重试
    private var currentUrl = ""
    private var isRetryWithHls = false

    init {
        // 【核心提速】：允许跨协议重定向，并缩短超时，加速失败判断
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(3000)
            .setReadTimeoutMs(3000)
            .setDefaultRequestProperties(mapOf("User-Agent" to "ExoPlayer/Media3"))

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 15000, 500, 500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .setLoadControl(loadControl)
            .build().apply {
                addAnalyticsListener(object : AnalyticsListener {
                    override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
                        currentResolution = "${videoSize.width} x ${videoSize.height}"
                        updateDebugInfo()
                    }
                    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                        currentDecoder = decoderName
                        updateDebugInfo()
                    }
                    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
                        videoCodecInfo = "${format.sampleMimeType} (${format.codecs ?: "N/A"})"
                        Log.e(TAG, "🎥 [Media3 探针] 视频: $videoCodecInfo, 分辨率=${format.width}x${format.height}")
                        updateDebugInfo()
                    }
                    override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
                        audioCodecInfo = "${format.sampleMimeType} (${format.codecs ?: "N/A"})"
                        Log.e(TAG, "🎵 [Media3 探针] 音频: $audioCodecInfo, 采样率=${format.sampleRate}")
                        updateDebugInfo()
                    }
                })

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> _playbackState.value = PlaybackState.BUFFERING
                            Player.STATE_READY -> _playbackState.value = PlaybackState.PLAYING
                            Player.STATE_IDLE, Player.STATE_ENDED -> _playbackState.value = PlaybackState.IDLE
                        }
                        updateDebugInfo()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        // 【终极 302 自愈逻辑】：如果嗅探失败 (3001错误) 或者抛出 UnrecognizedInputFormatException
                        if ((error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                                    error.cause?.toString()?.contains("UnrecognizedInputFormatException") == true) &&
                            !isRetryWithHls) {

                            Log.e(TAG, "⚠️ 探测到 302 重定向嗅探失败！触发自愈机制：强行按 M3U8 格式重试！")
                            isRetryWithHls = true
                            internalPlay(currentUrl, true)
                            return
                        }

                        Log.e(TAG, "💀 [Media3] 播放发生致命错误！", error)
                        _errorMessage.value = "错误代码: ${error.errorCodeName}\n视频: $videoCodecInfo\n音频: $audioCodecInfo"
                        _playbackState.value = PlaybackState.ERROR
                        updateDebugInfo()
                    }
                })
            }
    }

    private fun updateDebugInfo() {
        val stateStr = when (_playbackState.value) {
            PlaybackState.BUFFERING -> "缓冲中"
            PlaybackState.PLAYING -> "流畅"
            PlaybackState.IDLE -> "空闲"
            PlaybackState.ERROR -> "错误"
        }
        _debugInfo.value = """
            内核: Media3 (ExoPlayer)
            状态: $stateStr
            画质: $currentResolution
            视轨: $videoCodecInfo
            音轨: $audioCodecInfo
            硬件: $currentDecoder
        """.trimIndent()
    }

    override fun setSurface(surfaceView: SurfaceView?) {
        if (surfaceView != null) player.setVideoSurfaceView(surfaceView)
        else player.clearVideoSurface()
    }

    override fun play(url: String) {
        currentUrl = url
        isRetryWithHls = false
        internalPlay(url, false)
    }

    private fun internalPlay(url: String, forceHls: Boolean) {
        _errorMessage.value = ""
        currentResolution = "解析中..."
        currentDecoder = "分配中..."
        videoCodecInfo = "探测中..."
        audioCodecInfo = "探测中..."
        updateDebugInfo()

        player.stop()
        player.clearMediaItems()

        Log.e(TAG, "=========================================")
        Log.e(TAG, "🚀 [Media3] 开始加载: $url (强制HLS: $forceHls)")

        val mediaItemBuilder = MediaItem.Builder().setUri(url)
        // 如果触发了自愈机制，或者地址明确包含 m3u8，强行指定 MimeType 规避嗅探
        if (forceHls || url.contains(".m3u8", ignoreCase = true)) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        player.playWhenReady = true
    }

    override fun pause() { player.pause() }
    override fun resume() { player.play() }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
        _playbackState.value = PlaybackState.IDLE
    }

    override fun release() { player.release() }
}