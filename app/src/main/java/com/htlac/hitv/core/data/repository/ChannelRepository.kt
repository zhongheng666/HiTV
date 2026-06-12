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
    fun getAllChannels(): Flow<List<Channel>> {
        return channelDao.getAllChannels()
    }

    suspend fun syncChannelsFromUrl(m3uUrl: String) {
        Log.d("ChannelRepository", "准备开始同步 IPTV 源: $m3uUrl")

        // 【核心修复】：不要一上来就清空数据库！
        // 建立一个内存缓冲区，收集所有解析出来的频道（几千个对象大概只占不到 2MB 内存，非常安全）
        val newChannels = mutableListOf<Channel>()

        m3uParser.parse(m3uUrl)
            .catch { e ->
                Log.e("ChannelRepository", "❌ 同步 M3U 失败（网络异常或格式错误），旧频道数据不受影响！", e)
                throw e // 把异常抛给 ViewModel 处理，中断执行
            }
            .collect { batchChannels ->
                newChannels.addAll(batchChannels)
            }

        // 【核心修复】：只有当网络成功且解析到了新数据时，才开启事务，一刀切地替换数据库
        if (newChannels.isNotEmpty()) {
            channelDao.replaceAll(newChannels)
            Log.d("ChannelRepository", "✅ IPTV 源同步完成，利用事务一次性刷入 ${newChannels.size} 个频道！")
        } else {
            Log.w("ChannelRepository", "⚠️ M3U 文件为空或解析不出有效频道，不破坏现有数据库。")
        }
    }
}