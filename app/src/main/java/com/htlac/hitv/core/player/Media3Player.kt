package com.htlac.hitv.core.player

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
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
    private var realUrl = "追踪中..."

    // 【核心改造 1】：公开 exoPlayer 实例，让外部能够获取它并注入到 PlayerView 中，解决 0x0 黑屏问题！
    val exoPlayer: ExoPlayer
    private val httpDataSourceFactory: DefaultHttpDataSource.Factory

    private var currentOriginalUrl = ""
    private var currentFallbackLevel = 0

    init {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(5000)
            .setReadTimeoutMs(5000)
            .setUserAgent(userAgent)

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 64 * 1024))
            .setBufferDurationsMs(3000, 20000, 1500, 2000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        // 终生只初始化一次，坚决不 release 防止僵尸死锁
        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build().apply {
                addAnalyticsListener(EventLogger(TAG))

                addAnalyticsListener(object : AnalyticsListener {
                    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
                        if (mediaLoadData.dataType == androidx.media3.common.C.DATA_TYPE_MANIFEST) {
                            realUrl = loadEventInfo.uri.toString()
                            Log.d(TAG, "🔗 [Media3] 302 真实重定向地址: $realUrl")
                            updateDebugInfo()
                        }
                    }
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
                        Log.e(TAG, "💀 [Media3] 遇到致命错误: ${error.errorCodeName}", error)
                        if (currentFallbackLevel < 2) {
                            currentFallbackLevel++
                            Log.e(TAG, "🔄 触发自动 Fallback 降级容错 -> 进入策略 $currentFallbackLevel")
                            internalPlay(currentOriginalUrl, currentFallbackLevel)
                        } else {
                            Log.e(TAG, "❌ 所有策略耗尽，彻底放弃。")
                            _errorMessage.value = "错误: ${error.errorCodeName}\n已尝试多级容错修复失败。"
                            _playbackState.value = PlaybackState.ERROR
                            updateDebugInfo()
                        }
                    }
                })
            }
    }

    private fun updateDebugInfo() {
        val stateStr = when (_playbackState.value) {
            PlaybackState.BUFFERING -> "缓冲中..."
            PlaybackState.PLAYING -> "流畅输出"
            PlaybackState.IDLE -> "空闲"
            PlaybackState.ERROR -> "播放失败"
        }
        val dropped = exoPlayer.videoDecoderCounters?.droppedBufferCount ?: 0
        _debugInfo.value = """
            内核: ExoPlayer (策略 $currentFallbackLevel)
            状态: $stateStr
            画质: $currentResolution
            视轨: $videoCodecInfo
            解码: $currentDecoder
            丢帧: $dropped 帧
            地址: ${if (realUrl.length > 35) realUrl.take(35) + "..." else realUrl}
        """.trimIndent()
    }

    // 交给 Compose 的 PlayerView 去管理画布，不再手动设置
    override fun setSurface(surfaceView: SurfaceView?) {}

    override fun play(url: String) {
        currentOriginalUrl = url
        currentFallbackLevel = 0
        realUrl = "抓取 302 中..."
        internalPlay(url, 0)
    }

    private fun internalPlay(url: String, level: Int) {
        _errorMessage.value = ""
        currentResolution = "解析中..."
        videoCodecInfo = "探测中..."
        updateDebugInfo()

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        Log.e(TAG, "=========================================")

        val mediaSource = when (level) {
            0 -> {
                Log.e(TAG, "🚀 [策略 0] 默认智能嗅探模式: $url")
                DefaultMediaSourceFactory(httpDataSourceFactory).createMediaSource(MediaItem.fromUri(url))
            }
            1 -> {
                Log.e(TAG, "🔧 [策略 1] 针对脏流 HLS: 强制关闭无切片准备，允许非 IDR 关键帧起播")
                val hlsExtractorFactory = DefaultHlsExtractorFactory(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES, true)
                HlsMediaSource.Factory(httpDataSourceFactory)
                    .setExtractorFactory(hlsExtractorFactory)
                    .setAllowChunklessPreparation(false)
                    .createMediaSource(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
            }
            2 -> {
                Log.e(TAG, "🔧 [策略 2] 终极 TS 容错: 强制识别为 MP2T 纯二进制流")
                val extractorsFactory = DefaultExtractorsFactory().setTsExtractorFlags(
                    DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                )
                DefaultMediaSourceFactory(httpDataSourceFactory, extractorsFactory)
                    .createMediaSource(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP2T).build())
            }
            else -> throw IllegalStateException("Unknown fallback level")
        }

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override fun pause() { exoPlayer.pause() }
    override fun resume() { exoPlayer.play() }

    override fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _playbackState.value = PlaybackState.IDLE
    }

    override fun release() {
        Log.d(TAG, "⏹ [Media3] 清空播放队列以释放资源")
        stop()
    }
}