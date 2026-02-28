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

                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {
                    composable("splash") {
                        val savedUrl by settingsManager.iptvUrlFlow.collectAsState(initial = null)
                        LaunchedEffect(savedUrl) {
                            if (savedUrl != null) {
                                if (savedUrl!!.isNotBlank()) {
                                    navController.navigate("player") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                } else {
                                    navController.navigate("settings") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    }

                    composable("settings") {
                        SettingsScreen(
                            onNavigateToPlayer = {
                                navController.navigate("player") {
                                    // 扫码配置完后，清空栈跳到播放页
                                    popUpTo("settings") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("player") {
                        // 【核心修改：传入跳转设置页的回调】
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