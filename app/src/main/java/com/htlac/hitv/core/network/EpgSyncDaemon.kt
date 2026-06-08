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

    // 【核心修复】：增加冷却时间锁
    private var lastSyncAttemptTime = 0L
    private val COOLDOWN_MS = 60_000L // 失败/成功后的冷却时间：60秒

    fun start(scope: CoroutineScope) {
        scope.launch {
            Log.d(TAG, "🛡️ [EPG 守护进程] 已在后台苏醒")

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

        // 【核心修复】：防连击冷却机制！距离上次尝试不足60秒时，直接装死忽略
        val now = System.currentTimeMillis()
        if (now - lastSyncAttemptTime < COOLDOWN_MS) {
            Log.w(TAG, "⏳ [EPG 守护进程] 距上次拉取不足60秒，处于冷却中，忽略触发: $reason")
            return
        }

        val epgUrl = settingsManager.epgUrlFlow.firstOrNull()
        if (epgUrl.isNullOrBlank()) return

        Log.i(TAG, "🔄 [EPG 守护进程] 触发静默同步，触发原因: $reason")
        isSyncing = true
        lastSyncAttemptTime = now // 记录本次尝试的时间戳

        try {
            epgRepository.syncEpgFromUrl(epgUrl)
        } catch (e: Exception) {
            Log.e(TAG, "❌ [EPG 守护进程] 静默同步失败", e)
        } finally {
            isSyncing = false
        }
    }
}