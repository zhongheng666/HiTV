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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlin.math.floor

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    val playbackState by viewModel.playerController.playbackState.collectAsState()
    val errorMessage by viewModel.playerController.errorMessage.collectAsState()
    val channels by viewModel.allChannels.collectAsState()
    val debugInfo by (viewModel.playerController as Media3Player).debugInfo.collectAsState()

    val currentIptv by viewModel.currentIptvUrl.collectAsState()
    val iptvHistory by viewModel.iptvHistory.collectAsState()
    val currentEpg by viewModel.currentEpgUrl.collectAsState()
    val epgHistory by viewModel.epgHistory.collectAsState()
    val useMpv by viewModel.useMpv.collectAsState()
    val forceSoftAudio by viewModel.forceSoftAudio.collectAsState()

    val rootFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    var isLongPressHandled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.epgSyncEvent.collect { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    LaunchedEffect(channels) {
        if (channels.isNotEmpty() && viewModel.currentPlayingChannel == null) viewModel.playChannel(channels[0])
    }

    LaunchedEffect(viewModel.isChannelListVisible, viewModel.isAdvancedSettingsVisible) {
        if (!viewModel.isChannelListVisible && !viewModel.isAdvancedSettingsVisible) {
            try { rootFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    BackHandler(enabled = viewModel.isChannelListVisible || viewModel.isAdvancedSettingsVisible) {
        if (viewModel.isAdvancedSettingsVisible) viewModel.isAdvancedSettingsVisible = false
        else if (viewModel.isChannelListVisible) viewModel.isChannelListVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (viewModel.isSyncing) return@onPreviewKeyEvent true

                // 【核心修复：全局拦截只在没有任何侧边栏打开时才生效，把确认键交还给列表子组件！】
                if (!viewModel.isChannelListVisible && !viewModel.isAdvancedSettingsVisible) {
                    if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            if (event.nativeKeyEvent.repeatCount > 0 && !isLongPressHandled) {
                                isLongPressHandled = true
                                viewModel.isAdvancedSettingsVisible = true
                            }
                            return@onPreviewKeyEvent true
                        } else if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                            if (!isLongPressHandled) {
                                viewModel.isChannelListVisible = true
                            }
                            isLongPressHandled = false
                            return@onPreviewKeyEvent true
                        }
                    }

                    // 盲操快捷换台也只在无侧边栏时生效
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> { viewModel.playPreviousChannel(); return@onPreviewKeyEvent true }
                            KeyEvent.KEYCODE_DPAD_DOWN -> { viewModel.playNextChannel(); return@onPreviewKeyEvent true }
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> { viewModel.showEpgCard(); return@onPreviewKeyEvent true }
                        }
                    }
                }
                false
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> PlayerView(ctx).apply { player = viewModel.playerController.player; useController = false } }
        )

        if (viewModel.isSyncing) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在同步新源频道，请稍候...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp).background(Color.Black.copy(0.7f), RoundedCornerShape(8.dp)).padding(16.dp)
        ) {
            Text("🛠 播放器 Debug 面板\n\n频道: ${viewModel.currentPlayingChannel?.name ?: "未选择"}\n\n${viewModel.epgDebugInfo}\n\n$debugInfo", color = Color(0xFF00FF00), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }

        if (playbackState == PlaybackState.ERROR && !viewModel.isSyncing) {
            Text("播放失败\n\n$errorMessage", color = Color.Red, textAlign = TextAlign.Center, modifier = Modifier.background(Color.Black.copy(0.8f)).padding(16.dp))
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 48.dp, start = 64.dp, end = 64.dp)) {
            AnimatedVisibility(visible = viewModel.isEpgVisible, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it })) {
                EpgBottomCard(viewModel.currentPlayingChannel?.name ?: "", viewModel.currentProgram, viewModel.nextProgram)
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

        if (viewModel.isAdvancedSettingsVisible) {
            AdvancedSettingsSidebar(
                currentIptv = currentIptv,
                iptvHistory = iptvHistory.toList(),
                currentEpg = currentEpg,
                epgHistory = epgHistory.toList(),
                useMpv = useMpv,
                forceSoftAudio = forceSoftAudio,
                onSwitchIptv = { viewModel.switchIptvSource(it) },
                onSwitchEpg = { viewModel.switchEpgSource(it) },
                onToggleMpv = { viewModel.toggleMpv(it) },
                onToggleSoftAudio = { viewModel.toggleSoftAudio(it) },
                onAddNewSource = { viewModel.isAdvancedSettingsVisible = false; onNavigateToSettings() },
                onClose = { viewModel.isAdvancedSettingsVisible = false }
            )
        }
    }
}

/**
 * 高级设置：加入了超时自动隐藏机制
 */
@Composable
fun AdvancedSettingsSidebar(
    currentIptv: String,
    iptvHistory: List<String>,
    currentEpg: String,
    epgHistory: List<String>,
    useMpv: Boolean,
    forceSoftAudio: Boolean,
    onSwitchIptv: (String) -> Unit,
    onSwitchEpg: (String) -> Unit,
    onToggleMpv: (Boolean) -> Unit,
    onToggleSoftAudio: (Boolean) -> Unit,
    onAddNewSource: () -> Unit,
    onClose: () -> Unit
) {
    var userActionTrigger by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val firstItemFocusRequester = remember { FocusRequester() }

    // 【新增：15 秒无操作自动收起高级设置】
    LaunchedEffect(userActionTrigger) {
        delay(15000)
        onClose()
    }

    fun formatUrl(url: String): String {
        if (url.isBlank()) return "空"
        return url.replace("http://", "").replace("https://", "").take(35) + if (url.length > 35) "..." else ""
    }

    LaunchedEffect(Unit) {
        delay(50)
        try { firstItemFocusRequester.requestFocus() } catch (e: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.1f))
            .clickable { onClose() }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    userActionTrigger++ // 记录用户的任何操作，重置倒计时
                }
                false
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier.fillMaxHeight().width(420.dp).background(Color.Black.copy(alpha = 0.85f)).padding(top = 32.dp, bottom = 32.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text("高级与多源设置", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(modifier = Modifier.height(32.dp))
                }

                item {
                    Text("IPTV 直播源", color = Color(0xFF0A84FF), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
                }
                items(iptvHistory) { url ->
                    val isSelected = url == currentIptv
                    TvRadioItem(text = formatUrl(url), isSelected = isSelected, onClick = { onSwitchIptv(url) }, modifier = if (url == iptvHistory.firstOrNull()) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                }
                item {
                    TvActionItem("➕ 扫码添加新 IPTV / EPG 源", onClick = onAddNewSource)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Text("EPG 节目单源", color = Color(0xFF34C759), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
                }
                items(epgHistory) { url ->
                    TvRadioItem(text = formatUrl(url), isSelected = url == currentEpg, onClick = { onSwitchEpg(url) })
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }

                item {
                    Text("播放器内核调度 (立即生效)", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
                }
                item { TvToggleItem("使用 MPV 备用内核", checked = useMpv, onCheckedChange = onToggleMpv) }
                item { TvToggleItem("强制开启音频软解", checked = forceSoftAudio, onCheckedChange = onToggleSoftAudio) }
                item { Spacer(modifier = Modifier.height(64.dp)) }
            }
        }
    }
}

@Composable
fun TvRadioItem(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1f, tween(150), label = "")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 6.dp)
            .scale(scale)
            .shadow(if (isFocused) 8.dp else 0.dp, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    onClick(); return@onPreviewKeyEvent true
                }
                false
            }
            .clickable { onClick() }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color.White else (if (isSelected) Color(0xFF1C1C1E) else Color.Transparent))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = if (isFocused) Color.Black else (if (isSelected) Color.White else Color.Gray), fontSize = 14.sp, fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (isSelected) {
                Spacer(modifier = Modifier.width(12.dp))
                Text("🟢 当前", color = if (isFocused) Color.Black else Color(0xFF34C759), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TvToggleItem(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1f, tween(150), label = "")

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 6.dp).scale(scale).shadow(if (isFocused) 8.dp else 0.dp, RoundedCornerShape(8.dp)).onFocusChanged { isFocused = it.isFocused }.focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    onCheckedChange(!checked); return@onPreviewKeyEvent true
                }
                false
            }
            .clickable { onCheckedChange(!checked) }.clip(RoundedCornerShape(8.dp)).background(if (isFocused) Color.White else Color(0xFF2C2C2E)).padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = if (isFocused) Color.Black else Color.White, fontSize = 15.sp, fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal)
            Text(if (checked) "🟢 开启" else "⚪ 关闭", color = if (isFocused) Color.Black else Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TvActionItem(text: String, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1f, tween(150), label = "")
    var modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 6.dp).scale(scale).shadow(if (isFocused) 8.dp else 0.dp, RoundedCornerShape(8.dp))
    if (focusRequester != null) modifier = modifier.focusRequester(focusRequester)

    Box(
        modifier = modifier.onFocusChanged { isFocused = it.isFocused }.focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    onClick(); return@onPreviewKeyEvent true
                }
                false
            }
            .clickable { onClick() }.clip(RoundedCornerShape(8.dp)).background(if (isFocused) Color.White else Color(0xFF2C2C2E)).padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (isFocused) Color.Black else Color.White, fontSize = 15.sp, fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal)
    }
}

/**
 * 频道列表
 */
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
                                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
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

@Composable
fun EpgBottomCard(channelName: String, currentProgram: EpgProgram?, nextProgram: EpgProgram?) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1C1C1E).copy(alpha = 0.85f), RoundedCornerShape(16.dp)).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = channelName, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.width(32.dp))
        Column(modifier = Modifier.weight(0.7f)) {
            if (currentProgram != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("正在播放", color = Color(0xFF0A84FF), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                    Text(text = "${timeFormatter.format(Date(currentProgram.startTime))} - ${timeFormatter.format(Date(currentProgram.endTime))}", color = Color.LightGray, fontSize = 16.sp, modifier = Modifier.width(130.dp))
                    Text(currentProgram.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text("暂无当前节目信息", color = Color.Gray, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (nextProgram != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("即将播放", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.width(80.dp))
                    Text(text = "${timeFormatter.format(Date(nextProgram.startTime))} - ${timeFormatter.format(Date(nextProgram.endTime))}", color = Color.DarkGray, fontSize = 14.sp, modifier = Modifier.width(130.dp))
                    Text(nextProgram.title, color = Color.LightGray, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}