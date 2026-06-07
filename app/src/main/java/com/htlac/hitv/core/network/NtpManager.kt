package com.htlac.hitv.core.network

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NtpManager @Inject constructor() {

    private var timeOffset: Long? = null
    private val ntpServer = "ntp.aliyun.com"

    // 【新增】：向 UI 层暴露 NTP 校准状态
    private val _isSyncedFlow = MutableStateFlow(false)
    val isSyncedFlow: StateFlow<Boolean> = _isSyncedFlow.asStateFlow()

    suspend fun syncTime() = withContext(Dispatchers.IO) {
        Log.d("NtpManager", "开始向 $ntpServer 请求网络时间...")
        val success = SntpClient.requestTime(ntpServer, 3000)

        if (success) {
            val now = SystemClock.elapsedRealtime()
            timeOffset = SntpClient.ntpTime - now
            _isSyncedFlow.value = true // 点亮绿灯
            Log.d("NtpManager", "NTP 时间同步成功！校准后的当前时间戳为: ${getCurrentTime()}")
        } else {
            _isSyncedFlow.value = false // 亮红灯
            Log.e("NtpManager", "NTP 时间同步失败，将回退使用系统本地时间。")
        }
    }

    fun getCurrentTime(): Long {
        return if (timeOffset != null) {
            SystemClock.elapsedRealtime() + timeOffset!!
        } else {
            System.currentTimeMillis()
        }
    }
}