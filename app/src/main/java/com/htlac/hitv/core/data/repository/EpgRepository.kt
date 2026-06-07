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

        epgDao.deleteAll()
        var totalPrograms = 0

        try {
            // 【核心升级】：在解析 EPG 之前，提取出现有的频道，做成两本字典
            val allChannels = channelDao.getAllChannels().firstOrNull() ?: emptyList()
            val tvgIdToHash = allChannels.filter { it.tvgId.isNotEmpty() }.associateBy({ it.tvgId }, { it.urlHash })
            val nameToHash = allChannels.associateBy({ it.name }, { it.urlHash })

            // 把字典传给解析器，让它进行精准拦截过滤
            epgParser.parse(epgUrl, tvgIdToHash, nameToHash)
                .catch { e ->
                    Log.e(TAG, "❌ [Repository] EPG 解析失败: ${e.message}", e)
                    _epgSyncEvent.emit("❌ EPG 解析失败")
                }
                .collect { batchPrograms ->
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