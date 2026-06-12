package com.htlac.hitv.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "epg_programs",
    // 【核心修复】：彻底废除 foreignKeys 级联约束！M3U 和 EPG 各自独立更新，互不牵连误杀！
    indices = [
        Index(value = ["channelHash"]),
        Index(value = ["channelName"]),
        Index(value = ["tvgId"])
    ]
)
data class EpgProgram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelHash: String,
    val tvgId: String,
    val channelName: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val description: String
)