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

        if (!response.isSuccessful) throw Exception("M3U 下载失败，状态码: ${response.code}")

        response.body?.charStream()?.buffered()?.use { reader ->
            var line: String?
            var currentChannelName = ""
            var currentTvgId = ""
            var currentTvgName = ""
            var currentLogo = ""
            var currentGroup = "未分类" // 默认分类

            val batchSize = 50
            val currentBatch = mutableListOf<Channel>()

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line!!.trim()
                if (currentLine.isEmpty()) continue

                if (currentLine.startsWith("#EXTINF:")) {
                    currentTvgId = extractAttribute(currentLine, "tvg-id")
                    currentTvgName = extractAttribute(currentLine, "tvg-name")
                    currentLogo = extractAttribute(currentLine, "tvg-logo")

                    val groupTitle = extractAttribute(currentLine, "group-title")
                    if (groupTitle.isNotEmpty()) currentGroup = groupTitle

                    val commaIndex = currentLine.indexOf(',')
                    currentChannelName = if (commaIndex != -1 && commaIndex < currentLine.length - 1) {
                        currentLine.substring(commaIndex + 1).trim()
                    } else {
                        "未知频道"
                    }
                }
                // 【核心修复】：兼容国内绝大多数源使用的 #EXTGRP 独立分类标签！
                else if (currentLine.startsWith("#EXTGRP:")) {
                    currentGroup = currentLine.substringAfter("#EXTGRP:").trim()
                }
                else if (!currentLine.startsWith("#")) {
                    val channel = Channel(
                        urlHash = HashUtil.md5(currentLine),
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

                    // 重置下一行的临时变量（但不能重置 currentGroup，因为有些源是一个分类管下面几十个台）
                    currentChannelName = ""
                    currentTvgId = ""
                    currentTvgName = ""
                    currentLogo = ""
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