package com.htlac.hitv.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// 【核心修复】：将 version = 1 升级为 version = 2
@Database(entities = [Channel::class, EpgProgram::class], version = 2, exportSchema = false)
abstract class HitvDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun epgDao(): EpgDao
}