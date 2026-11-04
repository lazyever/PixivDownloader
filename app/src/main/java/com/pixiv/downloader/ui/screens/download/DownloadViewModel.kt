package com.pixiv.downloader.ui.screens.download

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.downloader.data.api.PixivAuthRequiredException
import com.pixiv.downloader.data.api.PixivClient
import com.pixiv.downloader.data.api.models.ArtworkInfo
import com.pixiv.downloader.data.repository.DownloadRepository
import com.pixiv.downloader.data.repository.SettingsRepository
import com.pixiv.downloader.service.DownloadService
import com.pixiv.downloader.util.PixivUrlParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val pixivClient = PixivClient.getInstance(application)
    private val downloadRepository = DownloadRepository.getInstance(application)
    private val settingsRepository = SettingsRepository.getInstance(application)

    data class UiState(
        val inputText: String = "",
        val isParsing: Boolean = false,
        val parsedArtworks: List<ArtworkInfo> = emptyList(),
        val selectedUgoiraFormats: Map<String, String> = emptyMap(), // illustId -> "MP4" or "GIF"
        val errorMessage: String? = null,
        val successMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onInputTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text, errorMessage = null)
    }

    fun parseInputUrls() {
        val input = _uiState.value.inputText
        val illustIds = PixivUrlParser.parseMultipleIds(input)

        if (illustIds.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "未在输入中识别出有效的 Pixiv 作品链接或作品 ID，请核对后重试。"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isParsing = true, errorMessage = null, successMessage = null)
            val results = mutableListOf<ArtworkInfo>()
            val formatMap = _uiState.value.selectedUgoiraFormats.toMutableMap()
            val defaultFormat = settingsRepository.settingsState.value.defaultUgoiraFormat
            val preferAnonymous = settingsRepository.settingsState.value.preferAnonymous
            var failureMsg: String? = null

            for (id in illustIds) {
                val res = pixivClient.fetchArtwork(id, preferAnonymous = preferAnonymous)
                res.onSuccess { artwork ->
                    results.add(artwork)
                    if (artwork.isUgoira && !formatMap.containsKey(artwork.id)) {
                        formatMap[artwork.id] = defaultFormat
                    }
                }.onFailure { err ->
                    if (err is PixivAuthRequiredException) {
                        failureMsg = "作品 $id 需要登录 Pixiv 账号后方可访问，请前往「设置」页面登录。"
                    } else if (failureMsg == null) {
                        failureMsg = "解析作品 $id 失败: ${err.localizedMessage}"
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                isParsing = false,
                parsedArtworks = results,
                selectedUgoiraFormats = formatMap,
                errorMessage = failureMsg,
                successMessage = if (results.isNotEmpty()) "成功解析 ${results.size} 个作品" else null
            )
        }
    }

    fun setUgoiraFormat(illustId: String, format: String) {
        val updated = _uiState.value.selectedUgoiraFormats.toMutableMap()
        updated[illustId] = format
        _uiState.value = _uiState.value.copy(selectedUgoiraFormats = updated)
    }

    fun downloadSingle(artwork: ArtworkInfo) {
        viewModelScope.launch {
            val format = _uiState.value.selectedUgoiraFormats[artwork.id]
                ?: settingsRepository.settingsState.value.defaultUgoiraFormat

            downloadRepository.enqueueArtwork(artwork, format)
            DownloadService.start(getApplication())

            // 从解析列表中移除已加入队列的项目
            val remaining = _uiState.value.parsedArtworks.filter { it.id != artwork.id }
            _uiState.value = _uiState.value.copy(
                parsedArtworks = remaining,
                successMessage = "已将《${artwork.title}》加入下载队列"
            )
        }
    }

    fun downloadAll() {
        val list = _uiState.value.parsedArtworks
        if (list.isEmpty()) return

        viewModelScope.launch {
            for (artwork in list) {
                val format = _uiState.value.selectedUgoiraFormats[artwork.id]
                    ?: settingsRepository.settingsState.value.defaultUgoiraFormat
                downloadRepository.enqueueArtwork(artwork, format)
            }
            DownloadService.start(getApplication())

            _uiState.value = _uiState.value.copy(
                parsedArtworks = emptyList(),
                inputText = "",
                successMessage = "已将全部 ${list.size} 个作品加入下载队列"
            )
        }
    }

    fun clearInput() {
        _uiState.value = _uiState.value.copy(inputText = "", parsedArtworks = emptyList(), errorMessage = null, successMessage = null)
    }

    fun dismissMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
