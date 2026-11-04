package com.pixiv.downloader

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.pixiv.downloader.data.api.PixivClient
import com.pixiv.downloader.data.db.AppDatabase
import com.pixiv.downloader.service.DownloadNotificationHelper

class PixivDownloaderApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        try {
            AppDatabase.getInstance(this)
        } catch (e: Throwable) {
            Log.e("PixivApp", "数据库预初始化异常", e)
        }
        try {
            DownloadNotificationHelper(this)
        } catch (e: Throwable) {
            Log.e("PixivApp", "通知组件初始化异常", e)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return try {
            ImageLoader.Builder(this)
                .okHttpClient { PixivClient.getInstance(this).okHttpClient }
                .crossfade(true)
                .build()
        } catch (e: Throwable) {
            Log.e("PixivApp", "ImageLoader 初始化异常，启用默认配置", e)
            ImageLoader.Builder(this)
                .crossfade(true)
                .build()
        }
    }
}
