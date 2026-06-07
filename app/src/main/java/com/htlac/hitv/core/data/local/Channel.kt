package com.htlac.hitv.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// @Entity 代表这是一张数据库表，表名叫 channels
@Entity(
    tableName = "channels",
    indices = [Index(value = ["name"])] // 保留按名字搜索的索引，加速查询
)
data class Channel(
    @PrimaryKey val urlHash: String, // 【重构核心】：废弃自增ID，使用播放链接的 MD5 作为绝对主键
    val name: String,         // 频道名称，比如 "CCTV-1"
    val url: String,          // 播放链接
    val groupName: String,    // 分类名称，比如 "央视", "卫视"
    val tvgId: String,        // 用于匹配 EPG 的 ID
    val tvgName: String,      // 用于匹配 EPG 的名字
    val logo: String          // 频道台标的图片链接
)