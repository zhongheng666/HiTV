package com.htlac.hitv.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// 使用 Kotlin 委托属性，在 Context 上挂载一个全局唯一的 DataStore 实例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hitv_settings")

@Singleton // 保证全局只有这一个配置管家
class SettingsManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    // 像字典一样，定义我们要存的数据的“钥匙 (Key)”
    companion object {
        val IPTV_URL_KEY = stringPreferencesKey("iptv_url")
        val EPG_URL_KEY = stringPreferencesKey("epg_url")
        // 设计文档 4.1 要求的防御性设置
        val FORCE_SOFT_AUDIO_KEY = booleanPreferencesKey("force_soft_audio")
        // 设计文档 6 要求的备用播放器共存
        val USE_MPV_KEY = booleanPreferencesKey("use_mpv")
    }

    // ================== IPTV 源地址 ==================
    // 供 UI 监听，只要源地址一变，UI 立马知道
    val iptvUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[IPTV_URL_KEY] ?: "" // 默认返回空字符串
    }

    suspend fun saveIptvUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[IPTV_URL_KEY] = url
        }
    }

    // ================== EPG 源地址 ==================
    val epgUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EPG_URL_KEY] ?: ""
    }

    suspend fun saveEpgUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[EPG_URL_KEY] = url
        }
    }

    // ================== 强制音频软解开关 ==================
    val forceSoftAudioFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FORCE_SOFT_AUDIO_KEY] ?: false // 默认关闭
    }

    suspend fun setForceSoftAudio(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FORCE_SOFT_AUDIO_KEY] = enabled
        }
    }

    // ================== MPV 备用播放器开关 ==================
    val useMpvFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_MPV_KEY] ?: false // 默认关闭，也就是默认使用 Media3
    }

    suspend fun setUseMpv(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_MPV_KEY] = enabled
        }
    }
}