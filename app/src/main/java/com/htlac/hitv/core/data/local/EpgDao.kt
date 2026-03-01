package com.htlac.hitv.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    // 保持您的严格匹配逻辑不变
    @Query("SELECT * FROM epg_programs WHERE (tvgId = :tvgId OR channelName = :channelName) AND endTime > :currentTime ORDER BY startTime ASC")
    fun getProgramsForChannel(tvgId: String, channelName: String, currentTime: Long): Flow<List<EpgProgram>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgram>)

    @Query("DELETE FROM epg_programs")
    suspend fun deleteAll()

    // 【新增探针】：随时查验数据库真实存活条数
    @Query("SELECT COUNT(*) FROM epg_programs")
    suspend fun getProgramCount(): Int
}