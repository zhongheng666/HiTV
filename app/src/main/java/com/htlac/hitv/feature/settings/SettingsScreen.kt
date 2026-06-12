package com.htlac.hitv.feature.settings

import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.htlac.hitv.core.utils.NetworkUtil
import com.htlac.hitv.core.utils.QrCodeUtil
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToPlayer: () -> Unit
) {
    val savedIptvUrl by viewModel.iptvUrl.collectAsState()
    val savedEpgUrl by viewModel.epgUrl.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    var inputIptvText by remember { mutableStateOf("") }
    var inputEpgText by remember { mutableStateOf("") }

    val saveButtonFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val localIp = remember { NetworkUtil.getLocalIpAddress() }
    val serverUrl = if (localIp.isNotEmpty()) "http://$localIp:8080" else ""
    val qrCodeBitmap = remember(serverUrl) { QrCodeUtil.generateQrCode(serverUrl) }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(syncState) {
        if (syncState is SyncState.Success) {
            val count = (syncState as SyncState.Success).channelCount
            // 在电视端，Toast 是极其标准的全局提醒方式，不会抢夺焦点
            android.widget.Toast.makeText(context, "✅ 成功导入 $count 个频道", android.widget.Toast.LENGTH_LONG).show()

            onNavigateToPlayer()
            viewModel.resetState()
        }
    }

    LaunchedEffect(savedIptvUrl, savedEpgUrl) {
        if (inputIptvText.isEmpty() && savedIptvUrl.isNotEmpty()) inputIptvText = savedIptvUrl
        if (inputEpgText.isEmpty() && savedEpgUrl.isNotEmpty()) inputEpgText = savedEpgUrl
    }

    LaunchedEffect(Unit) {
        delay(50)
        saveButtonFocusRequester.requestFocus()
        keyboardController?.hide()
    }

    val isLoading = syncState is SyncState.Loading
    val isError = syncState is SyncState.Error
    val errorMessage = if (isError) (syncState as SyncState.Error).message else ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(horizontal = 64.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ================= 左侧：扫码区域 =================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("扫码快速配置", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier.size(220.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrCodeBitmap != null) {
                        Image(bitmap = qrCodeBitmap.asImageBitmap(), contentDescription = "二维码", modifier = Modifier.fillMaxSize())
                    } else {
                        Text("获取局域网IP失败", color = Color.Red)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (serverUrl.isNotEmpty()) "也可在电脑浏览器输入\n$serverUrl" else "请确保设备已连接WiFi",
                    color = Color.Gray, fontSize = 16.sp, textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(64.dp))

            // ================= 右侧：手动配置区域 =================
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1.2f)
            ) {
                Text("手动输入配置", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))

                // 使用我们自己封装的 TV 专属输入框
                TvOutlinedTextField(
                    value = inputIptvText,
                    onValueChange = { inputIptvText = it; if (isError) viewModel.resetState() },
                    label = "IPTV M3U 订阅链接"
                )

                Spacer(modifier = Modifier.height(16.dp))

                TvOutlinedTextField(
                    value = inputEpgText,
                    onValueChange = { inputEpgText = it; if (isError) viewModel.resetState() },
                    label = "节目单 XMLTV 链接"
                )

                if (isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorMessage, color = Color(0xFFFF453A), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Spacer(modifier = Modifier.height(32.dp))
                }

                Button(
                    onClick = {
                        if (!isLoading) {
                            keyboardController?.hide()
                            viewModel.saveUrlsAndSync(inputIptvText, inputEpgText)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(saveButtonFocusRequester),
                    colors = ButtonDefaults.colors(
                        containerColor = if (isLoading) Color.Gray else Color(0xFF0A84FF),
                        contentColor = Color.White,
                        focusedContainerColor = if (isLoading) Color.Gray else Color.White,
                        focusedContentColor = if (isLoading) Color.White else Color.Black
                    )
                ) {
                    Text(
                        text = if (isLoading) "频道解析中，请稍候..." else "保存并解析",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        fontSize = 18.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 【架构师特制组件】
 * 专为 TV 端设计的优雅输入框。
 * 焦点移上去只变蓝框，不弹键盘；按下确认键才进入编辑模式弹键盘。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    // 两个状态：isFocused(是否被遥控器选中)，isEditing(是否按了确认键正在打字)
    var isFocused by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (isEditing) {
        // 真正的输入框（只在需要打字时才现身）
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (!state.isFocused) {
                        isEditing = false // 焦点一旦离开，自动退出编辑模式
                    }
                }
                .onPreviewKeyEvent { event ->
                    // 核心逻辑：按确认键、回车键，或者上下方向键，都能优雅退出编辑模式并收起键盘
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                isEditing = false
                                keyboardController?.hide()
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP -> {
                                isEditing = false
                                keyboardController?.hide()
                                return@onPreviewKeyEvent false // 不拦截，让系统自动把焦点移给下一个组件
                            }
                        }
                    }
                    false
                },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF0A84FF),
                unfocusedBorderColor = Color.DarkGray
            )
        )
        // 只要这个真输入框一现身，立刻强制索要焦点并弹出键盘
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    } else {
        // 伪装的输入框（平时显示用，防误触弹键盘）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state -> isFocused = state.isFocused }
                .focusable()
                .clickable {
                    // 当遥控器按下确认键时，触发变身
                    isEditing = true
                }
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color(0xFF0A84FF) else Color.DarkGray,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 16.dp, vertical = 18.dp) // 模拟 OutlinedTextField 的高度
        ) {
            if (value.isEmpty()) {
                Text(label, color = Color.Gray, fontSize = 16.sp)
            } else {
                Text(value, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}