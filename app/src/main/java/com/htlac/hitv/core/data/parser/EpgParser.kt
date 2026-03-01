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
    // 【统一 Tag】
    private val TAG = "HiTV_Debug"
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault())

    fun parse(url: String): Flow<List<EpgProgram>> = flow {
        Log.d(TAG, "🟢 [EPG解析器] 启动下载: $url")
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            Log.e(TAG, "❌ [EPG解析器] 下载失败，HTTP状态码: ${response.code}")
            throw Exception("EPG 下载失败，HTTP 状态码: ${response.code}")
        }

        val inputStream = response.body?.byteStream() ?: return@flow
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        val batchSize = 1000
        val currentBatch = mutableListOf<EpgProgram>()

        val channelMap = mutableMapOf<String, String>()

        var currentChannelId = ""
        var currentTitle = ""
        var currentStart = 0L
        var currentEnd = 0L
        var currentDesc = ""
        var isParsingChannel = false

        var parsedProgramCount = 0

        Log.d(TAG, "🟢 [EPG解析器] 开始读取 XML 节点...")

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            isParsingChannel = true
                            currentChannelId = parser.getAttributeValue(null, "id") ?: ""
                        }
                        "display-name" -> {
                            if (isParsingChannel) {
                                val name = parser.nextText().trim()
                                if (currentChannelId.isNotEmpty() && name.isNotEmpty()) {
                                    channelMap[currentChannelId] = name
                                    Log.d(TAG, "📺 [EPG频道字典] 提取成功: ID=[$currentChannelId] -> 名字=[$name]")
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
                        "title" -> currentTitle = parser.nextText().trim()
                        "desc" -> currentDesc = parser.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "channel") {
                        isParsingChannel = false
                    }
                    if (parser.name == "programme") {
                        val mappedName = channelMap[currentChannelId] ?: ""

                        // 抽样打印前 10 条节目单，检查时间戳是否解析出了 0
                        if (parsedProgramCount < 10) {
                            Log.d(TAG, "🎬 [EPG节目抽样] 关联ID=[$currentChannelId], 最终名=[$mappedName], 节目=[$currentTitle], 起=[$currentStart], 止=[$currentEnd]")
                        }
                        parsedProgramCount++

                        currentBatch.add(
                            EpgProgram(
                                tvgId = currentChannelId,
                                channelName = mappedName,
                                title = currentTitle,
                                startTime = currentStart,
                                endTime = currentEnd,
                                description = currentDesc
                            )
                        )

                        if (currentBatch.size >= batchSize) {
                            Log.d(TAG, "📦 [EPG解析器] 攒够 $batchSize 条，发射入库...")
                            emit(currentBatch.toList())
                            currentBatch.clear()
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (currentBatch.isNotEmpty()) {
            Log.d(TAG, "📦 [EPG解析器] 发射最后一批尾部数据: ${currentBatch.size} 条")
            emit(currentBatch.toList())
        }
        inputStream.close()
        Log.d(TAG, "🟢 [EPG解析器] 彻底完成！共提炼节目: $parsedProgramCount 条")
    }.flowOn(Dispatchers.IO)

    private fun parseTime(timeStr: String): Long {
        if (timeStr.isEmpty()) return 0L
        return try {
            dateFormat.parse(timeStr)?.time ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "❌ [EPG时间解析器] 严重错误！无法解析的时间格式: [$timeStr] -> 这将导致 endTime 为 0，在数据库中被抛弃！")
            0L
        }
    }
}