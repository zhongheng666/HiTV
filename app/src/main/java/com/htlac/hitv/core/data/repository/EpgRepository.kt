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
    private val TAG = "HiTV_Debug"

    private val _epgSyncEvent = MutableSharedFlow<String>()
    val epgSyncEvent = _epgSyncEvent.asSharedFlow()

    fun getProgramsForChannel(tvgId: String, channelName: String): Flow<List<EpgProgram>> {
        val accurateCurrentTime = ntpManager.getCurrentTime()
        return epgDao.getProgramsForChannel(tvgId, channelName, accurateCurrentTime)
    }

    suspend fun syncEpgFromUrl(epgUrl: String) {
        Log.i(TAG, "📡 EPG 节目单开始在后台下载解析: $epgUrl")
        _epgSyncEvent.emit("📡 EPG 节目单开始在后台下载解析...")

        epgDao.deleteAll()
        var totalPrograms = 0

        try {
            epgParser.parse(epgUrl)
                .catch { e ->
                    Log.e(TAG, "❌ EPG 解析失败: ${e.message}", e)
                    _epgSyncEvent.emit("❌ EPG 解析失败: ${e.message}")
                }
                .collect { batchPrograms ->
                    epgDao.insertPrograms(batchPrograms)
                    totalPrograms += batchPrograms.size
                }

            // 【核心：将 EPG 获取结果强力输出到 Logcat】
            Log.i(TAG, "✅ EPG 更新成功！后台共解析入库 $totalPrograms 条节目。")
            _epgSyncEvent.emit("✅ EPG 更新成功！共解析 $totalPrograms 条节目。")

        } catch (e: Exception) {
            Log.e(TAG, "❌ EPG 更新发生异常", e)
            _epgSyncEvent.emit("❌ EPG 更新发生异常")
        }
    }
}