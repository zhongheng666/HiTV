package com.htlac.hitv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.htlac.hitv.core.data.datastore.SettingsManager
import com.htlac.hitv.core.network.NtpManager
import com.htlac.hitv.feature.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var ntpManager: NtpManager

    // 新增：把本地配置大管家请进来，用来读取有没有存过地址
    @Inject
    lateinit var settingsManager: SettingsManager

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
                val navController = rememberNavController()

                // 把大管家的起点改成一个隐藏的 "splash"
                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {

                    // ================= 启动路由分发页 =================
                    composable("splash") {
                        // 读取本地保存的 IPTV 地址（initial=null 表示还在读取的这几毫秒中）
                        val savedUrl by settingsManager.iptvUrlFlow.collectAsState(initial = null)

                        LaunchedEffect(savedUrl) {
                            if (savedUrl != null) {
                                if (savedUrl!!.isNotBlank()) {
                                    // 智能分发：有数据，直接跳去播放页！
                                    navController.navigate("player") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                } else {
                                    // 智能分发：没数据，老老实实去设置页
                                    navController.navigate("settings") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            }
                        }

                        // 读取这几毫秒时的纯黑占位背景，完美无缝衔接
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    }

                    // ================= 配置页 =================
                    composable("settings") {
                        SettingsScreen(
                            onNavigateToPlayer = {
                                navController.navigate("player") {
                                    popUpTo("settings") { inclusive = true }
                                }
                            }
                        )
                    }

                    // ================= 播放页 =================
                    composable("player") {
                        com.htlac.hitv.feature.player.PlayerScreen()
                    }
                }
            }
        }
    }
}