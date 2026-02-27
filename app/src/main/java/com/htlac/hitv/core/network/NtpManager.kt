package com.htlac.hitv.core.network

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // 告诉 Hilt，这个管理器在整个 App 生命周期里只存在一份（单例）
class NtpManager @Inject constructor() {

    // 记录：网络真实时间与本地开机运行时间（elapsedRealtime）的差值
    private var timeOffset: Long? = null

    // 设计文档中指定的阿里云 NTP 服务器
    private val ntpServer = "ntp.aliyun.com"

    /**
     * 发起网络请求同步时间。
     * 这是一个耗时操作，所以用 suspend 挂起函数，并切换到 IO 线程池执行。
     */
    suspend fun syncTime() = withContext(Dispatchers.IO) {
        Log.d("NtpManager", "开始向 $ntpServer 请求网络时间...")

        // 设置 3000 毫秒（3秒）超时
        val success = SntpClient.requestTime(ntpServer, 3000)

        if (success) {
            // elapsedRealtime() 是手机/盒子开机到现在走过的毫秒数，它不受用户手动改时间的影响
            val now = SystemClock.elapsedRealtime()
            // 计算时间差
            timeOffset = SntpClient.ntpTime - now
            Log.d("NtpManager", "NTP 时间同步成功！校准后的当前时间戳为: ${getCurrentTime()}")
        } else {
            Log.e("NtpManager", "NTP 时间同步失败，将回退使用系统本地时间。请检查盒子是否联网。")
        }
    }

    /**
     * 获取当前绝对准确的时间（毫秒）
     * 其他模块（比如 EPG 解析模块）以后直接调用这个方法拿时间即可
     */
    fun getCurrentTime(): Long {
        return if (timeOffset != null) {
            // 准确时间 = 当前开机运行时间 + 时间差
            SystemClock.elapsedRealtime() + timeOffset!!
        } else {
            // 如果没同步成功，只能退而求其次用系统本地时间
            System.currentTimeMillis()
        }
    }
}