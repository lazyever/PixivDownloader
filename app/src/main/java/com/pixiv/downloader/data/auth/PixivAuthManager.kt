package com.pixiv.downloader.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pixiv.downloader.data.api.models.AuthTokenResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

class PixivAuthManager private constructor(context: Context) {

    private val prefs: SharedPreferences = createPreferences(context)

    data class UserProfile(
        val isLoggedIn: Boolean,
        val userId: String? = null,
        val userName: String? = null,
        val account: String? = null,
        val avatarUrl: String? = null
    )

    private val _userProfileState = MutableStateFlow(UserProfile(isLoggedIn = false))
    val userProfileState: StateFlow<UserProfile> = _userProfileState.asStateFlow()

    init {
        loadCurrentProfile()
    }

    private fun loadCurrentProfile() {
        try {
            val token = prefs.getString(KEY_ACCESS_TOKEN, null)
            val isLoggedIn = !token.isNullOrEmpty()
            val userId = prefs.getString(KEY_USER_ID, null)
            val userName = prefs.getString(KEY_USER_NAME, null)
            val account = prefs.getString(KEY_USER_ACCOUNT, null)
            val avatarUrl = prefs.getString(KEY_AVATAR_URL, null)

            _userProfileState.value = UserProfile(
                isLoggedIn = isLoggedIn,
                userId = userId,
                userName = userName,
                account = account,
                avatarUrl = avatarUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "加载用户配置失败，已重置为默认状态", e)
            _userProfileState.value = UserProfile(isLoggedIn = false)
        }
    }

    fun isLoggedIn(): Boolean = _userProfileState.value.isLoggedIn

    fun getAccessToken(): String? {
        return try {
            prefs.getString(KEY_ACCESS_TOKEN, null)
        } catch (e: Exception) {
            null
        }
    }

    fun getRefreshToken(): String? {
        return try {
            prefs.getString(KEY_REFRESH_TOKEN, null)
        } catch (e: Exception) {
            null
        }
    }

    fun saveTokenResponse(response: AuthTokenResponse) {
        try {
            val editor = prefs.edit()
            response.accessToken?.let { editor.putString(KEY_ACCESS_TOKEN, it) }
            response.refreshToken?.let { editor.putString(KEY_REFRESH_TOKEN, it) }
            val expiresAt = System.currentTimeMillis() + ((response.expiresIn ?: 3600L) * 1000L)
            editor.putLong(KEY_EXPIRES_AT, expiresAt)

            response.user?.let { user ->
                editor.putString(KEY_USER_ID, user.id)
                editor.putString(KEY_USER_NAME, user.name)
                editor.putString(KEY_USER_ACCOUNT, user.account)
                user.profileImageUrls?.px170x170?.let { editor.putString(KEY_AVATAR_URL, it) }
                    ?: user.profileImageUrls?.px50x50?.let { editor.putString(KEY_AVATAR_URL, it) }
            }
            editor.apply()
            loadCurrentProfile()
        } catch (e: Exception) {
            Log.e(TAG, "保存 Token 异常", e)
        }
    }

    fun logout() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "清除凭据异常", e)
        }
        loadCurrentProfile()
    }

    fun generateLoginUrl(): Pair<String, String> {
        val pkce = PKCEUtil.generatePKCE()
        val url = "https://app-api.pixiv.net/web/v1/login" +
                "?code_challenge=${URLEncoder.encode(pkce.codeChallenge, "UTF-8")}" +
                "&code_challenge_method=S256" +
                "&client=pixiv-android"
        return Pair(url, pkce.codeVerifier)
    }

    companion object {
        private const val TAG = "PixivAuthManager"
        private const val PREFS_NAME = "pixiv_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "pixiv_app_prefs_fallback"

        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ACCOUNT = "user_account"
        private const val KEY_AVATAR_URL = "avatar_url"

        // Pixiv Android OAuth application credentials.
        const val CLIENT_ID = "MOBrBDS8blbauoSck0ZfDbtuzpyT"
        const val CLIENT_SECRET = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj"
        const val REDIRECT_URI = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback"

        @Volatile
        private var instance: PixivAuthManager? = null

        fun getInstance(context: Context): PixivAuthManager {
            return instance ?: synchronized(this) {
                instance ?: PixivAuthManager(context.applicationContext).also { instance = it }
            }
        }

        /**
         * 安全初始化 SharedPreferences：
         * 优先使用 EncryptedSharedPreferences (AES256)。
         * 若因特定机型（如小米 HyperOS / Android 15 Keystore 兼容性差异）抛出 KeyStoreException 或解密失败，
         * 自动容错降级为应用沙盒私有存储，绝不引发 App 启动闪退。
         */
        private fun createPreferences(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Throwable) {
                Log.w(TAG, "系统密钥库不可用或不支持 AES256_SIV，启用沙盒私有存储容灾模式", e)
                try {
                    context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
                } catch (fallbackEx: Throwable) {
                    context.getSharedPreferences(PREFS_NAME + "_compat", Context.MODE_PRIVATE)
                }
            }
        }
    }
}
