package com.pixiv.downloader.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.pixiv.downloader.data.repository.SettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class StorageManager private constructor(private val context: Context) {

    private val settingsRepository = SettingsRepository.getInstance(context)

    /**
     * 判断目标文件是否已存在（且大小有效）
     */
    fun isFileExists(
        authorName: String,
        authorId: String,
        fileName: String
    ): Boolean {
        val customUriString = settingsRepository.settingsState.value.customUriString
        val authorFolder = FileUtils.getAuthorFolderName(authorName, authorId)

        if (!customUriString.isNullOrEmpty()) {
            // SAF 自定义目录
            val treeUri = Uri.parse(customUriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            val authorDoc = rootDoc.findFile(authorFolder) ?: return false
            val fileDoc = authorDoc.findFile(fileName) ?: return false
            return fileDoc.exists() && fileDoc.length() > 0
        } else {
            // 默认目录：Pictures/Pixiv/作者名称-作者ID/
            val pixivDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Pixiv/$authorFolder"
            )
            val targetFile = File(pixivDir, fileName)
            return targetFile.exists() && targetFile.length() > 0
        }
    }

    /**
     * 打开目标输出流，准备写入图片或合成文件
     */
    fun openOutputStream(
        authorName: String,
        authorId: String,
        fileName: String,
        mimeType: String
    ): OutputStream? {
        val customUriString = settingsRepository.settingsState.value.customUriString
        val authorFolder = FileUtils.getAuthorFolderName(authorName, authorId)

        if (!customUriString.isNullOrEmpty()) {
            // 使用 SAF DocumentFile
            val treeUri = Uri.parse(customUriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IllegalStateException("无法访问自定义保存目录，请重新授权。")

            // 获取或创建作者子目录
            val authorDoc = rootDoc.findFile(authorFolder)
                ?: rootDoc.createDirectory(authorFolder)
                ?: throw IllegalStateException("创建作者目录失败: $authorFolder")

            // 若存在则删除旧文件避免冲突
            authorDoc.findFile(fileName)?.delete()

            val newFileDoc = authorDoc.createFile(mimeType, fileName)
                ?: throw IllegalStateException("创建文件失败: $fileName")

            return context.contentResolver.openOutputStream(newFileDoc.uri)
        } else {
            // 默认目录：Pictures/Pixiv/作者名称-作者ID/
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = "Pictures/Pixiv/$authorFolder/"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collectionUri = if (mimeType.startsWith("video/")) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val uri = context.contentResolver.insert(collectionUri, contentValues)
                    ?: throw IllegalStateException("无法向媒体库写入文件: $fileName")

                val out = context.contentResolver.openOutputStream(uri)
                return object : OutputStream() {
                    override fun write(b: Int) = out!!.write(b)
                    override fun write(b: ByteArray, off: Int, len: Int) = out!!.write(b, off, len)
                    override fun flush() = out!!.flush()
                    override fun close() {
                        out!!.close()
                        // 解除 IS_PENDING 状态
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        context.contentResolver.update(uri, values, null, null)
                    }
                }
            } else {
                val pixivDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Pixiv/$authorFolder"
                )
                if (!pixivDir.exists()) pixivDir.mkdirs()
                val targetFile = File(pixivDir, fileName)
                return FileOutputStream(targetFile)
            }
        }
    }

    /**
     * 获取显示给用户的保存路径文本
     */
    fun getDisplaySavePath(authorName: String, authorId: String): String {
        val authorFolder = FileUtils.getAuthorFolderName(authorName, authorId)
        val customUriString = settingsRepository.settingsState.value.customUriString
        return if (!customUriString.isNullOrEmpty()) {
            "自定义目录/Pixiv/$authorFolder/"
        } else {
            "Pictures/Pixiv/$authorFolder/"
        }
    }

    companion object {
        @Volatile
        private var instance: StorageManager? = null

        fun getInstance(context: Context): StorageManager {
            return instance ?: synchronized(this) {
                instance ?: StorageManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
