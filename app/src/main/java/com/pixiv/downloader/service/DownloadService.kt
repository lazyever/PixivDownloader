package com.pixiv.downloader.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.pixiv.downloader.data.repository.DownloadRepository

class DownloadService : Service() {

    private lateinit var repository: DownloadRepository
    private lateinit var notificationHelper: DownloadNotificationHelper

    override fun onCreate() {
        super.onCreate()
        repository = DownloadRepository.getInstance(applicationContext)
        notificationHelper = DownloadNotificationHelper(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = notificationHelper.buildProgressNotification(
            title = "Pixiv 下载服务",
            content = "正在准备下载任务队列...",
            progress = 0,
            indeterminate = true
        )
        startForeground(DownloadNotificationHelper.NOTIFICATION_ID, initialNotification)

        repository.startQueueProcessing()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            context.stopService(intent)
        }
    }
}
