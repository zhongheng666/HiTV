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
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault())

    fun parse(url: String): Flow<List<EpgProgram>> = flow {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("EPG 下载失败，HTTP 状态码: ${response.code}")
        }

        val inputStream = response.body?.byteStream() ?: return@flow
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        val batchSize = 1000
        val currentBatch = mutableListOf<EpgProgram>()

        // 【核心魔法：建立频道 ID 和 名称的映射字典】
        val channelMap = mutableMapOf<String, String>()

        var currentChannelId = ""
        var currentTitle = ""
        var currentStart = 0L
        var currentEnd = 0L
        var currentDesc = ""

        var isParsingChannel = false
        var currentChannelMapId = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            isParsingChannel = true
                            currentChannelMapId = parser.getAttributeValue(null, "id") ?: ""
                        }
                        "display-name" -> {
                            // 记下频道名称，比如把 "1" 映射为 "CCTV-1"
                            if (isParsingChannel) {
                                val name = parser.nextText()
                                if (currentChannelMapId.isNotEmpty()) {
                                    channelMap[currentChannelMapId] = name
                                }
                            }
                        }
                        "programme" -> {
                            currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                            val startStr = parser.getAttributeValue(null, "start") ?: ""
                            val stopStr = parser.getAttributeValue(null, "stop") ?: ""
                            currentStart = parseTime(startStr)
                            currentEnd = parseTime(stopStr)
                            currentTitle = ""
                            currentDesc = ""
                        }
                        "title" -> if (!isParsingChannel) currentTitle = parser.nextText()
                        "desc" -> if (!isParsingChannel) currentDesc = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "channel") {
                        isParsingChannel = false
                    }
                    if (parser.name == "programme") {
                        // 【绝杀】：从字典里找出对应的中文频道名，一起存入数据库！
                        val mappedName = channelMap[currentChannelId] ?: ""

                        currentBatch.add(
                            EpgProgram(
                                tvgId = currentChannelId,
                                channelName = mappedName, // 赋值真正的频道名称！
                                title = currentTitle,
                                startTime = currentStart,
                                endTime = currentEnd,
                                description = currentDesc
                            )
                        )

                        if (currentBatch.size >= batchSize) {
                            emit(currentBatch.toList())
                            currentBatch.clear()
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (currentBatch.isNotEmpty()) emit(currentBatch.toList())
        inputStream.close()
    }.flowOn(Dispatchers.IO)

    private fun parseTime(timeStr: String): Long {
        if (timeStr.isEmpty()) return 0L
        return try {
            dateFormat.parse(timeStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}