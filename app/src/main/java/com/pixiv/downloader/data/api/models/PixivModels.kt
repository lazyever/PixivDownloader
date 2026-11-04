package com.pixiv.downloader.data.api.models

import com.google.gson.annotations.SerializedName

/**
 * Pixiv 作品通用信息模型（支持单图、多图与 Ugoira 动图）
 */
data class ArtworkInfo(
    val id: String,
    val title: String,
    val authorName: String,
    val authorId: String,
    val coverUrl: String,
    val pageCount: Int,
    val originalUrls: List<String>,
    val isUgoira: Boolean = false,
    val ugoiraFrames: List<UgoiraFrame>? = null,
    val ugoiraZipUrl: String? = null,
    val authType: String = "匿名" // 最终实际使用的认证方式："匿名" 或 "已登录"
)

/**
 * Ugoira 动图单帧元数据
 */
data class UgoiraFrame(
    @SerializedName("file")
    val file: String,
    @SerializedName("delay")
    val delay: Int // 毫秒
)

/**
 * Pixiv 网页端 Ajax 插画信息响应
 */
data class PixivAjaxIllustResponse(
    @SerializedName("error")
    val error: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("body")
    val body: PixivAjaxIllustBody?
)

data class PixivAjaxIllustBody(
    @SerializedName("illustId")
    val illustId: String,
    @SerializedName("illustTitle")
    val illustTitle: String,
    @SerializedName("illustType")
    val illustType: Int, // 0: 插画, 1: 漫画, 2: ugoira 动图
    @SerializedName("userName")
    val userName: String,
    @SerializedName("userId")
    val userId: String,
    @SerializedName("pageCount")
    val pageCount: Int,
    @SerializedName("urls")
    val urls: PixivUrls?
)

data class PixivUrls(
    @SerializedName("mini")
    val mini: String?,
    @SerializedName("thumb")
    val thumb: String?,
    @SerializedName("small")
    val small: String?,
    @SerializedName("regular")
    val regular: String?,
    @SerializedName("original")
    val original: String?
)

/**
 * Pixiv 网页端 Ajax 多页插画响应
 */
data class PixivAjaxPagesResponse(
    @SerializedName("error")
    val error: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("body")
    val body: List<PixivAjaxPageItem>?
)

data class PixivAjaxPageItem(
    @SerializedName("urls")
    val urls: PixivUrls?
)

/**
 * Pixiv 网页端 Ugoira 动图元数据响应
 */
data class PixivAjaxUgoiraResponse(
    @SerializedName("error")
    val error: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("body")
    val body: PixivAjaxUgoiraBody?
)

data class PixivAjaxUgoiraBody(
    @SerializedName("src")
    val src: String?,
    @SerializedName("originalSrc")
    val originalSrc: String?,
    @SerializedName("mime_type")
    val mimeType: String?,
    @SerializedName("frames")
    val frames: List<UgoiraFrame>?
)
