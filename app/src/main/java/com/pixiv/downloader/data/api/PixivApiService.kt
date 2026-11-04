package com.pixiv.downloader.data.api

import com.pixiv.downloader.data.api.models.AuthTokenResponse
import com.pixiv.downloader.data.api.models.PixivAjaxIllustResponse
import com.pixiv.downloader.data.api.models.PixivAjaxPagesResponse
import com.pixiv.downloader.data.api.models.PixivAjaxUgoiraResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface PixivApiService {

    @GET("ajax/illust/{id}")
    suspend fun getIllustAjax(
        @Path("id") id: String,
        @Query("lang") lang: String = "zh",
        @Header("Referer") referer: String
    ): Response<PixivAjaxIllustResponse>

    @GET("ajax/illust/{id}/pages")
    suspend fun getIllustPages(
        @Path("id") id: String,
        @Query("lang") lang: String = "zh",
        @Header("Referer") referer: String
    ): Response<PixivAjaxPagesResponse>

    @GET("ajax/illust/{id}/ugoira_meta")
    suspend fun getUgoiraMeta(
        @Path("id") id: String,
        @Query("lang") lang: String = "zh",
        @Header("Referer") referer: String
    ): Response<PixivAjaxUgoiraResponse>

    @FormUrlEncoded
    @POST("https://oauth.secure.pixiv.net/auth/token")
    suspend fun exchangeToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("redirect_uri") redirectUri: String,
        @Field("include_policy") includePolicy: String = "true",
        @Header("User-Agent") userAgent: String,
        @Header("App-OS") appOs: String,
        @Header("App-OS-Version") appOsVersion: String,
        @Header("App-Version") appVersion: String,
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String
    ): Response<AuthTokenResponse>

    @FormUrlEncoded
    @POST("https://oauth.secure.pixiv.net/auth/token")
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): Response<AuthTokenResponse>

    @Streaming
    @GET
    suspend fun downloadImage(
        @Url url: String
    ): Response<ResponseBody>
}
