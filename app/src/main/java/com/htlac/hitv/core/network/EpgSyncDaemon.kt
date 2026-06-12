package com.htlac.hitv.core.network

import android.util.Log
import com.htlac.hitv.core.data.datastore.SettingsManager
import com.htlac.hitv.core.data.repository.EpgRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgSyncDaemon @Inject constructor(
    private val epgRepository: EpgRepository,
    private val settingsManager: SettingsManager
) {
    private val TAG = "HiTV_Daemon"
    private val SYNC_INTERVAL = 12 * 60 * 60 * 1000L

    private var isSyncing = false
    private var lastSyncAttemptTime = 0L
    private val COOLDOWN_MS = 60_000L

    // 【深度修复】：建立自己专属的永生作用域，不再依赖外部的 UI 生命周期
    private val daemonScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        daemonScope.launch {
            Log.d(TAG, "🛡️ [EPG 守护进程] 已在后台苏醒，独立接管生命周期")

            delay(10000)
            triggerSync("开机自检")

            while (isActive) {
                delay(SYNC_INTERVAL)
                triggerSync("定时器自动轮询")
            }
        }
    }

    suspend fun triggerSync(reason: String) {
        if (isSyncing) return

        val now = System.currentTimeMillis()
        if (now - lastSyncAttemptTime < COOLDOWN_MS) {
            Log.w(TAG, "⏳ [EPG 守护进程] 距上次拉取不足60秒，处于冷却中，忽略触发: $reason")
            return
        }

        val epgUrl = settingsManager.epgUrlFlow.firstOrNull()
        if (epgUrl.isNullOrBlank()) return

        Log.i(TAG, "🔄 [EPG 守护进程] 触发静默同步，触发原因: $reason")
        isSyncing = true
        lastSyncAttemptTime = now

        try {
            epgRepository.syncEpgFromUrl(epgUrl)
        } catch (e: Exception) {
            Log.e(TAG, "❌ [EPG 守护进程] 静默同步失败", e)
        } finally {
            isSyncing = false
        }
    }
}