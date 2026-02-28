package com.htlac.hitv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.htlac.hitv.core.data.repository.ChannelRepository
import com.htlac.hitv.core.player.PlaybackState
import com.htlac.hitv.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SimplePlayerViewModel @Inject constructor(
    val playerController: PlayerController,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    fun playFirstChannel() {
        viewModelScope.launch {
            val channels = channelRepository.getAllChannels().firstOrNull()
            if (!channels.isNullOrEmpty()) {
                val firstChannel = channels[0]
                playerController.play(firstChannel.url)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}

@Composable
fun PlayerScreen(
    viewModel: SimplePlayerViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playerController.playbackState.collectAsState()
    // 获取深层报错信息
    val errorMessage by viewModel.playerController.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.playFirstChannel()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.playerController.player
                    useController = false
                }
            }
        )

        if (playbackState == PlaybackState.BUFFERING) {
            Text(text = "加载中...", color = Color.White)
        }

        // 发生错误时，将详细的 Debug 信息显示在屏幕上
        if (playbackState == PlaybackState.ERROR) {
            Text(
                text = "播放失败\n\n$errorMessage\n\n(注意：若是局域网IP，请确保模拟器能连通该IP)",
                color = Color.Red,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
    }
}