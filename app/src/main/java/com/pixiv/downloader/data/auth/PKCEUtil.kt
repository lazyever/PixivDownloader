package com.pixiv.downloader.data.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object PKCEUtil {

    data class PKCECodes(
        val codeVerifier: String,
        val codeChallenge: String
    )

    fun generatePKCE(): PKCECodes {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val codeVerifier = Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        val codeChallenge = Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        return PKCECodes(codeVerifier, codeChallenge)
    }
}
