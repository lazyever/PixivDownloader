package com.pixiv.downloader.data.api.models

import com.google.gson.annotations.SerializedName

/**
 * Pixiv 官方 OAuth Token 响应
 */
data class AuthTokenResponse(
    @SerializedName("access_token")
    val accessToken: String?,
    @SerializedName("refresh_token")
    val refreshToken: String?,
    @SerializedName("expires_in")
    val expiresIn: Long?,
    @SerializedName("token_type")
    val tokenType: String?,
    @SerializedName("scope")
    val scope: String?,
    @SerializedName("user")
    val user: AuthUser?
)

data class AuthUser(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("account")
    val account: String,
    @SerializedName("profile_image_urls")
    val profileImageUrls: AuthProfileImageUrls?
)

data class AuthProfileImageUrls(
    @SerializedName("px_50x50")
    val px50x50: String?,
    @SerializedName("px_170x170")
    val px170x170: String?
)
