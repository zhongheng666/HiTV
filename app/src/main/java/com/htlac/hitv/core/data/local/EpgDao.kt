package com.htlac.hitv.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    // 核心逻辑：查找某个频道在“当前时间”之后的节目单，并按时间先后排序。
    @Query("SELECT * FROM epg_programs WHERE (tvgId = :tvgId OR channelName = :channelName) AND endTime > :currentTime ORDER BY startTime ASC")
    fun getProgramsForChannel(tvgId: String, channelName: String, currentTime: Long): Flow<List<EpgProgram>>

    // 批量插入节目单
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgram>)

    // 清空节目单表
    @Query("DELETE FROM epg_programs")
    suspend fun deleteAll()
}