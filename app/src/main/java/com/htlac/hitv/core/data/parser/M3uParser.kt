package com.htlac.hitv.core.data.parser

import com.htlac.hitv.core.data.local.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

class M3uParser @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    // 返回 Flow 流，按批次发射解析好的频道
    fun parse(url: String): Flow<List<Channel>> = flow {
        val request = Request.Builder().url(url).build()
        // 发起网络请求
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("M3U 下载失败，HTTP 状态码: ${response.code}")
        }

        // charStream().buffered() 可以一行一行读取，不占用大量内存
        response.body?.charStream()?.buffered()?.use { reader ->
            var line: String?
            var currentChannelName = ""
            var currentTvgId = ""
            var currentTvgName = ""
            var currentLogo = ""
            var currentGroup = "未分类"

            val batchSize = 50 // 每解析 50 个打包发射一次
            val currentBatch = mutableListOf<Channel>()

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line!!.trim()
                if (currentLine.isEmpty()) continue

                if (currentLine.startsWith("#EXTINF:")) {
                    // 解析标签属性 (如 tvg-id="cctv1" tvg-name="CCTV-1" tvg-logo="http...")
                    currentTvgId = extractAttribute(currentLine, "tvg-id")
                    currentTvgName = extractAttribute(currentLine, "tvg-name")
                    currentLogo = extractAttribute(currentLine, "tvg-logo")
                    currentGroup = extractAttribute(currentLine, "group-title").ifEmpty { "全部频道" }

                    // 解析频道名称（最后一个逗号后面的内容）
                    val commaIndex = currentLine.lastIndexOf(',')
                    currentChannelName = if (commaIndex != -1 && commaIndex < currentLine.length - 1) {
                        currentLine.substring(commaIndex + 1).trim()
                    } else {
                        "未知频道"
                    }
                } else if (!currentLine.startsWith("#")) {
                    // 不是以 # 开头的行，就是播放链接！
                    // 根据设计文档，这里可能直接是指向 nginx 的地址，直接存下来即可，播放器会自动处理302
                    val channel = Channel(
                        name = currentChannelName.ifEmpty { "未知频道" },
                        url = currentLine,
                        groupName = currentGroup,
                        tvgId = currentTvgId,
                        tvgName = currentTvgName.ifEmpty { currentChannelName },
                        logo = currentLogo
                    )
                    currentBatch.add(channel)

                    // 攒够了 50 个，就顺着 Flow 流发射出去，并清空篮子继续装
                    if (currentBatch.size >= batchSize) {
                        emit(currentBatch.toList())
                        currentBatch.clear()
                    }

                    // 重置临时变量，准备解析下一个频道
                    currentChannelName = ""
                    currentTvgId = ""
                    currentTvgName = ""
                    currentLogo = ""
                    currentGroup = "未分类"
                }
            }

            // 循环结束，把篮子里剩下的（不足50个的频道）也发射出去
            if (currentBatch.isNotEmpty()) {
                emit(currentBatch.toList())
            }
        }
    }.flowOn(Dispatchers.IO) // 强制在 IO 线程池执行耗时操作，绝对不卡主线程

    // 用正则表达式提取 M3U 标签里的属性值
    private fun extractAttribute(line: String, attribute: String): String {
        val regex = Regex("""$attribute="([^"]*)"""")
        val matchResult = regex.find(line)
        return matchResult?.groups?.get(1)?.value ?: ""
    }
}