package com.htlac.hitv.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    // 获取所有频道。返回 Flow 是为了让 UI 能够自动感知数据库的变化，数据库一更新，电视界面自动刷新
    @Query("SELECT * FROM channels")
    fun getAllChannels(): Flow<List<Channel>>

    // 批量插入频道。如果有冲突直接替换 (REPLACE)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<Channel>)

    // 清空频道表
    @Query("DELETE FROM channels")
    suspend fun deleteAll()
}