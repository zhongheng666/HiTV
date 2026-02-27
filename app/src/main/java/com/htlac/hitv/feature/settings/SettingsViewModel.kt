package com.htlac.hitv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.htlac.hitv.core.data.datastore.SettingsManager
import com.htlac.hitv.core.data.repository.ChannelRepository
import com.htlac.hitv.core.data.repository.EpgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// 定义页面的状态：空闲、解析中、成功、失败
sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val channelRepository: ChannelRepository, // 引入频道大管家
    private val epgRepository: EpgRepository          // 引入EPG大管家
) : ViewModel() {

    val iptvUrl: StateFlow<String> = settingsManager.iptvUrlFlow.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = ""
    )
    val epgUrl: StateFlow<String> = settingsManager.epgUrlFlow.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = ""
    )

    // 给 UI 暴露当前的解析状态
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    fun saveUrlsAndSync(iptv: String, epg: String) {
        // 如果没有填 IPTV 地址，直接报错
        if (iptv.isBlank()) {
            _syncState.value = SyncState.Error("IPTV 地址不能为空")
            return
        }

        viewModelScope.launch {
            _syncState.value = SyncState.Loading // 通知 UI 显示“正在解析”
            try {
                // 1. 先把地址安全存到本地
                settingsManager.saveIptvUrl(iptv)
                settingsManager.saveEpgUrl(epg)

                // 2. 阻塞式解析频道列表（必须等频道解析完，因为我们要拿着第一个频道去播放）
                channelRepository.syncChannelsFromUrl(iptv)

                // 3. 非阻塞式异步解析 EPG（单独开一个协程，让它在后台默默解析，绝不卡主线程）
                if (epg.isNotBlank()) {
                    viewModelScope.launch {
                        try {
                            epgRepository.syncEpgFromUrl(epg)
                        } catch (e: Exception) {
                            e.printStackTrace() // EPG 解析失败不影响看电视，只打印日志
                        }
                    }
                }

                // 4. 频道解析成功，通知 UI 跳转！
                _syncState.value = SyncState.Success

            } catch (e: Exception) {
                // 如果频道解析失败（比如网络不通、地址无效），通知 UI 显示错误
                _syncState.value = SyncState.Error("解析失败，请检查网络或地址是否正确")
            }
        }
    }

    // UI 收到错误提示后，重置状态以便再次尝试
    fun resetState() {
        _syncState.value = SyncState.Idle
    }
}