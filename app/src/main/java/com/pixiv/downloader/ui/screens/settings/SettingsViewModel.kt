package com.pixiv.downloader.ui.screens.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.downloader.data.api.PixivClient
import com.pixiv.downloader.data.auth.PixivAuthManager
import com.pixiv.downloader.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = PixivAuthManager.getInstance(application)
    private val pixivClient = PixivClient.getInstance(application)
    private val settingsRepository = SettingsRepository.getInstance(application)

    val userProfile = authManager.userProfileState
    val settingsState = settingsRepository.settingsState

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    fun getLoginUrlWithVerifier(): Pair<String, String> {
        return authManager.generateLoginUrl()
    }

    fun handleAuthCode(code: String, verifier: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoggingIn.value = true
            _loginError.value = null
            val result = pixivClient.exchangeToken(code, verifier)
            _isLoggingIn.value = false
            result.onSuccess {
                onComplete(true)
            }.onFailure { err ->
                _loginError.value = "授权登录失败: ${err.localizedMessage}"
                onComplete(false)
            }
        }
    }

    fun logout() {
        authManager.logout()
    }

    fun setCustomDirectory(uri: Uri) {
        settingsRepository.setCustomDirectoryUri(uri.toString())
    }

    fun resetToDefaultDirectory() {
        settingsRepository.resetToDefaultDirectory()
    }

    fun setDefaultUgoiraFormat(format: String) {
        settingsRepository.setDefaultUgoiraFormat(format)
    }

    fun setPreferAnonymous(prefer: Boolean) {
        settingsRepository.setPreferAnonymous(prefer)
    }

    fun dismissLoginError() {
        _loginError.value = null
    }
}
