package com.htlac.hitv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HitvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 这里以后会用来初始化一些全局的组件，比如日志、图片加载器缓存等
    }
}