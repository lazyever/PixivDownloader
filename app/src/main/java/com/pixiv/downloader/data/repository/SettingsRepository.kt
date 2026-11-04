package com.pixiv.downloader.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class SettingsState(
        val customUriString: String? = null,
        val duplicateAction: String = "SKIP", // "SKIP" 或 "OVERWRITE"
        val defaultUgoiraFormat: String = "MP4", // "MP4" 或 "GIF"
        val preferAnonymous: Boolean = true // 公开作品优先匿名下载（默认开启）
    )

    private val _settingsState = MutableStateFlow(loadSettings())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private fun loadSettings(): SettingsState {
        return SettingsState(
            customUriString = prefs.getString(KEY_CUSTOM_URI, null),
            duplicateAction = prefs.getString(KEY_DUPLICATE_ACTION, "SKIP") ?: "SKIP",
            defaultUgoiraFormat = prefs.getString(KEY_DEFAULT_UGOIRA_FORMAT, "MP4") ?: "MP4",
            preferAnonymous = prefs.getBoolean(KEY_PREFER_ANONYMOUS, true)
        )
    }

    fun setPreferAnonymous(prefer: Boolean) {
        prefs.edit().putBoolean(KEY_PREFER_ANONYMOUS, prefer).apply()
        _settingsState.value = _settingsState.value.copy(preferAnonymous = prefer)
    }

    fun setCustomDirectoryUri(uriString: String?) {
        prefs.edit().putString(KEY_CUSTOM_URI, uriString).apply()
        _settingsState.value = _settingsState.value.copy(customUriString = uriString)
    }

    fun resetToDefaultDirectory() {
        prefs.edit().remove(KEY_CUSTOM_URI).apply()
        _settingsState.value = _settingsState.value.copy(customUriString = null)
    }

    fun setDuplicateAction(action: String) {
        prefs.edit().putString(KEY_DUPLICATE_ACTION, action).apply()
        _settingsState.value = _settingsState.value.copy(duplicateAction = action)
    }

    fun setDefaultUgoiraFormat(format: String) {
        prefs.edit().putString(KEY_DEFAULT_UGOIRA_FORMAT, format).apply()
        _settingsState.value = _settingsState.value.copy(defaultUgoiraFormat = format)
    }

    companion object {
        private const val PREFS_NAME = "pixiv_downloader_settings"
        private const val KEY_CUSTOM_URI = "custom_storage_uri"
        private const val KEY_DUPLICATE_ACTION = "duplicate_action"
        private const val KEY_DEFAULT_UGOIRA_FORMAT = "default_ugoira_format"
        private const val KEY_PREFER_ANONYMOUS = "prefer_anonymous"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
