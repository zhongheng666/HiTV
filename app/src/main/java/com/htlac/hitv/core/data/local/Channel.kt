package com.htlac.hitv.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// @Entity 代表这是一张数据库表，表名叫 channels
@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // 自增主键
    val name: String,         // 频道名称，比如 "CCTV-1"
    val url: String,          // 播放链接
    val groupName: String,    // 分类名称，比如 "央视", "卫视"
    val tvgId: String,        // 用于匹配 EPG 的 ID
    val tvgName: String,      // 用于匹配 EPG 的名字
    val logo: String          // 频道台标的图片链接
)