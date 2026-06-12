package com.htlac.hitv.core.data.repository

import android.util.Log
import com.htlac.hitv.core.data.local.ChannelDao
import com.htlac.hitv.core.data.local.EpgDao
import com.htlac.hitv.core.data.local.EpgProgram
import com.htlac.hitv.core.data.parser.EpgParser
import com.htlac.hitv.core.network.NtpManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepository @Inject constructor(
    private val epgDao: EpgDao,
    private val channelDao: ChannelDao, // 注入频道Dao，用来做字典
    private val epgParser: EpgParser,
    private val ntpManager: NtpManager
) {
    private val TAG = "HiTV_Debug"
    private val _epgSyncEvent = MutableSharedFlow<String>()
    val epgSyncEvent = _epgSyncEvent.asSharedFlow()

    fun getProgramsForChannel(channelHash: String): Flow<List<EpgProgram>> {
        val accurateCurrentTime = ntpManager.getCurrentTime()
        return epgDao.getProgramsForChannel(channelHash, accurateCurrentTime)
    }

    suspend fun getProgramCount(): Int = epgDao.getProgramCount()

    suspend fun syncEpgFromUrl(epgUrl: String) {
        Log.i(TAG, "📡 EPG 节目单开始在后台下载解析...")
        _epgSyncEvent.emit("📡 EPG 开始下载解析...")

        var totalPrograms = 0
        var isOldDataCleared = false // 【防御编程】：增加安全锁

        try {
            val allChannels = channelDao.getAllChannels().firstOrNull() ?: emptyList()
            val tvgIdToHash = allChannels.filter { it.tvgId.isNotEmpty() }.associateBy({ it.tvgId }, { it.urlHash })
            val nameToHash = allChannels.associateBy({ it.name }, { it.urlHash })

            epgParser.parse(epgUrl, tvgIdToHash, nameToHash)
                .catch { e ->
                    Log.e(TAG, "❌ [Repository] EPG 解析失败: ${e.message}", e)
                    // 如果网络失败，旧数据根本没被删掉，用户依然可以看旧的节目单，体验降级但不断层！
                    _epgSyncEvent.emit("❌ EPG 解析失败")
                }
                .collect { batchPrograms ->
                    // 【核心修复】：只在确实拿到新数据的第一时间，才安全地销毁旧数据
                    if (!isOldDataCleared && batchPrograms.isNotEmpty()) {
                        epgDao.deleteAll()
                        isOldDataCleared = true
                    }
                    epgDao.insertPrograms(batchPrograms)
                    totalPrograms += batchPrograms.size
                }

            val dbCount = epgDao.getProgramCount()
            Log.i(TAG, "✅ [Repository] EPG 更新成功！实存 $dbCount 条")
            _epgSyncEvent.emit("✅ EPG 更新成功！(实存 $dbCount 条)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Repository] EPG 严重异常", e)
            _epgSyncEvent.emit("❌ EPG 严重异常")
        }
    }
}