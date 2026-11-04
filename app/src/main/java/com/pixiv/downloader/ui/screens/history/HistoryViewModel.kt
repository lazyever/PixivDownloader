package com.pixiv.downloader.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.downloader.data.db.TaskEntity
import com.pixiv.downloader.data.db.TaskStatus
import com.pixiv.downloader.data.repository.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository.getInstance(application)

    enum class HistoryFilter { ALL, COMPLETED, FAILED, SKIPPED }

    val currentFilter = MutableStateFlow(HistoryFilter.ALL)

    val filteredHistoryTasks: StateFlow<List<TaskEntity>> = combine(
        repository.historyTasks,
        currentFilter
    ) { tasks, filter ->
        when (filter) {
            HistoryFilter.ALL -> tasks
            HistoryFilter.COMPLETED -> tasks.filter { it.status == TaskStatus.COMPLETED }
            HistoryFilter.FAILED -> tasks.filter { it.status == TaskStatus.FAILED }
            HistoryFilter.SKIPPED -> tasks.filter { it.status == TaskStatus.SKIPPED }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: HistoryFilter) {
        currentFilter.value = filter
    }

    fun retryTask(taskId: Long) {
        viewModelScope.launch {
            repository.retryTask(taskId)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            repository.cancelTask(taskId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
