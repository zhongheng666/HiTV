package com.htlac.hitv.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            // 【网络加固】：激进的超时策略，绝不等待死链
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            // 【核心修复】：极大缩短 Keep-Alive 存活期，防止机顶盒底层 Socket 资源耗尽死锁
            .connectionPool(ConnectionPool(5, 5, TimeUnit.SECONDS))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}