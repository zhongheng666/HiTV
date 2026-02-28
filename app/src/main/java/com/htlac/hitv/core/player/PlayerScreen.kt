package com.htlac.hitv.feature.player

import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Text
import com.htlac.hitv.core.data.local.Channel
import com.htlac.hitv.core.data.local.EpgProgram
import com.htlac.hitv.core.player.Media3Player
import com.htlac.hitv.core.player.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playerController.playbackState.collectAsState()
    val errorMessage by viewModel.playerController.errorMessage.collectAsState()
    val channels by viewModel.allChannels.collectAsState()

    val debugInfo by (viewModel.playerController as Media3Player).debugInfo.collectAsState()
    val rootFocusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 【核心新增：监听 EPG 后台事件并弹窗】
    LaunchedEffect(Unit) {
        viewModel.epgSyncEvent.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

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
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!viewModel.isChannelListVisible) {
                                viewModel.playPreviousChannel()
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!viewModel.isChannelListVisible) {
                                viewModel.playNextChannel()
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!viewModel.isChannelListVisible) {
                                viewModel.showEpgCard()
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

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text(
                // 将 EPG 诊断信息合并显示在面板上
                text = "🛠 播放器 Debug 面板\n\n频道: ${viewModel.currentPlayingChannel?.name ?: "未选择"}\n\n${viewModel.epgDebugInfo}\n\n$debugInfo",
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

        // ================= 底部 EPG 节目预告卡片 =================
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 64.dp, end = 64.dp)
        ) {
            AnimatedVisibility(
                visible = viewModel.isEpgVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                EpgBottomCard(
                    channelName = viewModel.currentPlayingChannel?.name ?: "",
                    currentProgram = viewModel.currentProgram,
                    nextProgram = viewModel.nextProgram
                )
            }
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
fun EpgBottomCard(
    channelName: String,
    currentProgram: EpgProgram?,
    nextProgram: EpgProgram?
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1E).copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = channelName,
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.3f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(32.dp))

        Column(modifier = Modifier.weight(0.7f)) {
            if (currentProgram != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("正在播放", color = Color(0xFF0A84FF), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                    Text(
                        text = "${timeFormatter.format(Date(currentProgram.startTime))} - ${timeFormatter.format(Date(currentProgram.endTime))}",
                        color = Color.LightGray, fontSize = 16.sp, modifier = Modifier.width(130.dp)
                    )
                    Text(currentProgram.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text("暂无当前节目信息", color = Color.Gray, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (nextProgram != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("即将播放", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.width(80.dp))
                    Text(
                        text = "${timeFormatter.format(Date(nextProgram.startTime))} - ${timeFormatter.format(Date(nextProgram.endTime))}",
                        color = Color.DarkGray, fontSize = 14.sp, modifier = Modifier.width(130.dp)
                    )
                    Text(nextProgram.title, color = Color.LightGray, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
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
    var userActionTrigger by remember { mutableIntStateOf(0) }
    val tvListState = rememberTvLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }

    var currentFocusedIndex by remember {
        mutableIntStateOf(channels.indexOfFirst { it.id == currentPlaying?.id }.coerceAtLeast(0))
    }

    var isJumpingPage by remember { mutableStateOf(false) }

    LaunchedEffect(userActionTrigger) {
        delay(8000)
        onClose()
    }

    LaunchedEffect(Unit) {
        if (channels.isNotEmpty()) {
            tvListState.scrollToItem(currentFocusedIndex, 0)
            delay(150)
            focusRequesters[currentFocusedIndex]?.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.1f))
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
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(24.dp)
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        val layoutInfo = tvListState.layoutInfo
                        val visibleItems = layoutInfo.visibleItemsInfo

                        if (visibleItems.isNotEmpty()) {
                            val actualPageSize = (visibleItems.last().index - visibleItems.first().index).coerceAtLeast(1)

                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    if (isJumpingPage) return@onPreviewKeyEvent true
                                    if (currentFocusedIndex > 0) {
                                        isJumpingPage = true
                                        val target = (currentFocusedIndex - actualPageSize).coerceAtLeast(0)
                                        coroutineScope.launch {
                                            tvListState.scrollToItem(target, 0)
                                            delay(100)
                                            try { focusRequesters[target]?.requestFocus() } catch (e: Exception) {}
                                            isJumpingPage = false
                                        }
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    if (isJumpingPage) return@onPreviewKeyEvent true
                                    if (currentFocusedIndex < channels.lastIndex) {
                                        isJumpingPage = true
                                        val target = (currentFocusedIndex + actualPageSize).coerceAtMost(channels.lastIndex)
                                        coroutineScope.launch {
                                            tvListState.scrollToItem(target, 0)
                                            delay(100)
                                            try { focusRequesters[target]?.requestFocus() } catch (e: Exception) {}
                                            isJumpingPage = false
                                        }
                                    }
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                    }
                    false
                }
        ) {
            val visibleItems = tvListState.layoutInfo.visibleItemsInfo
            // 兜底逻辑防止算术异常
            val actualPageSize = if (visibleItems.size > 1) visibleItems.last().index - visibleItems.first().index else 8
            val firstVisible = tvListState.firstVisibleItemIndex

            val currentPage = (firstVisible / actualPageSize) + 1
            val totalPages = ceil(channels.size.toFloat() / actualPageSize).toInt().coerceAtLeast(1)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("全部频道", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("$currentPage / $totalPages", color = Color.LightGray, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            TvLazyColumn(
                state = tvListState,
                modifier = Modifier.fillMaxSize(),
                pivotOffsets = PivotOffsets(parentFraction = 0f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = channels,
                    key = { _, channel -> channel.id }
                ) { index, channel ->
                    val isPlaying = channel.id == currentPlaying?.id
                    var isFocused by remember { mutableStateOf(false) }
                    val requester = focusRequesters.getOrPut(index) { FocusRequester() }

                    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, tween(150), label = "scale")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .scale(scale)
                            .shadow(if (isFocused) 12.dp else 0.dp, RoundedCornerShape(12.dp))
                            .focusRequester(requester)
                            .onFocusChanged {
                                isFocused = it.isFocused
                                if (it.isFocused) currentFocusedIndex = index
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                                        onChannelSelected(channel)
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                false
                            }
                            .focusable()
                            .clickable { onChannelSelected(channel) }
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFocused) Color.White else Color.Transparent)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                text = String.format("%03d", index + 1),
                                color = if (isFocused) Color.DarkGray else Color.LightGray,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(44.dp)
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