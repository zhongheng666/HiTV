package com.htlac.hitv.feature.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.htlac.hitv.core.data.local.Channel
import com.htlac.hitv.core.data.repository.ChannelRepository
import com.htlac.hitv.core.player.Media3Player
import com.htlac.hitv.core.player.PlaybackState
import com.htlac.hitv.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.ceil

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerController: PlayerController,
    channelRepository: ChannelRepository
) : ViewModel() {

    val allChannels: StateFlow<List<Channel>> = channelRepository.getAllChannels()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    var isChannelListVisible by mutableStateOf(false)
    var currentPlayingChannel by mutableStateOf<Channel?>(null)

    fun playChannel(channel: Channel) {
        currentPlayingChannel = channel
        playerController.play(channel.url)
        isChannelListVisible = false
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playerController.playbackState.collectAsState()
    val errorMessage by viewModel.playerController.errorMessage.collectAsState()
    val channels by viewModel.allChannels.collectAsState()

    val debugInfo by (viewModel.playerController as Media3Player).debugInfo.collectAsState()

    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(channels) {
        if (channels.isNotEmpty() && viewModel.currentPlayingChannel == null) {
            viewModel.playChannel(channels[0])
        }
    }

    LaunchedEffect(viewModel.isChannelListVisible) {
        if (!viewModel.isChannelListVisible) {
            try { rootFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    BackHandler(enabled = viewModel.isChannelListVisible) {
        viewModel.isChannelListVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (!viewModel.isChannelListVisible) {
                                viewModel.isChannelListVisible = true
                                return@onPreviewKeyEvent true
                            }
                        }
                        // 删除了隐藏面板的快捷键，现在 Debug 面板强制常驻
                    }
                }
                false
            },
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

        // 强制常驻的 Debug 面板
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "🛠 播放器 Debug 面板\n\n频道: ${viewModel.currentPlayingChannel?.name ?: "未选择"}\n$debugInfo",
                color = Color(0xFF00FF00),
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        if (playbackState == PlaybackState.ERROR) {
            Text(
                text = "播放失败\n\n$errorMessage",
                color = Color.Red,
                textAlign = TextAlign.Center,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.8f)).padding(16.dp)
            )
        }

        if (viewModel.isChannelListVisible) {
            ChannelListSidebar(
                channels = channels,
                currentPlaying = viewModel.currentPlayingChannel,
                onChannelSelected = { viewModel.playChannel(it) },
                onClose = { viewModel.isChannelListVisible = false }
            )
        }
    }
}

@Composable
fun ChannelListSidebar(
    channels: List<Channel>,
    currentPlaying: Channel?,
    onChannelSelected: (Channel) -> Unit,
    onClose: () -> Unit
) {
    // 【修改 1：完美契合你的屏幕，防止挤出屏幕外】
    val pageSize = 8
    var currentPage by remember { mutableIntStateOf(0) }

    // 【修改 2：超时自动隐藏大管家】
    var userActionTrigger by remember { mutableIntStateOf(0) }

    val totalPages = if (channels.isEmpty()) 1 else ceil(channels.size / pageSize.toFloat()).toInt()

    val pagedChannels = if (channels.isNotEmpty()) {
        channels.chunked(pageSize).getOrNull(currentPage) ?: emptyList()
    } else {
        emptyList()
    }

    val firstItemFocusRequester = remember { FocusRequester() }

    // 当用户没有任何按键动作超过 8 秒时，自动收起侧边栏
    LaunchedEffect(userActionTrigger) {
        delay(8000) // 8000 毫秒 = 8 秒
        onClose()
    }

    LaunchedEffect(currentPage) {
        delay(50)
        try { firstItemFocusRequester.requestFocus() } catch (e: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onClose() }
            // 只要在这个全屏遮罩里发生任何按键（上下左右确认），都重置 8 秒倒计时
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    userActionTrigger++
                }
                false // 不拦截事件，继续往下传
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(360.dp)
                .background(Color(0xFF1C1C1E).copy(alpha = 0.98f))
                .padding(24.dp)
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (currentPage > 0) currentPage--
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (currentPage < totalPages - 1) currentPage++
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    false
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("全部频道", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${currentPage + 1} / $totalPages", color = Color.Gray, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pagedChannels) { index, channel ->
                    val globalIndex = (currentPage * pageSize) + index + 1
                    val indexString = String.format("%03d", globalIndex)

                    val isPlaying = channel.id == currentPlaying?.id
                    var isFocused by remember { mutableStateOf(false) }

                    val modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier

                    val scale by animateFloatAsState(
                        targetValue = if (isFocused) 1.05f else 1f,
                        animationSpec = tween(durationMillis = 150),
                        label = "scale"
                    )

                    Box(
                        modifier = modifier
                            .fillMaxWidth()
                            .scale(scale)
                            .shadow(if (isFocused) 12.dp else 0.dp, RoundedCornerShape(12.dp))
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .clickable { onChannelSelected(channel) }
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFocused) Color.White else Color(0xFF2C2C2E))
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = indexString,
                                color = if (isFocused) Color.DarkGray else Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(40.dp)
                            )

                            Text(
                                text = channel.name,
                                color = if (isFocused) Color.Black else (if (isPlaying) Color(0xFF0A84FF) else Color.White),
                                fontWeight = if (isFocused || isPlaying) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}