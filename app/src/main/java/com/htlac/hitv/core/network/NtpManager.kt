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

    private val _isSyncedFlow = MutableStateFlow(false)
    val isSyncedFlow: StateFlow<Boolean> = _isSyncedFlow.asStateFlow()

    suspend fun syncTime() = withContext(Dispatchers.IO) {
        Log.d("NtpManager", "开始向 $ntpServer 请求网络时间...")

        SntpClient.requestTime(ntpServer, 3000).onSuccess { networkTime ->
            val now = SystemClock.elapsedRealtime()
            timeOffset = networkTime - now
            _isSyncedFlow.value = true
            Log.d("NtpManager", "NTP 时间同步成功！校准后的当前时间戳为: ${getCurrentTime()}")
        }.onFailure {
            _isSyncedFlow.value = false
            Log.e("NtpManager", "NTP 时间同步失败，将回退使用系统本地时间。", it)
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