package com.htlac.hitv.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// 指定包含哪些表，以及数据库版本号
@Database(entities = [Channel::class, EpgProgram::class], version = 1, exportSchema = false)
abstract class HitvDatabase : RoomDatabase() {
    // 暴露出我们刚才写的 Dao
    abstract fun channelDao(): ChannelDao
    abstract fun epgDao(): EpgDao
}