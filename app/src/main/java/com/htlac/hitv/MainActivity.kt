package com.htlac.hitv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Text
import com.htlac.hitv.core.network.NtpManager
import com.htlac.hitv.feature.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var ntpManager: NtpManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            ntpManager.syncTime()
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                // 引入 Compose 的大管家：导航控制器
                val navController = rememberNavController()

                // NavHost 相当于一个容器，管理着所有的页面
                NavHost(
                    navController = navController,
                    startDestination = "settings" // 默认一进来是设置页
                ) {
                    // 第一个页面：配置页
                    composable("settings") {
                        SettingsScreen(
                            onNavigateToPlayer = {
                                // 当 SettingsScreen 告诉我们解析成功了，我们就跳到 player 页！
                                // popUpTo("settings") { inclusive = true } 意思是跳走后，把设置页从返回栈里清掉（按返回键直接退应用，而不是回到设置）
                                navController.navigate("player") {
                                    popUpTo("settings") { inclusive = true }
                                }
                            }
                        )
                    }

                    // 第二个页面：未来的播放页（先用一个黑屏占位）
                    composable("player") {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black), // 播放器标准的纯黑背景
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "频道解析成功！即将加载第一集视频...",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}