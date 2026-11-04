package com.pixiv.downloader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskStatus {
    PENDING,      // 等待下载
    DOWNLOADING,  // 正在下载或合成
    PAUSED,       // 已暂停
    COMPLETED,    // 已完成
    FAILED,       // 失败
    SKIPPED       // 已存在跳过
}

@Entity(tableName = "download_tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val illustId: String,
    val title: String,
    val authorName: String,
    val authorId: String,
    val coverUrl: String,
    val pageCount: Int,
    val downloadedPages: Int = 0,
    val status: TaskStatus = TaskStatus.PENDING,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val savedPath: String? = null,
    val isUgoira: Boolean = false,
    val ugoiraFormat: String = "MP4", // MP4 或 GIF
    val ugoiraFramesJson: String? = null,
    val ugoiraZipUrl: String? = null,
    val originalUrlsJson: String = "[]",
    val authType: String = "匿名", // 最终实际使用的认证方式："匿名" 或 "已登录"
    val createTime: Long = System.currentTimeMillis(),
    val completedTime: Long? = null
)
