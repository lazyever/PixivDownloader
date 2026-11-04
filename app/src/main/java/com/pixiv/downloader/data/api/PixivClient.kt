package com.pixiv.downloader.data.api

import android.content.Context
import com.pixiv.downloader.data.api.models.ArtworkInfo
import com.pixiv.downloader.data.api.models.AuthTokenResponse
import com.pixiv.downloader.data.auth.PixivAuthManager
import kotlinx.coroutines.CancellationException
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class PixivClient private constructor(private val context: Context) {

    private val authManager = PixivAuthManager.getInstance(context)

    private val dispatcher = Dispatcher().apply {
        maxRequests = 4
        maxRequestsPerHost = 2
    }

    private fun createBaseInterceptor(): Interceptor =
        Interceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()

            // 只补齐默认请求头，不覆盖 Retrofit 为具体作品/OAuth 请求设置的请求头。
            if (original.header("User-Agent").isNullOrBlank()) {
                builder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
            }
            if (original.header("Referer").isNullOrBlank()) {
                builder.header("Referer", "https://www.pixiv.net/")
            }
            if (original.header("Accept-Language").isNullOrBlank()) {
                builder.header(
                    "Accept-Language",
                    "zh-CN,zh;q=0.9,ja;q=0.8,en;q=0.7"
                )
            }

            chain.proceed(builder.build())
        }

    // 匿名客户端：不携带 Token 和 Cookie
    val anonymousOkHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .cookieJar(CookieJar.NO_COOKIES)
            .addInterceptor(createBaseInterceptor())
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .removeHeader("Authorization")
                    .removeHeader("Cookie")
                    .build()

                chain.proceed(request)
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    // 已登录客户端：只向 Pixiv 网站 API 注入 OAuth Token
    val authenticatedOkHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .cookieJar(CookieJar.NO_COOKIES)
            .addInterceptor(createBaseInterceptor())
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()

                val isPixivApi =
                    original.url.host.equals(
                        "www.pixiv.net",
                        ignoreCase = true
                    )

                val token = authManager.getAccessToken()

                if (
                    isPixivApi &&
                    !token.isNullOrBlank() &&
                    original.header("Authorization") == null
                ) {
                    builder.header("Authorization", "Bearer $token")
                }

                if (!isPixivApi) {
                    builder.removeHeader("Authorization")
                }

                chain.proceed(builder.build())
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    // 默认公开客户端，供 Coil 等使用
    val okHttpClient: OkHttpClient
        get() = anonymousOkHttpClient

    private val anonymousRetrofit: Retrofit =
        Retrofit.Builder()
            .baseUrl("https://www.pixiv.net/")
            .client(anonymousOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private val authenticatedRetrofit: Retrofit =
        Retrofit.Builder()
            .baseUrl("https://www.pixiv.net/")
            .client(authenticatedOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    val anonymousApiService: PixivApiService =
        anonymousRetrofit.create(PixivApiService::class.java)

    val authenticatedApiService: PixivApiService =
        authenticatedRetrofit.create(PixivApiService::class.java)

    // 兼容原有属性引用
    val apiService: PixivApiService
        get() = anonymousApiService

    /**
     * 判断错误是否确实属于需要登录或授权受限。
     *
     * 401：明确未授权，可以触发登录回退。
     * 403：必须有明确的登录提示。
     * 200：仅检查 Ajax 错误信息中的明确登录提示。
     * 404、429、5xx：绝不触发登录回退。
     */
    fun isAuthRequired(
        statusCode: Int,
        errorBody: String?,
        apiMessage: String?
    ): Boolean {

        if (statusCode == 401) {
            return true
        }

        if (
            statusCode == 404 ||
            statusCode == 429 ||
            statusCode in 500..599
        ) {
            return false
        }

        if (statusCode != 403 && statusCode != 200) {
            return false
        }

        val textToInspect = buildString {
            if (!apiMessage.isNullOrBlank()) {
                append(apiMessage).append(" ")
            }

            if (!errorBody.isNullOrBlank()) {
                append(errorBody)
            }
        }.lowercase()

        if (textToInspect.isBlank()) {
            return false
        }

        val authKeywords = listOf(
            "login_required",
            "login required",
            "requires login",
            "require login",
            "authentication required",
            "unauthorized",
            "please log in",
            "please login",
            "ログインが必要",
            "ログインしてください",
            "ログインしてから",
            "请先登录",
            "需要登录",
            "请登录",
            "登录后"
        )

        return authKeywords.any { keyword ->
            textToInspect.contains(keyword)
        }
    }

    /**
     * 获取作品信息。
     * 默认优先匿名请求。
     * 只有明确需要登录时，才尝试使用已登录账号。
     */
    suspend fun fetchArtwork(
        illustId: String,
        preferAnonymous: Boolean = true
    ): Result<ArtworkInfo> {

        return try {
            if (preferAnonymous) {

                try {
                    val artwork = fetchArtworkWithService(
                        illustId = illustId,
                        service = anonymousApiService,
                        isAnonymous = true
                    )

                    Result.success(artwork)

                } catch (e: CancellationException) {
                    throw e

                } catch (e: PixivAuthRequiredException) {

                    if (!authManager.isLoggedIn()) {
                        return Result.failure(
                            PixivAuthRequiredException(
                                "该作品需要 Pixiv 登录或授权，请前往设置页面登录账号。"
                            )
                        )
                    }

                    try {
                        val artwork = fetchArtworkWithService(
                            illustId = illustId,
                            service = authenticatedApiService,
                            isAnonymous = false
                        )

                        Result.success(artwork)

                    } catch (authException: CancellationException) {
                        throw authException
                    } catch (authException: Exception) {
                        Result.failure(authException)
                    }

                } catch (e: Exception) {
                    Result.failure(normalizeNetworkException(e))
                }

            } else {

                val useAuthenticated = authManager.isLoggedIn()

                val service =
                    if (useAuthenticated) {
                        authenticatedApiService
                    } else {
                        anonymousApiService
                    }

                val artwork = fetchArtworkWithService(
                    illustId = illustId,
                    service = service,
                    isAnonymous = !useAuthenticated
                )

                Result.success(artwork)
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(normalizeNetworkException(e))
        }
    }

    /**
     * 使用指定服务获取作品详情。
     */
    private suspend fun fetchArtworkWithService(
        illustId: String,
        service: PixivApiService,
        isAnonymous: Boolean
    ): ArtworkInfo {

        val artworkReferer = "https://www.pixiv.net/artworks/$illustId"
        val response = service.getIllustAjax(
            id = illustId,
            referer = artworkReferer
        )
        val code = response.code()

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()

            if (isAuthRequired(code, errorBody, null)) {
                throw PixivAuthRequiredException(
                    "作品访问受限，需要登录 Pixiv 账号。"
                )
            }

            throw createHttpException(
                code = code,
                operation = "获取作品详情"
            )
        }

        val ajaxResponse = response.body()
            ?: throw IOException("未能获取到作品响应数据")

        if (ajaxResponse.error) {
            val message = ajaxResponse.message

            if (isAuthRequired(200, null, message)) {
                throw PixivAuthRequiredException(
                    message.ifBlank {
                        "该作品需要登录 Pixiv 账号。"
                    }
                )
            }

            throw IOException(
                message.ifBlank {
                    "获取作品详情失败"
                }
            )
        }

        val data = ajaxResponse.body
            ?: throw IOException("作品详细数据为空")

        val title = data.illustTitle.ifEmpty {
            "未命名作品_$illustId"
        }

        val authorName = data.userName.ifEmpty {
            "未知画师"
        }

        val authorId = data.userId

        val pageCount = data.pageCount.coerceAtLeast(1)

        val coverUrl =
            data.urls?.regular
                ?: data.urls?.small
                ?: data.urls?.thumb
                ?: ""

        val isUgoira = data.illustType == 2

        val authType =
            if (isAnonymous) "匿名" else "已登录"

        // Ugoira 动图
        if (isUgoira) {

            val ugoiraResponse = service.getUgoiraMeta(
                id = illustId,
                referer = artworkReferer
            )

            val ugoiraCode = ugoiraResponse.code()

            if (!ugoiraResponse.isSuccessful) {
                val errorBody =
                    ugoiraResponse.errorBody()?.string()

                if (
                    isAuthRequired(
                        ugoiraCode,
                        errorBody,
                        null
                    )
                ) {
                    throw PixivAuthRequiredException(
                        "Ugoira 元数据访问受限，需要登录账号。"
                    )
                }

                // 保留 Pixiv 返回的错误正文，便于区分作品不存在、接口拒绝和网关错误。
                // 之前只显示通用 HTTP 404，无法判断 404 是 Pixiv 本身返回还是中间网络层返回。
                val detail = errorBody
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?.take(400)
                    ?.takeIf { it.isNotBlank() }

                val message = buildString {
                    append("获取 Ugoira 元数据失败：HTTP ")
                    append(ugoiraCode)
                    if (detail != null) {
                        append("；服务器返回：")
                        append(detail)
                    }
                }

                throw IOException(message)
            }

            val metaResponse = ugoiraResponse.body()
                ?: throw IOException("Ugoira 元数据响应为空")

            if (metaResponse.error) {
                if (
                    isAuthRequired(
                        200,
                        null,
                        metaResponse.message
                    )
                ) {
                    throw PixivAuthRequiredException(
                        metaResponse.message.ifBlank {
                            "Ugoira 元数据需要登录账号。"
                        }
                    )
                }

                throw IOException(
                    metaResponse.message.ifBlank {
                        "获取 Ugoira 元数据失败"
                    }
                )
            }

            val metaBody = metaResponse.body
                ?: throw IOException("Ugoira 元数据内容为空")

            val frames = metaBody.frames
                ?: emptyList()

            val zipUrl =
                metaBody.originalSrc
                    ?: metaBody.src
                    ?: ""

            if (frames.isEmpty()) {
                throw IOException("Ugoira 动图帧数据为空")
            }

            if (zipUrl.isBlank()) {
                throw IOException("Ugoira ZIP 下载地址为空")
            }

            return ArtworkInfo(
                id = illustId,
                title = title,
                authorName = authorName,
                authorId = authorId,
                coverUrl = coverUrl,
                pageCount = 1,
                originalUrls = emptyList(),
                isUgoira = true,
                ugoiraFrames = frames,
                ugoiraZipUrl = zipUrl,
                authType = authType
            )
        }

        // 多页插画或漫画
        val originalUrls = mutableListOf<String>()

        if (pageCount > 1 || data.urls?.original.isNullOrBlank()) {
            // 多页作品必须读取 pages；单页作品仅在详情接口没有 original 时才回退查询。
            val pagesResponse = service.getIllustPages(
                id = illustId,
                referer = artworkReferer
            )

            val pagesCode = pagesResponse.code()

            if (!pagesResponse.isSuccessful) {
                val errorBody =
                    pagesResponse.errorBody()?.string()

                if (
                    isAuthRequired(
                        pagesCode,
                        errorBody,
                        null
                    )
                ) {
                    throw PixivAuthRequiredException(
                        "多页图片信息访问受限，需要登录账号。"
                    )
                }

                throw createHttpException(
                    code = pagesCode,
                    operation = "获取多页图片信息"
                )
            }

            val pagesBody = pagesResponse.body()
                ?: throw IOException("多页图片响应为空")

            if (pagesBody.error) {
                if (
                    isAuthRequired(
                        200,
                        null,
                        pagesBody.message
                    )
                ) {
                    throw PixivAuthRequiredException(
                        pagesBody.message.ifBlank {
                            "多页图片信息需要登录账号。"
                        }
                    )
                }

                throw IOException(
                    pagesBody.message.ifBlank {
                        "获取多页图片信息失败"
                    }
                )
            }

            val pageItems = pagesBody.body
                ?: throw IOException("多页图片数据为空")

            pageItems.forEach { pageItem ->
                pageItem.urls?.original?.let { url ->
                    if (url.isNotBlank()) {
                        originalUrls.add(url)
                    }
                }
            }

            if (pageCount > 1 && originalUrls.size < pageCount) {
                throw IOException(
                    "多页图片信息不完整：预期 $pageCount 页，实际获取 ${originalUrls.size} 页"
                )
            }
        }

        // 单页图片地址，或多页接口的兜底地址
        if (originalUrls.isEmpty()) {

            val primaryOriginal = data.urls?.original

            if (!primaryOriginal.isNullOrBlank()) {

                originalUrls.add(primaryOriginal)

                if (
                    pageCount > 1 &&
                    primaryOriginal.contains("_p0.")
                ) {
                    for (i in 1 until pageCount) {
                        val nextUrl =
                            primaryOriginal.replace(
                                "_p0.",
                                "_p${i}."
                            )

                        originalUrls.add(nextUrl)
                    }
                }
            }
        }

        if (originalUrls.isEmpty()) {
            throw IOException(
                "未能提取到该作品的原始图片地址"
            )
        }

        return ArtworkInfo(
            id = illustId,
            title = title,
            authorName = authorName,
            authorId = authorId,
            coverUrl = coverUrl,
            pageCount = pageCount,
            originalUrls = originalUrls,
            isUgoira = false,
            authType = authType
        )
    }

    /**
     * 统一处理 HTTP 错误。
     */
    private fun createHttpException(
        code: Int,
        operation: String
    ): IOException {

        return when (code) {
            404 -> IOException(
                "${operation}失败：作品或资源不存在 (HTTP 404)"
            )

            429 -> IOException(
                "${operation}失败：请求过于频繁，请稍后再试 (HTTP 429)"
            )

            in 500..599 -> IOException(
                "${operation}失败：Pixiv 服务器异常 (HTTP $code)"
            )

            else -> IOException(
                "${operation}失败：HTTP $code"
            )
        }
    }

    /**
     * 将常见网络异常转换成更容易理解的提示。
     */
    private fun normalizeNetworkException(
        exception: Exception
    ): Exception {

        return when (exception) {
            is java.net.SocketTimeoutException ->
                IOException(
                    "网络连接超时，请检查网络或代理设置。",
                    exception
                )

            is java.net.UnknownHostException ->
                IOException(
                    "DNS 解析失败，请检查网络或代理设置。",
                    exception
                )

            is java.net.ConnectException ->
                IOException(
                    "无法连接 Pixiv 服务器，请检查网络或代理设置。",
                    exception
                )

            else -> exception
        }
    }

    /**
     * OAuth 授权 Code 换取 Access Token。
     */
    suspend fun exchangeToken(
        code: String,
        verifier: String
    ): Result<AuthTokenResponse> {

        return try {
            val clientTime = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.US
            ).apply {
                timeZone = TimeZone.getDefault()
            }.format(Date())

            val hashSecret = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"
            val clientHash = MessageDigest.getInstance("MD5")
                .digest((clientTime + hashSecret).toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

            val response = authenticatedApiService.exchangeToken(
                clientId = PixivAuthManager.CLIENT_ID,
                clientSecret = PixivAuthManager.CLIENT_SECRET,
                code = code,
                codeVerifier = verifier,
                redirectUri = PixivAuthManager.REDIRECT_URI,
                includePolicy = "true",
                userAgent = "PixivAndroidApp/5.0.234 (Android ${android.os.Build.VERSION.RELEASE}; ${android.os.Build.MODEL})",
                appOs = "android",
                appOsVersion = android.os.Build.VERSION.RELEASE,
                appVersion = "5.0.234",
                clientTime = clientTime,
                clientHash = clientHash
            )

            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!

                authManager.saveTokenResponse(tokenResponse)

                Result.success(tokenResponse)

            } else {
                Result.failure(
                    IOException(
                        "Pixiv 授权失败：HTTP ${response.code()} " +
                            (response.errorBody()?.string() ?: "")
                    )
                )
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(normalizeNetworkException(e))
        }
    }

    /**
     * 下载图片或 Ugoira ZIP。
     *
     * useAuth = false：严格匿名请求。
     * useAuth = true：使用已登录账号 Token。
     */
    suspend fun downloadStream(
        url: String,
        useAuth: Boolean = false
    ): Response<ResponseBody> {

        return if (useAuth) {
            authenticatedApiService.downloadImage(url)
        } else {
            anonymousApiService.downloadImage(url)
        }
    }

    companion object {

        @Volatile
        private var instance: PixivClient? = null

        fun getInstance(context: Context): PixivClient {
            return instance ?: synchronized(this) {
                instance ?: PixivClient(
                    context.applicationContext
                ).also {
                    instance = it
                }
            }
        }
    }
}

class PixivAuthRequiredException(
    message: String
) : IOException(message)
