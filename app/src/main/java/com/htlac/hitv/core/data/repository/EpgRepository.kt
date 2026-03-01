package com.htlac.hitv.core.data.repository

import android.util.Log
import com.htlac.hitv.core.data.local.EpgDao
import com.htlac.hitv.core.data.local.EpgProgram
import com.htlac.hitv.core.data.parser.EpgParser
import com.htlac.hitv.core.network.NtpManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepository @Inject constructor(
    private val epgDao: EpgDao,
    private val epgParser: EpgParser,
    private val ntpManager: NtpManager
) {
    // 【统一 Tag】
    private val TAG = "HiTV_Debug"

    private val _epgSyncEvent = MutableSharedFlow<String>()
    val epgSyncEvent = _epgSyncEvent.asSharedFlow()

    fun getProgramsForChannel(tvgId: String, channelName: String): Flow<List<EpgProgram>> {
        val accurateCurrentTime = ntpManager.getCurrentTime()
        Log.d(TAG, "🔎 [SQL查询命令] 正在数据库检索 -> 目标 tvgId=[$tvgId], 目标 channelName=[$channelName], 当前NTP时间阈值=[$accurateCurrentTime]")
        return epgDao.getProgramsForChannel(tvgId, channelName, accurateCurrentTime)
    }

    suspend fun getProgramCount(): Int = epgDao.getProgramCount()

    suspend fun syncEpgFromUrl(epgUrl: String) {
        Log.i(TAG, "📡 EPG 节目单开始在后台下载解析: $epgUrl")
        _epgSyncEvent.emit("📡 EPG 开始下载解析...")

        epgDao.deleteAll()
        var totalPrograms = 0

        try {
            epgParser.parse(epgUrl)
                .catch { e ->
                    Log.e(TAG, "❌ [Repository] EPG 解析流断裂: ${e.message}", e)
                    _epgSyncEvent.emit("❌ EPG 解析失败: ${e.message}")
                }
                .collect { batchPrograms ->
                    epgDao.insertPrograms(batchPrograms)
                    totalPrograms += batchPrograms.size
                }

            val dbCount = epgDao.getProgramCount()
            Log.i(TAG, "✅ [Repository] EPG 更新成功！流解析了 $totalPrograms 条，数据库实际写入了 $dbCount 条！")
            _epgSyncEvent.emit("✅ EPG 更新成功！(实存 $dbCount 条)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Repository] EPG 严重异常", e)
            _epgSyncEvent.emit("❌ EPG 严重异常: ${e.message}")
        }
    }
}