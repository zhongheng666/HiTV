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
    private val TAG = "HiTV_Debug"
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault())

    // 【核心升级】：接收已有的频道字典（tvgIdToHash 和 nameToHash）
    fun parse(url: String, tvgIdToHash: Map<String, String>, nameToHash: Map<String, String>): Flow<List<EpgProgram>> = flow {
        Log.d(TAG, "🟢 [EPG解析器] 启动下载: $url")
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) throw Exception("EPG 下载失败，HTTP 状态码: ${response.code}")

        val inputStream = response.body?.byteStream() ?: return@flow

        // 【核心修复】：使用 .use {} 块包裹，确保发生异常时绝对安全地关闭底层 TCP 流
        inputStream.use { stream ->
            val parser = Xml.newPullParser()
            parser.setInput(stream, "UTF-8")

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
            var parsedCount = 0
            var droppedCount = 0 // 记录被拦截丢弃的垃圾数据

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
                                    if (currentChannelId.isNotEmpty() && name.isNotEmpty()) channelMap[currentChannelId] = name
                                }
                            }
                            "programme" -> {
                                currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                                currentStart = parseTime(parser.getAttributeValue(null, "start") ?: "")
                                currentEnd = parseTime(parser.getAttributeValue(null, "stop") ?: "")
                                currentTitle = ""
                                currentDesc = ""
                            }
                            "title" -> currentTitle = parser.nextText().trim()
                            "desc" -> currentDesc = parser.nextText().trim()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "channel") isParsingChannel = false
                        if (parser.name == "programme") {
                            val mappedName = channelMap[currentChannelId] ?: ""

                            // 【核心门卫拦截】：先用 tvgId 查字典，查不到再用名字查字典。如果字典里根本没这个台，直接丢弃！
                            val matchedHash = tvgIdToHash[currentChannelId] ?: nameToHash[mappedName]

                            if (matchedHash != null) {
                                parsedCount++
                                currentBatch.add(
                                    EpgProgram(
                                        channelHash = matchedHash, // 强行绑定外键
                                        tvgId = currentChannelId,
                                        channelName = mappedName,
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
                            } else {
                                droppedCount++
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (currentBatch.isNotEmpty()) emit(currentBatch.toList())
            Log.d(TAG, "🟢 [EPG解析器] 完成！提取有效节目: $parsedCount 条，拦截无关: $droppedCount 条")

        } // 离开这个大括号时，流会被自动安全地 close()

    }.flowOn(Dispatchers.IO)

    private fun parseTime(timeStr: String): Long {
        if (timeStr.isEmpty()) return 0L
        return try { dateFormat.parse(timeStr)?.time ?: 0L } catch (e: Exception) { 0L }
    }
}