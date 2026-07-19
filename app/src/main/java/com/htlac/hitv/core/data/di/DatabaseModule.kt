package com.htlac.hitv.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    // 【修复】：编写正规的数据库迁移路径。即使结构没变只是为了刷版本号，也要给一个空实现
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 留空即可
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HitvDatabase {
        return Room.databaseBuilder(
            context,
            HitvDatabase::class.java,
            "hitv_database.db"
        )
            // 【修复】：移除 destructive 暴力重建，改用平滑迁移，保护用户数据
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideChannelDao(database: HitvDatabase): ChannelDao = database.channelDao()

    @Provides
    fun provideEpgDao(database: HitvDatabase): EpgDao = database.epgDao()
}