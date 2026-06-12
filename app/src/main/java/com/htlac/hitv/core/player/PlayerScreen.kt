package com.htlac.hitv.feature.player

import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.htlac.hitv.core.data.local.Channel
import com.htlac.hitv.core.data.local.EpgProgram
import com.htlac.hitv.core.player.Media3Player
import com.htlac.hitv.core.player.MpvPlayer
import com.htlac.hitv.core.player.PlaybackState
import kotlinx.coroutines.delay
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
    val activeController by viewModel.activePlayer.collectAsState()
    val playbackState by activeController.playbackState.collectAsState()
    val errorMessage by activeController.errorMessage.collectAsState()
    val debugInfo by activeController.debugInfo.collectAsState()

    val channels by viewModel.allChannels.collectAsState()
    val isNtpSynced by viewModel.ntpSynced.collectAsState()
    val currentIptv by viewModel.currentIptvUrl.collectAsState()
    val iptvHistory by viewModel.iptvHistory.collectAsState()
    val currentEpg by viewModel.currentEpgUrl.collectAsState()
    val epgHistory by viewModel.epgHistory.collectAsState()
    val useMpv by viewModel.useMpv.collectAsState()
    val forceSoftAudio by viewModel.forceSoftAudio.collectAsState()

    val rootFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> activeController.pause()
                Lifecycle.Event.ON_STOP -> {
                    activeController.stop()
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.currentPlayingChannel?.let { viewModel.playChannel(it) }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        var currentToast: Toast? = null
        viewModel.epgSyncEvent.collect { message ->
            currentToast?.cancel()
            currentToast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
            currentToast?.show()
        }
    }

    LaunchedEffect(viewModel.isChannelListVisible, viewModel.isAdvancedSettingsVisible) {
        if (!viewModel.isChannelListVisible && !viewModel.isAdvancedSettingsVisible) {
            try { rootFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    var backPressedTime by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = true) {
        if (viewModel.isAdvancedSettingsVisible) {
            viewModel.isAdvancedSettingsVisible = false
        } else if (viewModel.isChannelListVisible) {
            viewModel.isChannelListVisible = false
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressedTime > 2000) {
                backPressedTime = currentTime
                Toast.makeText(context, "再按一次退出应用", Toast.LENGTH_SHORT).show()
            } else {
                var ctx = context
                while (ctx is android.content.ContextWrapper) {
                    if (ctx is android.app.Activity) {
                        ctx.finish()
                        break
                    }
                    ctx = ctx.baseContext
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                val action = event.nativeKeyEvent.action

                if (viewModel.isSyncing) return@onPreviewKeyEvent true

                if (action == KeyEvent.ACTION_DOWN) {
                    val digit = when (keyCode) {
                        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> (keyCode - KeyEvent.KEYCODE_0).toString()
                        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> (keyCode - KeyEvent.KEYCODE_NUMPAD_0).toString()
                        else -> null
                    }
                    if (digit != null) {
                        viewModel.onNumpadInput(digit)
                        return@onPreviewKeyEvent true
                    }
                }

                if (!viewModel.isChannelListVisible && !viewModel.isAdvancedSettingsVisible) {

                    if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == 172) {
                        if (action == KeyEvent.ACTION_DOWN) {
                            viewModel.isAdvancedSettingsVisible = true
                            return@onPreviewKeyEvent true
                        }
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                        if (action == KeyEvent.ACTION_UP) {
                            if (viewModel.numpadBuffer.isNotEmpty()) {
                                viewModel.executeNumpadSwitch()
                            } else {
                                viewModel.isChannelListVisible = true
                            }
                            return@onPreviewKeyEvent true
                        } else if (action == KeyEvent.ACTION_DOWN) {
                            return@onPreviewKeyEvent true
                        }
                    }

                    if (action == KeyEvent.ACTION_DOWN && viewModel.numpadBuffer.isEmpty()) {
                        when (keyCode) {
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

        when (val player = activeController) {
            is MpvPlayer -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) { viewModel.setSurface(this@apply) }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                override fun surfaceDestroyed(holder: SurfaceHolder) { viewModel.setSurface(null) }
                            })
                        }
                    }
                )
            }
            is Media3Player -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            useController = false
                            this.player = player.exoPlayer
                        }
                    },
                    update = { view -> view.player = player.exoPlayer }
                )
            }
        }

        if (viewModel.isSyncing) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在同步新源频道，请稍候...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (viewModel.showClock && !viewModel.isSyncing) {
            var currentTimeString by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                while (true) {
                    currentTimeString = formatter.format(Date())
                    delay(1000)
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (isNtpSynced) Color(0xFF34C759) else Color(0xFFFF3B30))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = currentTimeString, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Normal)
                }
            }
        }

        if (viewModel.showDebugPanel) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp).background(Color.Black.copy(0.7f), RoundedCornerShape(8.dp)).padding(16.dp)
            ) {
                Text("🛠 播放器 Debug 面板\n\n频道: ${viewModel.currentPlayingChannel?.name ?: "未选择"}\n\n${viewModel.epgDebugInfo}\n\n$debugInfo", color = Color(0xFF00FF00), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }

        Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 100.dp, end = 48.dp)) {
            AnimatedVisibility(
                visible = viewModel.numpadBuffer.isNotEmpty(),
                enter = fadeIn(tween(150)) + scaleIn(tween(150, delayMillis = 50)),
                exit = fadeOut(tween(150)) + scaleOut(tween(150))
            ) {
                Text(
                    text = viewModel.numpadBuffer, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp)).padding(horizontal = 32.dp, vertical = 16.dp)
                )
            }
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
                onOpenSettings = { viewModel.isAdvancedSettingsVisible = true },
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
                showDebugPanel = viewModel.showDebugPanel,
                showClock = viewModel.showClock,
                onSwitchIptv = { viewModel.switchIptvSource(it) },
                onSwitchEpg = { viewModel.switchEpgSource(it) },
                onDeleteIptv = { viewModel.deleteIptvSource(it) },
                onDeleteEpg = { viewModel.deleteEpgSource(it) },
                onToggleMpv = { viewModel.toggleMpv(it) },
                onToggleSoftAudio = { viewModel.toggleSoftAudio(it) },
                onToggleDebugPanel = { viewModel.showDebugPanel = it },
                onToggleClock = { viewModel.showClock = it },
                onAddNewSource = { viewModel.isAdvancedSettingsVisible = false; onNavigateToSettings() },
                onClose = { viewModel.isAdvancedSettingsVisible = false }
            )
        }
    }
}

@Composable
fun AdvancedSettingsSidebar(
    currentIptv: String,
    iptvHistory: List<String>,
    currentEpg: String,
    epgHistory: List<String>,
    useMpv: Boolean,
    forceSoftAudio: Boolean,
    showDebugPanel: Boolean,
    showClock: Boolean,
    onSwitchIptv: (String) -> Unit,
    onSwitchEpg: (String) -> Unit,
    onDeleteIptv: (String) -> Unit,
    onDeleteEpg: (String) -> Unit,
    onToggleMpv: (Boolean) -> Unit,
    onToggleSoftAudio: (Boolean) -> Unit,
    onToggleDebugPanel: (Boolean) -> Unit,
    onToggleClock: (Boolean) -> Unit,
    onAddNewSource: () -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    val firstItemFocusRequester = remember { FocusRequester() }

    fun formatUrl(url: String): String {
        if (url.isBlank()) return "空"
        return url.replace("http://", "").replace("https://", "").take(35) + if (url.length > 35) "..." else ""
    }

    LaunchedEffect(Unit) {
        delay(50)
        try { firstItemFocusRequester.requestFocus() } catch (e: Exception) {}
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f))
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                        onClose(); return@onPreviewKeyEvent true
                    }
                }
                false
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(modifier = Modifier.fillMaxHeight().width(460.dp).background(Color.Black.copy(alpha = 0.85f)).padding(top = 32.dp, bottom = 32.dp)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item {
                    Text("高级与多源设置", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(modifier = Modifier.height(32.dp))
                }

                item { Text("IPTV 直播源", color = Color(0xFF0A84FF), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) }
                items(iptvHistory) { url ->
                    val isSelected = url == currentIptv
                    TvRadioItem(
                        text = formatUrl(url),
                        isSelected = isSelected,
                        onClick = { onSwitchIptv(url) },
                        onDeleteClick = { onDeleteIptv(url) },
                        modifier = if (url == iptvHistory.firstOrNull()) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                    )
                }
                item {
                    TvActionItem("➕ 扫码添加新 IPTV / EPG 源", onClick = onAddNewSource)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item { Text("EPG 节目单源", color = Color(0xFF34C759), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) }
                items(epgHistory) { url ->
                    TvRadioItem(
                        text = formatUrl(url),
                        isSelected = url == currentEpg,
                        onClick = { onSwitchEpg(url) },
                        onDeleteClick = { onDeleteEpg(url) }
                    )
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }

                item { Text("播放器内核调度 (立即生效)", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) }
                item { TvToggleItem("使用 MPV 备用内核", checked = useMpv, onCheckedChange = onToggleMpv) }
                item { TvToggleItem("强制开启音频软解", checked = forceSoftAudio, onCheckedChange = onToggleSoftAudio) }
                item { Spacer(modifier = Modifier.height(24.dp)) }

                item { Text("界面显示设置", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) }
                item { TvToggleItem("显示右上角时钟", checked = showClock, onCheckedChange = onToggleClock) }
                item { TvToggleItem("显示底层 Debug 探针面板", checked = showDebugPanel, onCheckedChange = onToggleDebugPanel) }
                item { Spacer(modifier = Modifier.height(64.dp)) }
            }
        }
    }
}

@Composable
fun TvRadioItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val mainFocusRequester = remember { FocusRequester() }
    val deleteFocusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var isMainFocused by remember { mutableStateOf(false) }
        val mainScale by animateFloatAsState(if (isMainFocused) 1.03f else 1f, tween(150), label = "")

        Box(
            modifier = Modifier
                .weight(1f)
                .scale(mainScale)
                .shadow(if (isMainFocused) 8.dp else 0.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(if (isMainFocused) Color.White else (if (isSelected) Color(0xFF1C1C1E) else Color.Transparent))
                .focusRequester(mainFocusRequester)
                .onFocusChanged { isMainFocused = it.isFocused }
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && onDeleteClick != null) {
                            try { deleteFocusRequester.requestFocus() } catch (e: Exception) {}
                            return@onPreviewKeyEvent true
                        }
                        else if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                            onClick()
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                }
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text, color = if (isMainFocused) Color.Black else (if (isSelected) Color.White else Color.Gray), fontSize = 14.sp, fontWeight = if (isSelected || isMainFocused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (isSelected) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("🟢 当前", color = if (isMainFocused) Color.Black else Color(0xFF34C759), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (onDeleteClick != null) {
            Spacer(modifier = Modifier.width(12.dp))
            var isDelFocused by remember { mutableStateOf(false) }
            val delScale by animateFloatAsState(if (isDelFocused) 1.1f else 1f, tween(150), label = "")

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .scale(delScale)
                    .shadow(if (isDelFocused) 8.dp else 0.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isDelFocused) Color(0xFFFF3B30) else Color(0xFF2C2C2E))
                    .focusRequester(deleteFocusRequester)
                    .onFocusChanged { isDelFocused = it.isFocused }
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                try { mainFocusRequester.requestFocus() } catch (e: Exception) {}
                                return@onPreviewKeyEvent true
                            }
                            else if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                                onDeleteClick()
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }
                    .clickable { onDeleteClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("🗑️", fontSize = 18.sp)
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

@Composable
fun ChannelListSidebar(
    channels: List<Channel>,
    currentPlaying: Channel?,
    onChannelSelected: (Channel) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    var userActionTrigger by remember { mutableIntStateOf(0) }
    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }

    var currentFocusedIndex by remember {
        mutableIntStateOf(channels.indexOfFirst { it.urlHash == currentPlaying?.urlHash }.coerceAtLeast(0))
    }

    LaunchedEffect(userActionTrigger) { delay(3000); onClose() }

    LaunchedEffect(currentFocusedIndex) {
        delay(50)
        try {
            focusRequesters[currentFocusedIndex]?.requestFocus()
        } catch (e: Exception) {}
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)).clickable { onClose() }) {
        BoxWithConstraints(modifier = Modifier.fillMaxHeight().width(360.dp).background(Color.Black.copy(alpha = 0.45f)).padding(24.dp)) {
            val availableHeight = maxHeight.value
            val actualPageSize = maxOf(1, floor((availableHeight - 60f) / 68f).toInt())

            val currentPageIndex = maxOf(0, currentFocusedIndex) / actualPageSize
            val currentPageIndicator = currentPageIndex + 1
            val totalPages = if (channels.isEmpty()) 1 else ceil(channels.size / actualPageSize.toFloat()).toInt()

            val startIndex = currentPageIndex * actualPageSize
            val endIndex = minOf(startIndex + actualPageSize, channels.size)
            val currentPageChannels = if (channels.isNotEmpty()) channels.subList(startIndex, endIndex) else emptyList()

            Column(
                modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        userActionTrigger++
                        val keyCode = event.nativeKeyEvent.keyCode

                        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                            onClose(); return@onPreviewKeyEvent true
                        }

                        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == 172) {
                            onOpenSettings(); return@onPreviewKeyEvent true
                        }

                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (currentFocusedIndex > 0) currentFocusedIndex--; return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (currentFocusedIndex < channels.lastIndex) currentFocusedIndex++; return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (currentPageIndex > 0) {
                                    currentFocusedIndex = (currentPageIndex - 1) * actualPageSize
                                } else {
                                    onClose()
                                }
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (channels.isNotEmpty() && currentPageIndex < totalPages - 1) {
                                    currentFocusedIndex = minOf((currentPageIndex + 1) * actualPageSize, channels.lastIndex)
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    false
                }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("全部频道", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚙️ 按菜单键设置", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
                        Text("$currentPageIndicator / $totalPages", color = Color.LightGray, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    currentPageChannels.forEachIndexed { indexOnPage, channel ->
                        val globalIndex = startIndex + indexOnPage
                        val isPlaying = channel.urlHash == currentPlaying?.urlHash
                        val isFocused = globalIndex == currentFocusedIndex
                        val requester = focusRequesters.getOrPut(globalIndex) { FocusRequester() }
                        val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, tween(100), label = "scale")

                        Box(
                            modifier = Modifier.fillMaxWidth().height(60.dp).scale(scale).shadow(if (isFocused) 12.dp else 0.dp, RoundedCornerShape(12.dp)).focusRequester(requester)
                                .onFocusChanged { if (it.isFocused) currentFocusedIndex = globalIndex }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                                            onChannelSelected(channel); return@onPreviewKeyEvent true
                                        }
                                    }
                                    false
                                }
                                .focusable().clickable { onChannelSelected(channel) }.clip(RoundedCornerShape(12.dp)).background(if (isFocused) Color.White else Color.Transparent).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                                Text(String.format("%03d", globalIndex + 1), color = if (isFocused) Color.DarkGray else Color.LightGray, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(channel.name, color = if (isFocused) Color.Black else (if (isPlaying) Color(0xFF0A84FF) else Color.White), fontWeight = if (isFocused || isPlaying) FontWeight.Bold else FontWeight.Normal, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
} // 【修复】：这里加上了之前漏掉的那个闭合 ChannelListSidebar 的括号！

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