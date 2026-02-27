package com.htlac.hitv.core.data.repository

import android.util.Log
import com.htlac.hitv.core.data.local.EpgDao
import com.htlac.hitv.core.data.local.EpgProgram
import com.htlac.hitv.core.data.parser.EpgParser
import com.htlac.hitv.core.network.NtpManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepository @Inject constructor(
    private val epgDao: EpgDao,
    private val epgParser: EpgParser,
    private val ntpManager: NtpManager // 引入我们写的 NTP 校时大管家
) {
    /**
     * 获取指定频道当前的节目单。
     * UI 会调用这个方法，传入 tvgId 和 channelName（作为备用匹配）。
     */
    fun getProgramsForChannel(tvgId: String, channelName: String): Flow<List<EpgProgram>> {
        // 使用 NTP 校准后的绝对准确时间，防止盒子本地时间错误导致预告不准
        val accurateCurrentTime = ntpManager.getCurrentTime()
        return epgDao.getProgramsForChannel(tvgId, channelName, accurateCurrentTime)
    }

    /**
     * 下载并解析 EPG 链接，边解析边存入数据库
     */
    suspend fun syncEpgFromUrl(epgUrl: String) {
        Log.d("EpgRepository", "准备开始同步 EPG 节目单: $epgUrl")

        // 1. 同步前先清空旧的节目单缓存
        epgDao.deleteAll()

        var totalPrograms = 0

        // 2. 调用流式解析器
        epgParser.parse(epgUrl)
            .catch { e ->
                Log.e("EpgRepository", "同步 EPG 失败", e)
            }
            .collect { batchPrograms ->
                // 3. 每收到一批 1000 个节目，就写入数据库
                epgDao.insertPrograms(batchPrograms)
                totalPrograms += batchPrograms.size
                Log.d("EpgRepository", "已插入 $totalPrograms 条节目单数据...")
            }

        Log.d("EpgRepository", "EPG 同步彻底完成！共解析 $totalPrograms 条节目。")
    }
}