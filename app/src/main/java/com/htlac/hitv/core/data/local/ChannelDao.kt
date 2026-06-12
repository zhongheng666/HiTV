package com.htlac.hitv.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels")
    fun getAllChannels(): Flow<List<Channel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<Channel>)

    @Query("DELETE FROM channels")
    suspend fun deleteAll()

    // 【核心修复】：使用 @Transaction 开启事务。
    // 这保证了“清空旧数据+插入新数据”是一个原子动作，Room 的 Flow 只会向 UI 发射【1次】更新，彻底消灭 UI 卡顿！
    @Transaction
    suspend fun replaceAll(channels: List<Channel>) {
        deleteAll()
        insertChannels(channels)
    }
}