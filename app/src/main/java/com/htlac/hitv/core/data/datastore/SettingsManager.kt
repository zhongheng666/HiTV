package com.htlac.hitv.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// 使用 Kotlin 委托属性在顶层创建 DataStore 单例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hitv_settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 定义所有的存储 Key
    companion object {
        val IPTV_URL = stringPreferencesKey("iptv_url")
        val EPG_URL = stringPreferencesKey("epg_url")
        val USE_MPV = booleanPreferencesKey("use_mpv")
        val FORCE_SOFT_AUDIO = booleanPreferencesKey("force_soft_audio")

        // 历史记录 Keys (使用 StringSet 保存多个源)
        val IPTV_HISTORY = stringSetPreferencesKey("iptv_history")
        val EPG_HISTORY = stringSetPreferencesKey("epg_history")
    }

    // ================= 读取数据流 (Flow) =================

    val iptvUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[IPTV_URL] ?: ""
    }

    val epgUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EPG_URL] ?: ""
    }

    val useMpvFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_MPV] ?: false
    }

    val forceSoftAudioFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FORCE_SOFT_AUDIO] ?: false
    }

    /**
     * 暴露 IPTV 历史记录流
     * 如果当前配置的 URL 不在历史中，也将其合并进去，确保当前源一定可见
     */
    val iptvHistoryFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val history = preferences[IPTV_HISTORY] ?: emptySet()
        val current = preferences[IPTV_URL] ?: ""
        if (current.isNotBlank()) history + current else history
    }

    /**
     * 暴露 EPG 历史记录流
     */
    val epgHistoryFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val history = preferences[EPG_HISTORY] ?: emptySet()
        val current = preferences[EPG_URL] ?: ""
        if (current.isNotBlank()) history + current else history
    }

    // ================= 写入数据 (Suspend) =================

    suspend fun saveIptvUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[IPTV_URL] = url
            // 同步保存到历史记录中
            if (url.isNotBlank()) {
                val currentHistory = preferences[IPTV_HISTORY] ?: emptySet()
                // DataStore 规定：必须创建一个新的 Set 进行赋值，不能直接修改原 Set
                preferences[IPTV_HISTORY] = currentHistory + url
            }
        }
    }

    suspend fun saveEpgUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[EPG_URL] = url
            // 同步保存到历史记录中
            if (url.isNotBlank()) {
                val currentHistory = preferences[EPG_HISTORY] ?: emptySet()
                preferences[EPG_HISTORY] = currentHistory + url
            }
        }
    }

    suspend fun setUseMpv(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_MPV] = enabled
        }
    }

    suspend fun setForceSoftAudio(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FORCE_SOFT_AUDIO] = enabled
        }
    }

    /**
     * 清理所有历史记录（预留的高级管理接口）
     */
    suspend fun clearAllHistory() {
        context.dataStore.edit { preferences ->
            preferences.remove(IPTV_HISTORY)
            preferences.remove(EPG_HISTORY)
        }
    }
}