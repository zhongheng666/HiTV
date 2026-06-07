package com.htlac.hitv.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "epg_programs",
    foreignKeys = [
        ForeignKey(
            entity = Channel::class,
            parentColumns = ["urlHash"], // 频道表的主键
            childColumns = ["channelHash"], // 节目单表的关联键
            onDelete = ForeignKey.CASCADE // 核心联动：频道被删，对应的节目单瞬间自动清理
        )
    ],
    // 【重构核心】：复合索引加速。优先靠 channelHash 找，找不到再靠名字和 tvgId 兜底
    indices = [
        Index(value = ["channelHash"]),
        Index(value = ["channelName"]),
        Index(value = ["tvgId"])
    ]
)
data class EpgProgram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // SQLite 推荐自增主键用 Long
    val channelHash: String,  // 强关联 channels 表的 urlHash
    val tvgId: String,        // 匹配频道的 tvg-id
    val channelName: String,  // 如果 tvg-id 没有，就用频道名字模糊匹配
    val title: String,        // 节目名称，比如 "新闻联播"
    val startTime: Long,      // 开始时间戳（毫秒）
    val endTime: Long,        // 结束时间戳（毫秒）
    val description: String   // 节目简介
)