package com.htlac.hitv.core.player

import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.htlac.hitv.core.player.Media3Player
import com.htlac.hitv.core.player.MpvPlayer
import com.htlac.hitv.core.player.PlaybackState
import com.htlac.hitv.core.player.PlayerViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                Lifecycle.Event.ON_STOP -> activeController.stop()
                Lifecycle.Event.ON_RESUME -> viewModel.currentPlayingChannel?.let { viewModel.playChannel(it) }
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

        // 使用优化后的时钟逻辑
        if (viewModel.showClock && !viewModel.isSyncing) {
            val currentTimeString by produceState(initialValue = "") {
                val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                while (true) {
                    value = formatter.format(Date())
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