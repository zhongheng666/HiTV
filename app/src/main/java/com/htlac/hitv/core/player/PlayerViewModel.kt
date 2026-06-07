package com.htlac.hitv.feature.player

import android.view.SurfaceView
import android.util.Log
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
import com.htlac.hitv.core.network.EpgSyncDaemon
import com.htlac.hitv.core.network.NtpManager
import com.htlac.hitv.core.player.Media3Player
import com.htlac.hitv.core.player.MpvPlayer
import com.htlac.hitv.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val media3Player: Media3Player,
    private val mpvPlayer: MpvPlayer,
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository,
    private val ntpManager: NtpManager,
    private val settingsManager: SettingsManager,
    private val epgSyncDaemon: EpgSyncDaemon
) : ViewModel() {

    private val TAG = "HiTV_Debug"

    val activePlayer = MutableStateFlow<PlayerController>(media3Player)
    private var currentSurfaceView: SurfaceView? = null

    val allChannels = channelRepository.getAllChannels().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val epgSyncEvent = epgRepository.epgSyncEvent
    val ntpSynced = ntpManager.isSyncedFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentIptvUrl = settingsManager.iptvUrlFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val iptvHistory = settingsManager.iptvHistoryFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val currentEpgUrl = settingsManager.epgUrlFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val epgHistory = settingsManager.epgHistoryFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val useMpv = settingsManager.useMpvFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val forceSoftAudio = settingsManager.forceSoftAudioFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    var isChannelListVisible by mutableStateOf(false)
    var isAdvancedSettingsVisible by mutableStateOf(false)
    var isSyncing by mutableStateOf(false)

    var showDebugPanel by mutableStateOf(false)
    var showClock by mutableStateOf(true)

    var currentPlayingChannel by mutableStateOf<Channel?>(null)
    var isEpgVisible by mutableStateOf(false)
    var currentProgram by mutableStateOf<EpgProgram?>(null)
    var nextProgram by mutableStateOf<EpgProgram?>(null)
    private var epgHideJob: Job? = null
    var epgDebugInfo by mutableStateOf("EPG 状态: 等待加载中...")

    var numpadBuffer by mutableStateOf("")
    private var numpadJob: Job? = null

    init {
        viewModelScope.launch {
            allChannels.collect { channels ->
                if (channels.isNotEmpty() && currentPlayingChannel == null) {
                    val lastHash = settingsManager.lastChannelHashFlow.firstOrNull()
                    val targetChannel = channels.find { it.urlHash == lastHash } ?: channels[0]
                    playChannel(targetChannel)
                }
            }
        }

        viewModelScope.launch {
            epgSyncEvent.collect { message ->
                if (message.contains("成功") || message.contains("✅")) {
                    currentPlayingChannel?.let { fetchEpgForChannel(it) }
                }
            }
        }

        viewModelScope.launch {
            settingsManager.useMpvFlow.collect { isMpvSelected ->
                val targetEngine = if (isMpvSelected) mpvPlayer else media3Player
                if (activePlayer.value != targetEngine) {
                    activePlayer.value.setSurface(null)
                    activePlayer.value.stop()
                    delay(300)
                    activePlayer.value = targetEngine
                    targetEngine.setSurface(currentSurfaceView)
                    currentPlayingChannel?.let { activePlayer.value.play(it.url) }
                }
            }
        }
    }

    fun setSurface(surfaceView: SurfaceView?) {
        currentSurfaceView = surfaceView
        activePlayer.value.setSurface(surfaceView)
    }

    fun onNumpadInput(digit: String) {
        isChannelListVisible = false
        isAdvancedSettingsVisible = false
        if (numpadBuffer.length >= 4) return
        numpadBuffer += digit
        numpadJob?.cancel()
        numpadJob = viewModelScope.launch { delay(2000); executeNumpadSwitch() }
    }

    fun executeNumpadSwitch() {
        numpadJob?.cancel()
        val targetNumber = numpadBuffer.toIntOrNull()
        numpadBuffer = ""
        if (targetNumber != null && targetNumber > 0) {
            val targetIndex = targetNumber - 1
            val list = allChannels.value
            if (targetIndex in list.indices) {
                playChannel(list[targetIndex])
            } else {
                epgDebugInfo = "❌ 频道号 $targetNumber 不存在"
            }
        }
    }

    fun switchIptvSource(newUrl: String) {
        // 【核心修复 1】：恢复保护机制，如果点击的是当前正在播放的源，无视操作，防误触！
        if (newUrl == currentIptvUrl.value) return

        viewModelScope.launch {
            isSyncing = true
            isAdvancedSettingsVisible = false
            activePlayer.value.stop()
            currentPlayingChannel = null
            try {
                settingsManager.saveIptvUrl(newUrl)
                channelRepository.syncChannelsFromUrl(newUrl)
            } catch (e: Exception) {
                epgDebugInfo = "❌ 切换源失败: ${e.message}"
            } finally {
                isSyncing = false
            }
        }
    }

    fun switchEpgSource(newUrl: String) {
        // 【核心修复 2】：恢复保护机制，防误触！
        if (newUrl == currentEpgUrl.value) return

        viewModelScope.launch {
            isAdvancedSettingsVisible = false
            settingsManager.saveEpgUrl(newUrl)
            try {
                epgRepository.syncEpgFromUrl(newUrl)
                currentPlayingChannel?.let { fetchEpgForChannel(it) }
            } catch (e: Exception) {}
        }
    }

    fun deleteIptvSource(url: String) {
        viewModelScope.launch { settingsManager.removeIptvHistory(url) }
    }

    fun deleteEpgSource(url: String) {
        viewModelScope.launch { settingsManager.removeEpgHistory(url) }
    }

    fun toggleMpv(enabled: Boolean) { viewModelScope.launch { settingsManager.setUseMpv(enabled) } }
    fun toggleSoftAudio(enabled: Boolean) { viewModelScope.launch { settingsManager.setForceSoftAudio(enabled) } }

    fun playChannel(channel: Channel) {
        currentPlayingChannel = channel
        viewModelScope.launch { settingsManager.saveLastChannelHash(channel.urlHash) }
        activePlayer.value.play(channel.url)
        isChannelListVisible = false
        fetchEpgForChannel(channel)
        showEpgCard()
    }

    private fun fetchEpgForChannel(channel: Channel) {
        viewModelScope.launch {
            currentProgram = null; nextProgram = null
            val dbTotal = epgRepository.getProgramCount()

            epgDebugInfo = "📦 DB总条数: $dbTotal\n🔍 极速检索 Hash:\n[${channel.urlHash}]"
            val allPrograms = epgRepository.getProgramsForChannel(channel.urlHash).firstOrNull() ?: emptyList()

            if (allPrograms.isEmpty()) {
                epgDebugInfo += "\n❌ 结果: 数据过期或为空"
                epgSyncDaemon.triggerSync("节目单过期嗅探报警")
                return@launch
            }

            val currentTime = ntpManager.getCurrentTime()
            val timeFormatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

            epgDebugInfo += "\n✅ 命中 ${allPrograms.size} 条节目"
            epgDebugInfo += "\n⏰ NTP: ${timeFormatter.format(Date(currentTime))}"

            currentProgram = allPrograms.firstOrNull()
            if (allPrograms.size > 1) { nextProgram = allPrograms[1] }
            epgDebugInfo += "\n▶️ 在播: ${currentProgram?.title}"
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
        val currentIndex = list.indexOfFirst { it.urlHash == currentPlayingChannel?.urlHash }
        val nextIndex = if (currentIndex + 1 < list.size) currentIndex + 1 else 0
        playChannel(list[nextIndex])
    }

    fun playPreviousChannel() {
        val list = allChannels.value
        if (list.isEmpty() || currentPlayingChannel == null) return
        val currentIndex = list.indexOfFirst { it.urlHash == currentPlayingChannel?.urlHash }
        val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else list.lastIndex
        playChannel(list[prevIndex])
    }

    override fun onCleared() {
        super.onCleared()
        media3Player.release()
        mpvPlayer.release()
    }
}