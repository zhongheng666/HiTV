package com.htlac.hitv.core.player

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import com.htlac.hitv.core.data.local.Channel
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.floor

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
}