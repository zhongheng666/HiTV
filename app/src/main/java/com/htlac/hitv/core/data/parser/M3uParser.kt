package com.htlac.hitv.core.data.parser

import com.htlac.hitv.core.data.local.Channel
import com.htlac.hitv.core.utils.HashUtil
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
    fun parse(url: String): Flow<List<Channel>> = flow {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("M3U 下载失败，HTTP 状态码: ${response.code}")
        }

        response.body?.charStream()?.buffered()?.use { reader ->
            var line: String?
            var currentChannelName = ""
            var currentTvgId = ""
            var currentTvgName = ""
            var currentLogo = ""
            var currentGroup = "未分类"

            val batchSize = 50
            val currentBatch = mutableListOf<Channel>()

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line!!.trim()
                if (currentLine.isEmpty()) continue

                if (currentLine.startsWith("#EXTINF:")) {
                    currentTvgId = extractAttribute(currentLine, "tvg-id")
                    currentTvgName = extractAttribute(currentLine, "tvg-name")
                    currentLogo = extractAttribute(currentLine, "tvg-logo")
                    currentGroup = extractAttribute(currentLine, "group-title").ifEmpty { "全部频道" }
                    val commaIndex = currentLine.lastIndexOf(',')
                    currentChannelName = if (commaIndex != -1 && commaIndex < currentLine.length - 1) {
                        currentLine.substring(commaIndex + 1).trim()
                    } else {
                        "未知频道"
                    }
                } else if (!currentLine.startsWith("#")) {

                    val channel = Channel(
                        urlHash = HashUtil.md5(currentLine), // 【核心升级】：给频道打上 MD5 唯一钢印
                        name = currentChannelName.ifEmpty { "未知频道" },
                        url = currentLine,
                        groupName = currentGroup,
                        tvgId = currentTvgId,
                        tvgName = currentTvgName.ifEmpty { currentChannelName },
                        logo = currentLogo
                    )
                    currentBatch.add(channel)

                    if (currentBatch.size >= batchSize) {
                        emit(currentBatch.toList())
                        currentBatch.clear()
                    }
                    currentChannelName = ""
                    currentTvgId = ""
                    currentTvgName = ""
                    currentLogo = ""
                    currentGroup = "未分类"
                }
            }
            if (currentBatch.isNotEmpty()) emit(currentBatch.toList())
        }
    }.flowOn(Dispatchers.IO)

    private fun extractAttribute(line: String, attribute: String): String {
        val regex = Regex("""$attribute="([^"]*)"""")
        return regex.find(line)?.groups?.get(1)?.value ?: ""
    }
}