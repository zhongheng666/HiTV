package com.htlac.hitv.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 为 EPG 节目单建表。
// indices 的作用是建索引，因为节目单数据可能多达几万条，建索引可以极快地根据频道查询到它的节目。
@Entity(
    tableName = "epg_programs",
    indices = [Index(value = ["channelName"]), Index(value = ["tvgId"])]
)
data class EpgProgram(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tvgId: String,        // 匹配频道的 tvg-id
    val channelName: String,  // 如果 tvg-id 没有，就用频道名字模糊匹配 (按照设计文档要求)
    val title: String,        // 节目名称，比如 "新闻联播"
    val startTime: Long,      // 开始时间戳（毫秒）
    val endTime: Long,        // 结束时间戳（毫秒）
    val description: String   // 节目简介
)