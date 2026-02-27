package com.htlac.hitv.feature.settings

import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.htlac.hitv.core.utils.NetworkUtil
import com.htlac.hitv.core.utils.QrCodeUtil
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToPlayer: () -> Unit // 新增：当解析成功后，主 Activity 会传一个跳转指令进来
) {
    val savedIptvUrl by viewModel.iptvUrl.collectAsState()
    val savedEpgUrl by viewModel.epgUrl.collectAsState()
    val syncState by viewModel.syncState.collectAsState() // 监听解析状态

    var inputIptvText by remember { mutableStateOf("") }
    var inputEpgText by remember { mutableStateOf("") }

    val saveButtonFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val localIp = remember { NetworkUtil.getLocalIpAddress() }
    val serverUrl = if (localIp.isNotEmpty()) "http://$localIp:8080" else ""
    val qrCodeBitmap = remember(serverUrl) { QrCodeUtil.generateQrCode(serverUrl) }

    // 状态监听：成功就跳走，失败就重置以便用户修改
    LaunchedEffect(syncState) {
        if (syncState is SyncState.Success) {
            onNavigateToPlayer()
            viewModel.resetState()
        }
    }

    DisposableEffect(Unit) {
        val webServer = HiTvWebServer(port = 8080) { iptv, epg ->
            if (iptv.isNotEmpty()) inputIptvText = iptv
            if (epg.isNotEmpty()) inputEpgText = epg
            // 收到手机推送后，直接自动触发保存和解析！不需要遥控器再点一次
            viewModel.saveUrlsAndSync(iptv, epg)
        }
        try { webServer.start() } catch (e: Exception) { e.printStackTrace() }
        onDispose { webServer.stop() }
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

    // 根据不同的状态，决定按钮上显示的文字和颜色
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
            // 左侧扫码区保持不变
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

            // 右侧手动配置区
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1.2f)
            ) {
                Text("手动输入配置", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = inputIptvText, onValueChange = { inputIptvText = it; if (isError) viewModel.resetState() },
                    label = { Text("IPTV M3U 订阅链接", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                focusManager.moveFocus(FocusDirection.Down)
                                return@onPreviewKeyEvent true
                            }
                            false
                        },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF0A84FF), unfocusedBorderColor = Color.DarkGray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = inputEpgText, onValueChange = { inputEpgText = it; if (isError) viewModel.resetState() },
                    label = { Text("节目单 XMLTV 链接", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_DOWN -> { focusManager.moveFocus(FocusDirection.Down); return@onPreviewKeyEvent true }
                                    KeyEvent.KEYCODE_DPAD_UP -> { focusManager.moveFocus(FocusDirection.Up); return@onPreviewKeyEvent true }
                                }
                            }
                            false
                        },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF0A84FF), unfocusedBorderColor = Color.DarkGray
                    )
                )

                // 如果出错，显示红色的错误提示
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
                        // 根据状态动态改变文字
                        text = if (isLoading) "频道解析中，请稍候..." else "保存并解析",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        fontSize = 18.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}