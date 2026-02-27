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
@InstallIn(SingletonComponent::class) // 这里的组件和 App 的生命周期一样长
object DatabaseModule {

    @Provides
    @Singleton // 保证整个 App 只有一个数据库实例
    fun provideDatabase(@ApplicationContext context: Context): HitvDatabase {
        return Room.databaseBuilder(
            context,
            HitvDatabase::class.java,
            "hitv_database.db" // 数据库文件将保存在盒子里，名字叫这个
        ).build()
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