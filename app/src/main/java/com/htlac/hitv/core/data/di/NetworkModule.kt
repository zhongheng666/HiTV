package com.htlac.hitv.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // 配置全局的网络请求客户端
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS) // 连接超时 10 秒
            .readTimeout(30, TimeUnit.SECONDS)    // 读取超时 30 秒
            // 允许跨协议重定向（非常关键，应对 Nginx 的 302 跳转）
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}