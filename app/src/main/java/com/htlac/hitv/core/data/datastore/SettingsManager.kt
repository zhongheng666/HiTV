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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hitv_settings")

@Singleton
class SettingsManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        val IPTV_URL = stringPreferencesKey("iptv_url")
        val EPG_URL = stringPreferencesKey("epg_url")
        val USE_MPV = booleanPreferencesKey("use_mpv")
        val FORCE_SOFT_AUDIO = booleanPreferencesKey("force_soft_audio")
        val LAST_CHANNEL_HASH = stringPreferencesKey("last_channel_hash")

        val IPTV_HISTORY = stringSetPreferencesKey("iptv_history")
        val EPG_HISTORY = stringSetPreferencesKey("epg_history")
    }

    val iptvUrlFlow: Flow<String> = context.dataStore.data.map { it[IPTV_URL] ?: "" }
    val epgUrlFlow: Flow<String> = context.dataStore.data.map { it[EPG_URL] ?: "" }
    val useMpvFlow: Flow<Boolean> = context.dataStore.data.map { it[USE_MPV] ?: false }
    val forceSoftAudioFlow: Flow<Boolean> = context.dataStore.data.map { it[FORCE_SOFT_AUDIO] ?: false }
    val lastChannelHashFlow: Flow<String> = context.dataStore.data.map { it[LAST_CHANNEL_HASH] ?: "" }

    val iptvHistoryFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val history = preferences[IPTV_HISTORY] ?: emptySet()
        val current = preferences[IPTV_URL] ?: ""
        if (current.isNotBlank()) history + current else history
    }

    val epgHistoryFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val history = preferences[EPG_HISTORY] ?: emptySet()
        val current = preferences[EPG_URL] ?: ""
        if (current.isNotBlank()) history + current else history
    }

    suspend fun saveIptvUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[IPTV_URL] = url
            if (url.isNotBlank()) {
                val currentHistory = preferences[IPTV_HISTORY] ?: emptySet()
                preferences[IPTV_HISTORY] = currentHistory + url
            }
        }
    }

    suspend fun saveEpgUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[EPG_URL] = url
            if (url.isNotBlank()) {
                val currentHistory = preferences[EPG_HISTORY] ?: emptySet()
                preferences[EPG_HISTORY] = currentHistory + url
            }
        }
    }

    suspend fun setUseMpv(enabled: Boolean) { context.dataStore.edit { it[USE_MPV] = enabled } }
    suspend fun setForceSoftAudio(enabled: Boolean) { context.dataStore.edit { it[FORCE_SOFT_AUDIO] = enabled } }

    suspend fun saveLastChannelHash(hash: String) {
        context.dataStore.edit { it[LAST_CHANNEL_HASH] = hash }
    }

    // 【深度修复】：使用 filter 和 toSet 强制分配新内存地址，完美解决 DataStore 脏读不刷新的 Bug
    suspend fun removeIptvHistory(url: String) {
        context.dataStore.edit { preferences ->
            val currentHistory = preferences[IPTV_HISTORY] ?: emptySet()
            preferences[IPTV_HISTORY] = currentHistory.filter { it != url }.toSet()
            if (preferences[IPTV_URL] == url) {
                preferences[IPTV_URL] = ""
            }
        }
    }

    // 【深度修复】：同上
    suspend fun removeEpgHistory(url: String) {
        context.dataStore.edit { preferences ->
            val currentHistory = preferences[EPG_HISTORY] ?: emptySet()
            preferences[EPG_HISTORY] = currentHistory.filter { it != url }.toSet()
            if (preferences[EPG_URL] == url) {
                preferences[EPG_URL] = ""
            }
        }
    }

    suspend fun clearAllHistory() {
        context.dataStore.edit { preferences ->
            preferences.remove(IPTV_HISTORY)
            preferences.remove(EPG_HISTORY)
        }
    }
}