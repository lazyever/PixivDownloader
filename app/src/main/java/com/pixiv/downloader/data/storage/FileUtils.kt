package com.pixiv.downloader.data.storage

import java.net.URI

object FileUtils {

    /**
     * 清理非法文件名字符，防止文件创建失败或路径注入
     */
    fun sanitizeFileName(name: String): String {
        val sanitized = name.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
            .trim()
            .replace(Regex("_+"), "_")
        return if (sanitized.isEmpty()) "Unknown" else sanitized.take(50)
    }

    /**
     * 生成作者子目录名称：作者名称-作者ID
     */
    fun getAuthorFolderName(authorName: String, authorId: String): String {
        val cleanName = sanitizeFileName(authorName)
        val cleanId = sanitizeFileName(authorId)
        return "$cleanName-$cleanId"
    }

    /**
     * 生成标准插画文件名：作品ID_p{index}.{ext}
     */
    fun getIllustFileName(illustId: String, pageIndex: Int, url: String): String {
        val ext = getExtensionFromUrl(url)
        return "${illustId}_p${pageIndex}.$ext"
    }

    /**
     * 生成 Ugoira 动图文件名：作品ID.gif 或 作品ID.mp4
     */
    fun getUgoiraFileName(illustId: String, format: String): String {
        val ext = if (format.equals("GIF", ignoreCase = true)) "gif" else "mp4"
        return "${illustId}.$ext"
    }

    /**
     * 从图片 URL 解析真实后缀名（默认 jpg）
     */
    fun getExtensionFromUrl(url: String): String {
        return try {
            val uri = URI(url)
            val path = uri.path
            val dotIndex = path.lastIndexOf('.')
            if (dotIndex != -1 && dotIndex < path.length - 1) {
                val ext = path.substring(dotIndex + 1).lowercase()
                if (ext in listOf("jpg", "jpeg", "png", "webp", "gif", "mp4")) {
                    return if (ext == "jpeg") "jpg" else ext
                }
            }
            "jpg"
        } catch (e: Exception) {
            "jpg"
        }
    }

    /**
     * 根据文件扩展名返回 MIME 类型
     */
    fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
            fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
            fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            else -> "image/jpeg"
        }
    }
}
