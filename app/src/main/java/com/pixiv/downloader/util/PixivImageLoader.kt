package com.pixiv.downloader.util

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import com.pixiv.downloader.data.api.PixivClient

object PixivImageLoader {

    private var imageLoader: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return imageLoader ?: synchronized(this) {
            imageLoader ?: ImageLoader.Builder(context.applicationContext)
                .okHttpClient { PixivClient.getInstance(context).anonymousOkHttpClient }
                .crossfade(true)
                .build().also { imageLoader = it }
        }
    }

    fun buildRequest(context: Context, url: String): ImageRequest {
        return ImageRequest.Builder(context)
            .data(url)
            .addHeader("Referer", "https://www.pixiv.net/")
            .crossfade(true)
            .build()
    }
}
