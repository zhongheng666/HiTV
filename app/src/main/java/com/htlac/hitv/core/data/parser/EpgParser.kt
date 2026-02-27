package com.htlac.hitv.core.data.parser

import android.util.Log
import android.util.Xml
import com.htlac.hitv.core.data.local.EpgProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class EpgParser @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    // XMLTV 的标准时间格式通常是 "20240227120000 +0800"
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault())

    fun parse(url: String): Flow<List<EpgProgram>> = flow {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("EPG 下载失败，HTTP 状态码: ${response.code}")
        }

        val inputStream = response.body?.byteStream() ?: return@flow

        // 使用 Android 原生的轻量级 XmlPullParser，专治低端机内存不足
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        val batchSize = 1000 // 节目单数量巨大，每攒够 1000 条才写入一次数据库
        val currentBatch = mutableListOf<EpgProgram>()

        var currentChannelId = ""
        var currentTitle = ""
        var currentStart = 0L
        var currentEnd = 0L
        var currentDesc = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "programme" -> {
                            // 读取 <programme start="xxx" stop="xxx" channel="xxx">
                            currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                            val startStr = parser.getAttributeValue(null, "start") ?: ""
                            val stopStr = parser.getAttributeValue(null, "stop") ?: ""

                            currentStart = parseTime(startStr)
                            currentEnd = parseTime(stopStr)

                            // 重置内容，准备读取标签内部的文本
                            currentTitle = ""
                            currentDesc = ""
                        }
                        "title" -> {
                            currentTitle = parser.nextText()
                        }
                        "desc" -> {
                            currentDesc = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    // 一个节目节点解析结束，打包装车
                    if (parser.name == "programme") {
                        currentBatch.add(
                            EpgProgram(
                                tvgId = currentChannelId,
                                channelName = "", // EPG文件里一般只有id，没有名字，留空
                                title = currentTitle,
                                startTime = currentStart,
                                endTime = currentEnd,
                                description = currentDesc
                            )
                        )

                        // 攒够了 1000 个，发射流！
                        if (currentBatch.size >= batchSize) {
                            emit(currentBatch.toList())
                            currentBatch.clear()
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        // 循环结束，把剩下的尾巴也发射出去
        if (currentBatch.isNotEmpty()) {
            emit(currentBatch.toList())
        }

        // 别忘了关水龙头
        inputStream.close()
    }.flowOn(Dispatchers.IO) // 强制在 IO 线程执行

    private fun parseTime(timeStr: String): Long {
        if (timeStr.isEmpty()) return 0L
        return try {
            dateFormat.parse(timeStr)?.time ?: 0L
        } catch (e: Exception) {
            Log.e("EpgParser", "时间解析失败: $timeStr")
            0L
        }
    }
}