package com.htlac.hitv.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository,
    private val ntpManager: NtpManager
) : ViewModel() {

    val allChannels: StateFlow<List<Channel>> = channelRepository.getAllChannels()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 暴露 EPG 全局后台事件给 UI 弹 Toast
    val epgSyncEvent = epgRepository.epgSyncEvent

    var isChannelListVisible by mutableStateOf(false)
    var currentPlayingChannel by mutableStateOf<Channel?>(null)

    var isEpgVisible by mutableStateOf(false)
    var currentProgram by mutableStateOf<EpgProgram?>(null)
    var nextProgram by mutableStateOf<EpgProgram?>(null)
    private var epgHideJob: Job? = null

    // 【核心新增：EPG 嗅探诊断雷达】
    var epgDebugInfo by mutableStateOf("EPG 状态: 未开始匹配")

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

            // 诊断 1：开始匹配
            epgDebugInfo = "🔍 EPG 尝试匹配: tvg-id=[${channel.tvgId}], 名称=[${channel.name}]"

            val programs = epgRepository.getProgramsForChannel(channel.tvgId, channel.name).firstOrNull() ?: emptyList()

            // 诊断 2：数据库检索结果
            if (programs.isEmpty()) {
                epgDebugInfo += "\n❌ 结果: 数据库中未找到相关节目。请检查源的 tvg-id 是否一致！"
                return@launch
            }

            val currentTime = ntpManager.getCurrentTime()
            epgDebugInfo += "\n✅ 结果: 找到 ${programs.size} 条数据。系统时间戳: $currentTime"

            val currentIndex = programs.indexOfFirst { it.startTime <= currentTime && it.endTime > currentTime }

            // 诊断 3：时间过滤结果
            if (currentIndex != -1) {
                currentProgram = programs[currentIndex]
                if (currentIndex + 1 < programs.size) {
                    nextProgram = programs[currentIndex + 1]
                }
                epgDebugInfo += "\n🎯 时间匹配成功！"
            } else {
                epgDebugInfo += "\n⚠️ 警告: 有数据，但没有找到当前时间段的节目 (可能是 EPG 过期或时区错误)"
                val futureIndex = programs.indexOfFirst { it.endTime > currentTime }
                if (futureIndex != -1) {
                    nextProgram = programs[futureIndex]
                }
            }
        }
    }

    fun showEpgCard() {
        isEpgVisible = true
        epgHideJob?.cancel()
        epgHideJob = viewModelScope.launch {
            delay(5000)
            isEpgVisible = false
        }
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