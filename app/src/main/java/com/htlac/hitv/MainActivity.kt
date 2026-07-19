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
import com.htlac.hitv.core.network.EpgSyncDaemon
import com.htlac.hitv.core.network.NtpManager
import com.htlac.hitv.feature.settings.SettingsScreen
import com.htlac.hitv.feature.splash.SplashScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var ntpManager: NtpManager
    @Inject lateinit var settingsManager: SettingsManager
    @Inject lateinit var channelRepository: ChannelRepository
    @Inject lateinit var epgSyncDaemon: EpgSyncDaemon // 【注入守护进程】

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            // 1. 同步阿里云时间
            ntpManager.syncTime()
            // 2. 唤醒 EPG 守护进程
            epgSyncDaemon.start()
        }

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                val navController = rememberNavController()

                NavHost(navController, startDestination = "splash") {
                    composable("splash") {
                        SplashScreen(
                            onAnimationFinished = {
                                lifecycleScope.launch {
                                    val url = settingsManager.iptvUrlFlow.firstOrNull()
                                    if (!url.isNullOrBlank()) {
                                        val channels = channelRepository.getAllChannels().firstOrNull()
                                        if (!channels.isNullOrEmpty()) {
                                            navController.navigate("player") {
                                                popUpTo("splash") { inclusive = true }
                                            }
                                        } else {
                                            navController.navigate("settings") { popUpTo("splash") { inclusive = true } }
                                        }
                                    } else {
                                        navController.navigate("settings") { popUpTo("splash") { inclusive = true } }
                                    }
                                }
                            }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            onNavigateToPlayer = {
                                navController.navigate("player") { popUpTo("settings") { inclusive = true } }
                            }
                        )
                    }

                    composable("player") {
                        com.htlac.hitv.core.player.PlayerScreen(
                            onNavigateToSettings = {
                                navController.navigate("settings") { popUpTo("player") { inclusive = true } }
                            }
                        )
                    }
                }
            }
        }
    }
}