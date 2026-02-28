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
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository,
    private val ntpManager: NtpManager
) : ViewModel() {

    val allChannels: StateFlow<List<Channel>> = channelRepository.getAllChannels()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    var isChannelListVisible by mutableStateOf(false)
    var currentPlayingChannel by mutableStateOf<Channel?>(null)

    // EPG 状态
    var isEpgVisible by mutableStateOf(false)
    var currentProgram by mutableStateOf<EpgProgram?>(null)
    var nextProgram by mutableStateOf<EpgProgram?>(null)
    private var epgHideJob: Job? = null

    // 播放指定频道
    fun playChannel(channel: Channel) {
        currentPlayingChannel = channel
        playerController.play(channel.url)
        isChannelListVisible = false

        // 每次换台，触发 EPG 抓取和显示
        fetchEpgForChannel(channel)
        showEpgCard()
    }

    private fun fetchEpgForChannel(channel: Channel) {
        viewModelScope.launch {
            currentProgram = null
            nextProgram = null

            val programs = epgRepository.getProgramsForChannel(channel.tvgId, channel.name).firstOrNull() ?: emptyList()
            if (programs.isEmpty()) return@launch

            val currentTime = ntpManager.getCurrentTime()
            val currentIndex = programs.indexOfFirst { it.startTime <= currentTime && it.endTime > currentTime }

            if (currentIndex != -1) {
                currentProgram = programs[currentIndex]
                if (currentIndex + 1 < programs.size) {
                    nextProgram = programs[currentIndex + 1]
                }
            } else {
                val futureIndex = programs.indexOfFirst { it.endTime > currentTime }
                if (futureIndex != -1) {
                    nextProgram = programs[futureIndex]
                }
            }
        }
    }

    // 显示 EPG，并开启 5 秒后自动隐藏的倒计时
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