package com.pixiv.downloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pixiv.downloader.MainActivity
import com.pixiv.downloader.R

class DownloadNotificationHelper(private val context: Context) {

    private val notificationManager: NotificationManager? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(NotificationManager::class.java)
        } else {
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        }
    } catch (e: Throwable) {
        Log.e(TAG, "获取 NotificationManager 异常", e)
        null
    }

    init {
        try {
            createNotificationChannel()
        } catch (e: Throwable) {
            Log.e(TAG, "创建通知渠道异常", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun buildProgressNotification(
        title: String,
        content: String,
        progress: Int,
        maxProgress: Int = 100,
        indeterminate: Boolean = false
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(maxProgress, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateProgress(
        title: String,
        content: String,
        progress: Int,
        maxProgress: Int = 100,
        indeterminate: Boolean = false
    ) {
        try {
            val notification = buildProgressNotification(title, content, progress, maxProgress, indeterminate)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Throwable) {
            Log.e(TAG, "更新通知异常", e)
        }
    }

    fun cancelNotification() {
        try {
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Throwable) {
            Log.e(TAG, "取消通知异常", e)
        }
    }

    companion object {
        private const val TAG = "DownloadNotification"
        const val CHANNEL_ID = "pixiv_download_channel"
        const val NOTIFICATION_ID = 1001
    }
}
