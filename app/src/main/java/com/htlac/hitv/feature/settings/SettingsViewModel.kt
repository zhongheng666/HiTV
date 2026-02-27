package com.htlac.hitv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.htlac.hitv.core.data.datastore.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val iptvUrl: StateFlow<String> = settingsManager.iptvUrlFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    // 新增：监听 EPG 地址
    val epgUrl: StateFlow<String> = settingsManager.epgUrlFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    // 一键同时保存两个地址
    fun saveUrls(iptv: String, epg: String) {
        viewModelScope.launch {
            settingsManager.saveIptvUrl(iptv)
            settingsManager.saveEpgUrl(epg)
        }
    }
}