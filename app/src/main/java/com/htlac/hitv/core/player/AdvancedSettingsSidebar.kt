package com.htlac.hitv.core.player

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

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