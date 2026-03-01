package com.htlac.hitv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.htlac.hitv.core.data.datastore.SettingsManager
import com.htlac.hitv.core.data.repository.ChannelRepository
import com.htlac.hitv.core.network.NtpManager
import com.htlac.hitv.feature.settings.SettingsScreen
import com.htlac.hitv.feature.splash.SplashScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var ntpManager: NtpManager

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var channelRepository: ChannelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // 【消灭原生冷启动黑屏】：在 Compose 渲染前，立刻将背景还原成纯黑，与我们的纯黑动画无缝衔接
        window.setBackgroundDrawableResource(android.R.color.black)
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            ntpManager.syncTime()
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                val navController = rememberNavController()

                NavHost(navController, startDestination = "splash") {

                    // 【核心替换】：注入酷炫的描边动画
                    composable("splash") {
                        SplashScreen(
                            onAnimationFinished = {
                                lifecycleScope.launch {
                                    // 动画播完后，再进行判断和跳转，优雅从容
                                    val url = settingsManager.iptvUrlFlow.firstOrNull()
                                    if (!url.isNullOrBlank()) {
                                        val channels = channelRepository.getAllChannels().firstOrNull()
                                        if (!channels.isNullOrEmpty()) {
                                            navController.navigate("player") {
                                                popUpTo("splash") { inclusive = true }
                                            }
                                        } else {
                                            navController.navigate("settings") {
                                                popUpTo("splash") { inclusive = true }
                                            }
                                        }
                                    } else {
                                        navController.navigate("settings") {
                                            popUpTo("splash") { inclusive = true }
                                        }
                                    }
                                }
                            }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            onNavigateToPlayer = {
                                navController.navigate("player") {
                                    popUpTo("settings") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("player") {
                        com.htlac.hitv.feature.player.PlayerScreen(
                            onNavigateToSettings = {
                                navController.navigate("settings") {
                                    popUpTo("player") { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}