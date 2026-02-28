package com.htlac.hitv.core.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Media3Player @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PlayerController {

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _errorMessage = MutableStateFlow("")
    override val errorMessage: StateFlow<String> = _errorMessage

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(8000)
        .setReadTimeoutMs(8000)
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

            // 修复告警：使用带 Tag 字符串的最新版构造函数
            addAnalyticsListener(EventLogger("HiTV_Logger"))

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> _playbackState.value = PlaybackState.BUFFERING
                        Player.STATE_READY -> _playbackState.value = PlaybackState.PLAYING
                        Player.STATE_IDLE, Player.STATE_ENDED -> _playbackState.value = PlaybackState.IDLE
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val errorCode = error.errorCodeName
                    val deepCause = error.cause?.message ?: "未知网络或解码错误"
                    val detail = "错误代码: $errorCode\n详细原因: $deepCause"

                    Log.e("Media3Player_Debug", detail, error)

                    _errorMessage.value = detail
                    _playbackState.value = PlaybackState.ERROR
                }
            })
        }

    override fun play(url: String) {
        _errorMessage.value = ""
        Log.d("Media3Player_Debug", "准备极速加载: $url")

        // 【核心修复：智能识别流类型】
        // 如果链接包含 .m3u8，或者链接最后一部分没有任何后缀（比如 /CCTV-1），我们强制它使用 HLS 解析器
        val isLikelyHls = url.contains(".m3u8", ignoreCase = true) || !url.substringAfterLast("/").contains(".")

        val mediaItemBuilder = MediaItem.Builder().setUri(url)

        if (isLikelyHls) {
            Log.d("Media3Player_Debug", "判定为 HLS/M3U8 直播流，强制设置 MimeType")
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