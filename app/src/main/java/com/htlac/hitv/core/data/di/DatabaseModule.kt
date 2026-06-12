package com.htlac.hitv.core.data.di

import android.content.Context
import androidx.room.Room
import com.htlac.hitv.core.data.local.ChannelDao
import com.htlac.hitv.core.data.local.EpgDao
import com.htlac.hitv.core.data.local.HitvDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HitvDatabase {
        return Room.databaseBuilder(
            context,
            HitvDatabase::class.java,
            "hitv_database.db"
        )
            // 【核心修复】：添加自动毁灭重建指令。
            // 当发现表结构变动或版本号升级时，直接清空旧库重建表，彻底杜绝 Schema 闪退！
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideChannelDao(database: HitvDatabase): ChannelDao {
        return database.channelDao()
    }

    @Provides
    fun provideEpgDao(database: HitvDatabase): EpgDao {
        return database.epgDao()
    }
}