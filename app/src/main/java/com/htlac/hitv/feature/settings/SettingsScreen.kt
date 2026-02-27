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
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val savedIptvUrl by viewModel.iptvUrl.collectAsState()
    val savedEpgUrl by viewModel.epgUrl.collectAsState()

    var inputIptvText by remember { mutableStateOf("") }
    var inputEpgText by remember { mutableStateOf("") }

    val saveButtonFocusRequester = remember { FocusRequester() }

    // 引入焦点大管家和键盘控制器
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val localIp = remember { NetworkUtil.getLocalIpAddress() }
    val serverUrl = if (localIp.isNotEmpty()) "http://$localIp:8080" else ""
    val qrCodeBitmap = remember(serverUrl) { QrCodeUtil.generateQrCode(serverUrl) }

    DisposableEffect(Unit) {
        val webServer = HiTvWebServer(port = 8080) { iptv, epg ->
            if (iptv.isNotEmpty()) inputIptvText = iptv
            if (epg.isNotEmpty()) inputEpgText = epg
            viewModel.saveUrls(iptv, epg)
        }
        try { webServer.start() } catch (e: Exception) { e.printStackTrace() }
        onDispose { webServer.stop() }
    }

    LaunchedEffect(savedIptvUrl, savedEpgUrl) {
        if (inputIptvText.isEmpty() && savedIptvUrl.isNotEmpty()) inputIptvText = savedIptvUrl
        if (inputEpgText.isEmpty() && savedEpgUrl.isNotEmpty()) inputEpgText = savedEpgUrl
    }

    // 完美解决自动弹键盘：锁定焦点给按钮，并强行隐藏键盘
    LaunchedEffect(Unit) {
        delay(50) // 等待界面渲染
        saveButtonFocusRequester.requestFocus()
        keyboardController?.hide()
    }

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
                    modifier = Modifier
                        .size(220.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(12.dp),
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
                    color = Color.Gray,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
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

                // IPTV 框
                OutlinedTextField(
                    value = inputIptvText,
                    onValueChange = { inputIptvText = it },
                    label = { Text("IPTV M3U 订阅链接", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        // 【魔法：按键拦截】遥控器按下键时，强制焦点往下走，逃离文本框！
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        focusManager.moveFocus(FocusDirection.Down)
                                        return@onPreviewKeyEvent true
                                    }
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

                Spacer(modifier = Modifier.height(16.dp))

                // EPG 框
                OutlinedTextField(
                    value = inputEpgText,
                    onValueChange = { inputEpgText = it },
                    label = { Text("节目单 XMLTV 链接", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        // 同样加上按键拦截，支持上下键逃离
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        focusManager.moveFocus(FocusDirection.Down)
                                        return@onPreviewKeyEvent true
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        focusManager.moveFocus(FocusDirection.Up)
                                        return@onPreviewKeyEvent true
                                    }
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

                Spacer(modifier = Modifier.height(32.dp))

                // 保存按钮
                Button(
                    onClick = {
                        viewModel.saveUrls(inputIptvText, inputEpgText)
                        keyboardController?.hide() // 点击保存后也顺手收起键盘
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(saveButtonFocusRequester),
                    // 【魔法：自定义按钮颜色】强制它始终像一个实体按钮，遥控器选中时底色变白，文字变黑
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF0A84FF),         // 平时的底色：苹果蓝
                        contentColor = Color.White,                 // 平时的字色：白色
                        focusedContainerColor = Color.White,        // 选中时的底色：白色
                        focusedContentColor = Color.Black           // 选中时的字色：黑色
                    )
                ) {
                    Text(
                        text = "保存配置",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}