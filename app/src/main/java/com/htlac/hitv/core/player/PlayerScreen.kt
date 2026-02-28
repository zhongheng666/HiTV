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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor

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

        // Debug 面板
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

/**
 * 终极版 Apple TV 风格侧边栏
 * 特性：动态读取真实可视数量跳页、笔直左对齐排版、极致沉浸半透明
 */
@Composable
fun ChannelListSidebar(
    channels: List<Channel>,
    currentPlaying: Channel?,
    onChannelSelected: (Channel) -> Unit,
    onClose: () -> Unit
) {
    var userActionTrigger by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }

    val initialIndex = remember {
        val idx = channels.indexOfFirst { it.id == currentPlaying?.id }
        if (idx >= 0) idx else 0
    }

    var currentFocusedIndex by remember { mutableIntStateOf(initialIndex) }

    // 【核心魔法 1：动态监听真实可视数量】
    // 彻底告别写死或猜高度，直接让系统告诉我们当前屏幕到底装了几个！
    var dynamicJumpStep by remember { mutableIntStateOf(8) }
    LaunchedEffect(listState.layoutInfo.visibleItemsInfo.size) {
        val visibleCount = listState.layoutInfo.visibleItemsInfo.size
        // 减 1 是为了翻页时保留最后一个频道作为视觉连贯的上下文
        if (visibleCount > 2) {
            dynamicJumpStep = visibleCount - 1
        }
    }

    // 8秒无操作自动收起
    LaunchedEffect(userActionTrigger) {
        delay(8000)
        onClose()
    }

    // 呼出时自动定位到当前播放频道
    LaunchedEffect(Unit) {
        if (channels.isNotEmpty()) {
            listState.scrollToItem(initialIndex)
            delay(100)
            focusRequesters[initialIndex]?.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.1f)) // 最外层极浅遮罩
            .clickable { onClose() }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    userActionTrigger++
                }
                false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(360.dp)
                // 【核心魔法 2：高通透沉浸式背景】
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(24.dp)
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (event.nativeKeyEvent.keyCode) {
                            // 【基于真实视角的极速跳页】
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (channels.isNotEmpty() && currentFocusedIndex > 0) {
                                    val target = (currentFocusedIndex - dynamicJumpStep).coerceAtLeast(0)
                                    coroutineScope.launch {
                                        listState.scrollToItem(target)
                                        delay(50)
                                        focusRequesters[target]?.requestFocus()
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (channels.isNotEmpty() && currentFocusedIndex < channels.lastIndex) {
                                    val target = (currentFocusedIndex + dynamicJumpStep).coerceAtMost(channels.lastIndex)
                                    coroutineScope.launch {
                                        listState.scrollToItem(target)
                                        delay(50)
                                        focusRequesters[target]?.requestFocus()
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    false
                }
        ) {
            // 动态计算页码指示器
            val currentPageIndicator = (currentFocusedIndex / dynamicJumpStep) + 1
            val totalPages = if (channels.isEmpty()) 1 else ceil(channels.size / dynamicJumpStep.toFloat()).toInt()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("全部频道", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("$currentPageIndicator / $totalPages", color = Color.LightGray, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(channels) { index, channel ->
                    val isPlaying = channel.id == currentPlaying?.id
                    var isFocused by remember { mutableStateOf(false) }

                    val requester = focusRequesters.getOrPut(index) { FocusRequester() }
                    val indexString = String.format("%03d", index + 1)

                    val scale by animateFloatAsState(
                        targetValue = if (isFocused) 1.05f else 1f,
                        animationSpec = tween(durationMillis = 150),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(scale)
                            .shadow(if (isFocused) 12.dp else 0.dp, RoundedCornerShape(12.dp))
                            .focusRequester(requester)
                            .onFocusChanged { state ->
                                isFocused = state.isFocused
                                if (state.isFocused) currentFocusedIndex = index
                            }
                            .focusable()
                            .clickable { onChannelSelected(channel) }
                            .clip(RoundedCornerShape(12.dp))
                            // 选中时白底黑字，未选中时全透明
                            .background(if (isFocused) Color.White else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // 【核心魔法 3：笔直左对齐 + 视觉居中偏移】
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp), // 整体往右挤一点，达成视觉居中
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start // 坚决左对齐，保证序号成一条直线
                        ) {
                            Text(
                                text = indexString,
                                color = if (isFocused) Color.DarkGray else Color.LightGray,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(44.dp) // 固定序号宽度，充当强力锚点
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = channel.name,
                                color = if (isFocused) Color.Black else (if (isPlaying) Color(0xFF0A84FF) else Color.White),
                                fontWeight = if (isFocused || isPlaying) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 17.sp,
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