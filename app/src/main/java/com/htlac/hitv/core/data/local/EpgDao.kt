package com.htlac.hitv.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    // 【核心升级】：丢掉以前缓慢的名字匹配，直接拿 Hash 去找，O(1) 极速出结果！
    @Query("SELECT * FROM epg_programs WHERE channelHash = :channelHash AND endTime > :currentTime ORDER BY startTime ASC")
    fun getProgramsForChannel(channelHash: String, currentTime: Long): Flow<List<EpgProgram>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgram>)

    @Query("DELETE FROM epg_programs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM epg_programs")
    suspend fun getProgramCount(): Int
}