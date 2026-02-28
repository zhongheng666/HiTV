package com.htlac.hitv.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.htlac.hitv.core.data.datastore.SettingsManager
import com.htlac.hitv.core.data.local.Channel
import com.htlac.hitv.core.data.local.EpgProgram
import com.htlac.hitv.core.data.repository.ChannelRepository
import com.htlac.hitv.core.data.repository.EpgRepository
import com.htlac.hitv.core.network.NtpManager
import com.htlac.hitv.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerController: PlayerController,
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository,
    private val ntpManager: NtpManager,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val allChannels = channelRepository.getAllChannels().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val epgSyncEvent = epgRepository.epgSyncEvent

    // 当前源和历史源
    val currentIptvUrl = settingsManager.iptvUrlFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val iptvHistory = settingsManager.iptvHistoryFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val currentEpgUrl = settingsManager.epgUrlFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val epgHistory = settingsManager.epgHistoryFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val useMpv = settingsManager.useMpvFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val forceSoftAudio = settingsManager.forceSoftAudioFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    var isChannelListVisible by mutableStateOf(false)
    var isAdvancedSettingsVisible by mutableStateOf(false)

    // 【核心新增：全局加载状态】
    var isSyncing by mutableStateOf(false)

    var currentPlayingChannel by mutableStateOf<Channel?>(null)
    var isEpgVisible by mutableStateOf(false)
    var currentProgram by mutableStateOf<EpgProgram?>(null)
    var nextProgram by mutableStateOf<EpgProgram?>(null)
    private var epgHideJob: Job? = null
    var epgDebugInfo by mutableStateOf("EPG 状态: 等待加载中...")

    init {
        viewModelScope.launch {
            epgSyncEvent.collect { message ->
                if (message.contains("成功") || message.contains("✅")) {
                    currentPlayingChannel?.let { fetchEpgForChannel(it) }
                }
            }
        }
    }

    // 【核心新增：一键切换 IPTV 源】
    fun switchIptvSource(newUrl: String) {
        if (newUrl == currentIptvUrl.value) return
        viewModelScope.launch {
            isSyncing = true
            isAdvancedSettingsVisible = false
            playerController.pause() // 停止当前播放

            try {
                settingsManager.saveIptvUrl(newUrl)
                channelRepository.syncChannelsFromUrl(newUrl) // 重新下载解析

                // 自动播放新源的第一个频道
                val newChannels = channelRepository.getAllChannels().firstOrNull() ?: emptyList()
                if (newChannels.isNotEmpty()) {
                    playChannel(newChannels[0])
                }
            } catch (e: Exception) {
                epgDebugInfo = "❌ 切换源失败: ${e.message}"
            } finally {
                isSyncing = false
            }
        }
    }

    // 【核心新增：一键切换 EPG 源】
    fun switchEpgSource(newUrl: String) {
        if (newUrl == currentEpgUrl.value) return
        viewModelScope.launch {
            isAdvancedSettingsVisible = false
            settingsManager.saveEpgUrl(newUrl)
            try { epgRepository.syncEpgFromUrl(newUrl) } catch (e: Exception) {}
        }
    }

    fun toggleMpv(enabled: Boolean) { viewModelScope.launch { settingsManager.setUseMpv(enabled) } }
    fun toggleSoftAudio(enabled: Boolean) { viewModelScope.launch { settingsManager.setForceSoftAudio(enabled) } }

    fun playChannel(channel: Channel) {
        currentPlayingChannel = channel
        playerController.play(channel.url)
        isChannelListVisible = false
        fetchEpgForChannel(channel)
        showEpgCard()
    }

    private fun fetchEpgForChannel(channel: Channel) {
        viewModelScope.launch {
            currentProgram = null
            nextProgram = null
            epgDebugInfo = "🔍 EPG 尝试匹配: tvg-id=[${channel.tvgId}], 名称=[${channel.name}]"
            val programs = epgRepository.getProgramsForChannel(channel.tvgId, channel.name).firstOrNull() ?: emptyList()
            if (programs.isEmpty()) {
                epgDebugInfo += "\n❌ 结果: 数据库未找到相关节目"
                return@launch
            }
            val currentTime = ntpManager.getCurrentTime()
            val currentIndex = programs.indexOfFirst { it.startTime <= currentTime && it.endTime > currentTime }
            if (currentIndex != -1) {
                currentProgram = programs[currentIndex]
                if (currentIndex + 1 < programs.size) nextProgram = programs[currentIndex + 1]
                epgDebugInfo += "\n🎯 时间匹配成功！"
            } else {
                epgDebugInfo += "\n⚠️ 警告: 有数据但未找到当前时间段的节目"
                val futureIndex = programs.indexOfFirst { it.endTime > currentTime }
                if (futureIndex != -1) nextProgram = programs[futureIndex]
            }
        }
    }

    fun showEpgCard() {
        isEpgVisible = true
        epgHideJob?.cancel()
        epgHideJob = viewModelScope.launch { delay(5000); isEpgVisible = false }
    }

    fun playNextChannel() {
        val list = allChannels.value
        if (list.isEmpty() || currentPlayingChannel == null) return
        val currentIndex = list.indexOfFirst { it.id == currentPlayingChannel?.id }
        val nextIndex = if (currentIndex + 1 < list.size) currentIndex + 1 else 0
        playChannel(list[nextIndex])
    }

    fun playPreviousChannel() {
        val list = allChannels.value
        if (list.isEmpty() || currentPlayingChannel == null) return
        val currentIndex = list.indexOfFirst { it.id == currentPlayingChannel?.id }
        val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else list.lastIndex
        playChannel(list[prevIndex])
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}