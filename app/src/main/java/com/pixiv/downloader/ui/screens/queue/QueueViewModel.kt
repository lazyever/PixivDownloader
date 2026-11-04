package com.pixiv.downloader.ui.screens.queue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.downloader.data.db.TaskEntity
import com.pixiv.downloader.data.repository.DownloadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QueueViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository.getInstance(application)

    val queueTasks: StateFlow<List<TaskEntity>> = repository.queueTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pauseTask(taskId: Long) {
        viewModelScope.launch {
            repository.pauseTask(taskId)
        }
    }

    fun resumeTask(taskId: Long) {
        viewModelScope.launch {
            repository.resumeTask(taskId)
        }
    }

    fun cancelTask(taskId: Long) {
        viewModelScope.launch {
            repository.cancelTask(taskId)
        }
    }
}
