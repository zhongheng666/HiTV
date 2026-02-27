package com.htlac.hitv.core.data.repository

import android.util.Log
import com.htlac.hitv.core.data.local.Channel
import com.htlac.hitv.core.data.local.ChannelDao
import com.htlac.hitv.core.data.parser.M3uParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val m3uParser: M3uParser
) {
    // 供 UI 观察频道列表（当数据库有新数据时，UI会自动收到通知并刷新）
    fun getAllChannels(): Flow<List<Channel>> {
        return channelDao.getAllChannels()
    }

    // 下载并解析 M3U 链接，边解析边存入数据库
    suspend fun syncChannelsFromUrl(m3uUrl: String) {
        Log.d("ChannelRepository", "准备开始同步 IPTV 源: $m3uUrl")

        // 1. 先清空旧的频道数据
        channelDao.deleteAll()

        // 2. 调用解析器，收集流式发射过来的批次数据
        m3uParser.parse(m3uUrl)
            .catch { e ->
                // 捕获网络或解析错误
                Log.e("ChannelRepository", "同步 M3U 失败", e)
            }
            .collect { batchChannels ->
                // 3. 每收到一批 50 个频道，就塞进数据库
                channelDao.insertChannels(batchChannels)
                Log.d("ChannelRepository", "成功插入 ${batchChannels.size} 个频道到数据库")
            }

        Log.d("ChannelRepository", "IPTV 源同步完成！")
    }
}