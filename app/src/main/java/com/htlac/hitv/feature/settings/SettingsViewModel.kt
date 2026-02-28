package com.htlac.hitv.feature.settings

import android.util.Log
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

sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    data class Success(val channelCount: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository
) : ViewModel() {

    private val TAG = "HiTV_Debug"

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

                // 同步频道
                channelRepository.syncChannelsFromUrl(iptv)

                // 从数据库获取总数
                val channels = channelRepository.getAllChannels().firstOrNull() ?: emptyList()
                val channelCount = channels.size

                // 【核心：将频道导入结果强力输出到 Logcat】
                Log.i(TAG, "📺 IPTV 频道解析完成，成功导入 $channelCount 个频道！")

                // 后台同步 EPG
                if (epg.isNotBlank()) {
                    viewModelScope.launch {
                        try { epgRepository.syncEpgFromUrl(epg) } catch (e: Exception) { e.printStackTrace() }
                    }
                }

                _syncState.value = SyncState.Success(channelCount)

            } catch (e: Exception) {
                Log.e(TAG, "❌ 频道解析失败", e)
                _syncState.value = SyncState.Error("解析失败，请检查网络或地址")
            }
        }
    }

    fun resetState() {
        _syncState.value = SyncState.Idle
    }
}