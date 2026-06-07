package com.htlac.hitv.core.network

import android.util.Log
import com.htlac.hitv.core.data.datastore.SettingsManager
import com.htlac.hitv.core.data.repository.EpgRepository
import kotlinx.coroutines.CoroutineScope
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
    private val SYNC_INTERVAL = 12 * 60 * 60 * 1000L // 12小时轮询一次

    private var isSyncing = false

    fun start(scope: CoroutineScope) {
        scope.launch {
            Log.d(TAG, "🛡️ [EPG 守护进程] 已在后台苏醒")

            // 1. 开机自检：延迟 10 秒等网络彻底连上，然后静默拉取一次
            delay(10000)
            triggerSync("开机自检")

            // 2. 定时轮询：只要 App 活着，每 12 小时自动拉一次
            while (isActive) {
                delay(SYNC_INTERVAL)
                triggerSync("定时器自动轮询")
            }
        }
    }

    suspend fun triggerSync(reason: String) {
        if (isSyncing) return // 防抖

        val epgUrl = settingsManager.epgUrlFlow.firstOrNull()
        if (epgUrl.isNullOrBlank()) return

        Log.i(TAG, "🔄 [EPG 守护进程] 触发静默同步，触发原因: $reason")
        isSyncing = true
        try {
            epgRepository.syncEpgFromUrl(epgUrl)
        } catch (e: Exception) {
            Log.e(TAG, "❌ [EPG 守护进程] 静默同步失败", e)
        } finally {
            isSyncing = false
        }
    }
}