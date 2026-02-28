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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// 【规范】：UI 状态封闭类
sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    data class Success(val channelCount: Int) : SyncState() // 新增：携带频道数量
    data class Error(val message: String) : SyncState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository
) : ViewModel() {

    val iptvUrl = settingsManager.iptvUrlFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val epgUrl = settingsManager.epgUrlFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    fun saveUrlsAndSync(iptv: String, epg: String) {
        if (iptv.isBlank()) {
            _syncState.value = SyncState.Error("IPTV 地址不能为空")
            return
        }

        viewModelScope.launch {
            _syncState.value = SyncState.Loading
            try {
                settingsManager.saveIptvUrl(iptv)
                settingsManager.saveEpgUrl(epg)

                // 1. 同步频道
                channelRepository.syncChannelsFromUrl(iptv)

                // 2. 从数据库获取总数
                val channels = channelRepository.getAllChannels().firstOrNull() ?: emptyList()
                val channelCount = channels.size

                // 3. 后台同步 EPG
                if (epg.isNotBlank()) {
                    viewModelScope.launch {
                        try { epgRepository.syncEpgFromUrl(epg) } catch (e: Exception) { e.printStackTrace() }
                    }
                }

                // 4. 将频道数量发给 UI
                _syncState.value = SyncState.Success(channelCount)

            } catch (e: Exception) {
                _syncState.value = SyncState.Error("解析失败，请检查网络或地址")
            }
        }
    }

    fun resetState() {
        _syncState.value = SyncState.Idle
    }
}