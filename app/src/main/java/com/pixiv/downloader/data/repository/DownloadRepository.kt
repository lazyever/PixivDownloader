package com.pixiv.downloader.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pixiv.downloader.data.api.PixivAuthRequiredException
import com.pixiv.downloader.data.api.PixivClient
import com.pixiv.downloader.data.api.models.ArtworkInfo
import com.pixiv.downloader.data.api.models.UgoiraFrame
import com.pixiv.downloader.data.auth.PixivAuthManager
import com.pixiv.downloader.data.db.AppDatabase
import com.pixiv.downloader.data.db.TaskEntity
import com.pixiv.downloader.data.db.TaskStatus
import com.pixiv.downloader.data.storage.FileUtils
import com.pixiv.downloader.data.storage.StorageManager
import com.pixiv.downloader.service.DownloadNotificationHelper
import com.pixiv.downloader.util.UgoiraSynthesizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import okhttp3.ResponseBody
import retrofit2.Response

class DownloadRepository private constructor(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val taskDao = db.taskDao()
    private val pixivClient = PixivClient.getInstance(context)
    private val authManager = PixivAuthManager.getInstance(context)
    private val storageManager = StorageManager.getInstance(context)
    private val notificationHelper = DownloadNotificationHelper(context)
    private val gson = Gson()

    private val repositoryScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var downloadJob: Job? = null

    @Volatile
    private var isCancelled = false

    val queueTasks: Flow<List<TaskEntity>> =
        taskDao.getQueueTasks()

    val historyTasks: Flow<List<TaskEntity>> =
        taskDao.getHistoryTasks()

    private data class DownloadAttempt(
        val response: Response<ResponseBody>,
        val authType: String,
        val errorBody: String?
    )

    init {
        repositoryScope.launch {
            taskDao.resetDownloadingToPending()
        }
    }

    /**
     * 将作品加入下载队列
     */
    suspend fun enqueueArtwork(
        artwork: ArtworkInfo,
        selectedUgoiraFormat: String = "MP4"
    ): Long {

        val originalUrlsJson =
            gson.toJson(artwork.originalUrls)

        val ugoiraFramesJson =
            if (artwork.isUgoira) {
                gson.toJson(artwork.ugoiraFrames)
            } else {
                null
            }

        val task = TaskEntity(
            illustId = artwork.id,
            title = artwork.title,
            authorName = artwork.authorName,
            authorId = artwork.authorId,
            coverUrl = artwork.coverUrl,
            pageCount = artwork.pageCount,
            downloadedPages = 0,
            status = TaskStatus.PENDING,
            progress = 0f,
            isUgoira = artwork.isUgoira,
            ugoiraFormat = selectedUgoiraFormat,
            ugoiraFramesJson = ugoiraFramesJson,
            ugoiraZipUrl = artwork.ugoiraZipUrl,
            originalUrlsJson = originalUrlsJson,
            authType = artwork.authType
        )

        val id = taskDao.insert(task)
        startQueueProcessing()
        return id
    }

    /**
     * 批量加入队列
     */
    suspend fun enqueueBatchArtworks(
        artworks: List<ArtworkInfo>,
        defaultUgoiraFormat: String = "MP4"
    ): List<Long> {

        val tasks = artworks.map { artwork ->

            TaskEntity(
                illustId = artwork.id,
                title = artwork.title,
                authorName = artwork.authorName,
                authorId = artwork.authorId,
                coverUrl = artwork.coverUrl,
                pageCount = artwork.pageCount,
                downloadedPages = 0,
                status = TaskStatus.PENDING,
                progress = 0f,
                isUgoira = artwork.isUgoira,
                ugoiraFormat = defaultUgoiraFormat,
                ugoiraFramesJson =
                    if (artwork.isUgoira) {
                        gson.toJson(artwork.ugoiraFrames)
                    } else {
                        null
                    },
                ugoiraZipUrl = artwork.ugoiraZipUrl,
                originalUrlsJson = gson.toJson(artwork.originalUrls),
                authType = artwork.authType
            )
        }

        val ids = taskDao.insertAll(tasks)
        startQueueProcessing()
        return ids
    }

    /**
     * 启动下载队列
     */
    fun startQueueProcessing() {

        if (downloadJob?.isActive == true) {
            return
        }

        downloadJob = repositoryScope.launch {

            isCancelled = false

            while (isActive && !isCancelled) {

                val nextTask =
                    taskDao.getNextPendingTask() ?: break

                processTask(nextTask)
            }

            notificationHelper.cancelNotification()
        }
    }

    /**
     * 执行单个下载任务
     */
    private suspend fun processTask(task: TaskEntity) {

        val updatedTask = task.copy(
            status = TaskStatus.DOWNLOADING,
            progress = 0.05f
        )

        taskDao.update(updatedTask)

        try {

            if (task.isUgoira) {
                processUgoiraTask(task)
            } else {
                processIllustTask(task)
            }

        } catch (e: CancellationException) {

            withContext(NonCancellable) {
                taskDao.update(
                    task.copy(
                        status = TaskStatus.PAUSED,
                        errorMessage = "用户手动暂停"
                    )
                )
            }

            throw e

        } catch (e: Exception) {

            val errorMsg =
                e.localizedMessage ?: "下载或合成失败"

            taskDao.update(
                task.copy(
                    status = TaskStatus.FAILED,
                    errorMessage = errorMsg,
                    completedTime = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * 统一图片和 Ugoira ZIP 的认证回退逻辑。
     *
     * 1. 按任务当前认证方式发起请求。
     * 2. 匿名请求只有明确需要登录时才尝试账号请求。
     * 3. 429、404、5xx 等错误不触发登录回退。
     * 4. 记录最终成功使用的认证方式。
     */
    private suspend fun downloadWithAuthFallback(
        url: String,
        currentAuthType: String
    ): DownloadAttempt {

        var actualAuthType =
            if (currentAuthType == "已登录") {
                "已登录"
            } else {
                "匿名"
            }

        var response = pixivClient.downloadStream(
            url,
            useAuth = (actualAuthType == "已登录")
        )

        var errorBody =
            if (!response.isSuccessful) {
                response.errorBody()?.string()
            } else {
                null
            }

        val shouldFallback =
            !response.isSuccessful &&
                actualAuthType == "匿名" &&
                authManager.isLoggedIn() &&
                pixivClient.isAuthRequired(
                    response.code(),
                    errorBody,
                    null
                )

        if (shouldFallback) {

            val authResponse =
                pixivClient.downloadStream(
                    url,
                    useAuth = true
                )

            val authErrorBody =
                if (!authResponse.isSuccessful) {
                    authResponse.errorBody()?.string()
                } else {
                    null
                }

            response = authResponse
            errorBody = authErrorBody

            if (authResponse.isSuccessful) {
                actualAuthType = "已登录"
            }
        }

        return DownloadAttempt(
            response = response,
            authType = actualAuthType,
            errorBody = errorBody
        )
    }

    /**
     * 统一处理下载失败信息
     */
    private fun throwDownloadError(
        code: Int,
        errorBody: String?,
        operation: String
    ): Nothing {

        if (
            pixivClient.isAuthRequired(
                code,
                errorBody,
                null
            )
        ) {

            if (!authManager.isLoggedIn()) {
                throw PixivAuthRequiredException(
                    "${operation}需要 Pixiv 登录或授权，请前往「设置」页面登录。"
                )
            }

            throw PixivAuthRequiredException(
                "${operation}需要登录或授权，但当前账号仍无法访问该资源，请检查账号权限。"
            )
        }

        when (code) {

            404 -> throw IOException(
                "${operation}失败：作品或资源不存在 (HTTP 404)"
            )

            429 -> throw IOException(
                "${operation}请求过于频繁 (HTTP 429 限流)，请稍后再试。"
            )

            in 500..599 -> throw IOException(
                "${operation}服务器异常 (HTTP $code)，请稍后再试。"
            )

            else -> throw IOException(
                "${operation}失败：HTTP $code"
            )
        }
    }

    /**
     * 下载标准插画与多页插画
     */
    private suspend fun processIllustTask(task: TaskEntity) {

        val urlListType =
            object : TypeToken<List<String>>() {}.type

        val urls: List<String> =
            gson.fromJson(task.originalUrlsJson, urlListType)
                ?: emptyList()

        if (urls.isEmpty()) {
            throw IOException("作品原始图片地址为空，无法下载。")
        }

        val totalPages =
            task.pageCount.coerceAtLeast(urls.size).coerceAtLeast(1)

        var downloadedCount = 0
        var allPagesAlreadyExist = true

        var currentAuthType =
            if (task.authType == "已登录") {
                "已登录"
            } else {
                "匿名"
            }

        val savePath =
            storageManager.getDisplaySavePath(
                task.authorName,
                task.authorId
            )

        for (i in urls.indices) {

            if (isCancelled) {
                throw CancellationException("下载任务已取消")
            }

            currentCoroutineContext().ensureActive()

            val url = urls[i]

            val fileName =
                FileUtils.getIllustFileName(
                    task.illustId,
                    i,
                    url
                )

            val mimeType =
                FileUtils.getMimeType(fileName)

            if (
                storageManager.isFileExists(
                    task.authorName,
                    task.authorId,
                    fileName
                )
            ) {

                downloadedCount++

                val curProgress =
                    downloadedCount.toFloat() /
                        totalPages.toFloat()

                taskDao.update(
                    task.copy(
                        downloadedPages = downloadedCount,
                        progress = curProgress,
                        authType = currentAuthType
                    )
                )

                continue
            }

            allPagesAlreadyExist = false

            notificationHelper.updateProgress(
                title = "正在下载: ${task.title}",
                content = "第 ${i + 1}/$totalPages 张 (${(i.toFloat() / totalPages * 100).toInt()}%)",
                progress = (i.toFloat() / totalPages * 100).toInt()
            )

            val attempt =
                downloadWithAuthFallback(
                    url = url,
                    currentAuthType = currentAuthType
                )

            val response = attempt.response
            currentAuthType = attempt.authType

            if (!response.isSuccessful) {

                throwDownloadError(
                    code = response.code(),
                    errorBody = attempt.errorBody,
                    operation = "下载第 ${i + 1} 页图片"
                )
            }

            val body = response.body()
                ?: throw IOException("图片下载响应内容为空。")

            val out =
                storageManager.openOutputStream(
                    task.authorName,
                    task.authorId,
                    fileName,
                    mimeType
                ) ?: throw IOException(
                    "创建目标文件失败: $fileName"
                )

            out.use { outputStream ->

                body.byteStream().use { inputStream ->
                    copyStream(inputStream, outputStream)
                }
            }

            downloadedCount++

            val pageProgress =
                downloadedCount.toFloat() /
                    totalPages.toFloat()

            taskDao.update(
                task.copy(
                    downloadedPages = downloadedCount,
                    progress = pageProgress,
                    authType = currentAuthType
                )
            )
        }

        if (
            allPagesAlreadyExist &&
            downloadedCount == totalPages
        ) {

            taskDao.update(
                task.copy(
                    status = TaskStatus.SKIPPED,
                    progress = 1.0f,
                    authType = currentAuthType,
                    savedPath = savePath,
                    completedTime = System.currentTimeMillis()
                )
            )

        } else {

            taskDao.update(
                task.copy(
                    status = TaskStatus.COMPLETED,
                    progress = 1.0f,
                    authType = currentAuthType,
                    savedPath = savePath,
                    completedTime = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * 下载并合成 Ugoira 动图。
     * 支持 MP4 与 GIF。
     */
    private suspend fun processUgoiraTask(task: TaskEntity) {

        val targetFileName =
            FileUtils.getUgoiraFileName(
                task.illustId,
                task.ugoiraFormat
            )

        val mimeType =
            if (task.ugoiraFormat.equals("GIF", ignoreCase = true)) {
                "image/gif"
            } else {
                "video/mp4"
            }

        val savePath =
            storageManager.getDisplaySavePath(
                task.authorName,
                task.authorId
            )

        if (
            storageManager.isFileExists(
                task.authorName,
                task.authorId,
                targetFileName
            )
        ) {

            taskDao.update(
                task.copy(
                    status = TaskStatus.SKIPPED,
                    progress = 1.0f,
                    savedPath = savePath,
                    completedTime = System.currentTimeMillis()
                )
            )

            return
        }

        val zipUrl = task.ugoiraZipUrl

        if (zipUrl.isNullOrBlank()) {
            throw IOException("Ugoira 原始帧资源地址为空。")
        }

        val framesType =
            object : TypeToken<List<UgoiraFrame>>() {}.type

        val frames: List<UgoiraFrame> =
            gson.fromJson(task.ugoiraFramesJson, framesType)
                ?: emptyList()

        if (frames.isEmpty()) {
            throw IOException("Ugoira 帧序列数据为空。")
        }

        notificationHelper.updateProgress(
            title = "正在下载动图帧包: ${task.title}",
            content = "正在获取原始帧序列...",
            progress = 20
        )

        val tempZipFile = File(
            context.cacheDir,
            "ugoira_${task.illustId}_${System.currentTimeMillis()}.zip"
        )

        val tempFrameDir = File(
            context.cacheDir,
            "frames_${task.illustId}_${System.currentTimeMillis()}"
        )

        var currentAuthType =
            if (task.authType == "已登录") {
                "已登录"
            } else {
                "匿名"
            }

        try {

            // 1. 下载 Ugoira ZIP
            val attempt =
                downloadWithAuthFallback(
                    url = zipUrl,
                    currentAuthType = currentAuthType
                )

            val response = attempt.response
            currentAuthType = attempt.authType

            if (!response.isSuccessful) {

                throwDownloadError(
                    code = response.code(),
                    errorBody = attempt.errorBody,
                    operation = "下载 Ugoira 动图帧包"
                )
            }

            val responseBody = response.body()
                ?: throw IOException("Ugoira ZIP 下载内容为空。")

            FileOutputStream(tempZipFile).use { fos ->

                responseBody.byteStream().use { fis ->
                    copyStream(fis, fos)
                }
            }

            currentCoroutineContext().ensureActive()

            notificationHelper.updateProgress(
                title = "正在合成动图: ${task.title}",
                content = "解压帧数据中...",
                progress = 40
            )

            // 2. 解压所有帧
            UgoiraSynthesizer.unzipFrames(
                tempZipFile,
                tempFrameDir
            )

            currentCoroutineContext().ensureActive()

            // 3. 创建目标文件输出流
            val out =
                storageManager.openOutputStream(
                    task.authorName,
                    task.authorId,
                    targetFileName,
                    mimeType
                ) ?: throw IOException(
                    "创建输出文件失败: $targetFileName"
                )

            out.use { outputStream ->

                if (
                    task.ugoiraFormat.equals(
                        "GIF",
                        ignoreCase = true
                    )
                ) {

                    notificationHelper.updateProgress(
                        title = "正在合成 GIF: ${task.title}",
                        content = "逐帧编码色彩与延迟...",
                        progress = 60
                    )

                    UgoiraSynthesizer.synthesizeGif(
                        tempFrameDir,
                        frames,
                        outputStream
                    ) { p ->

                        val synthProgress =
                            0.5f + (p * 0.5f)

                        repositoryScope.launch {
                            taskDao.update(
                                task.copy(
                                    progress = synthProgress,
                                    authType = currentAuthType
                                )
                            )
                        }
                    }

                } else {

                    notificationHelper.updateProgress(
                        title = "正在合成 MP4: ${task.title}",
                        content = "硬件编码视频流...",
                        progress = 60
                    )

                    UgoiraSynthesizer.synthesizeMp4(
                        tempFrameDir,
                        frames,
                        outputStream
                    ) { p ->

                        val synthProgress =
                            0.5f + (p * 0.5f)

                        repositoryScope.launch {
                            taskDao.update(
                                task.copy(
                                    progress = synthProgress,
                                    authType = currentAuthType
                                )
                            )
                        }
                    }
                }
            }

            // 4. 完成标记
            taskDao.update(
                task.copy(
                    status = TaskStatus.COMPLETED,
                    progress = 1.0f,
                    authType = currentAuthType,
                    savedPath = savePath,
                    completedTime = System.currentTimeMillis()
                )
            )

        } finally {

            tempZipFile.delete()
            tempFrameDir.deleteRecursively()
        }
    }

    /**
     * 流复制
     */
    private fun copyStream(
        input: InputStream,
        output: OutputStream
    ) {

        val buffer = ByteArray(16384)
        var bytesRead: Int

        while (
            input.read(buffer).also {
                bytesRead = it
            } != -1
        ) {

            output.write(
                buffer,
                0,
                bytesRead
            )
        }

        output.flush()
    }

    /**
     * 暂停任务
     */
    suspend fun pauseTask(taskId: Long) {

        val task =
            taskDao.getTaskById(taskId) ?: return

        taskDao.update(
            task.copy(status = TaskStatus.PAUSED)
        )
    }

    /**
     * 恢复任务
     */
    suspend fun resumeTask(taskId: Long) {

        val task =
            taskDao.getTaskById(taskId) ?: return

        taskDao.update(
            task.copy(
                status = TaskStatus.PENDING,
                errorMessage = null
            )
        )

        startQueueProcessing()
    }

    /**
     * 重试任务
     */
    suspend fun retryTask(taskId: Long) {

        val task =
            taskDao.getTaskById(taskId) ?: return

        taskDao.update(
            task.copy(
                status = TaskStatus.PENDING,
                progress = 0f,
                errorMessage = null
            )
        )

        startQueueProcessing()
    }

    /**
     * 取消并删除任务
     */
    suspend fun cancelTask(taskId: Long) {
        taskDao.deleteById(taskId)
    }

    /**
     * 清空下载历史
     */
    suspend fun clearHistory() {
        taskDao.clearCompletedHistory()
    }

    companion object {

        @Volatile
        private var instance: DownloadRepository? = null

        fun getInstance(context: Context): DownloadRepository {

            return instance ?: synchronized(this) {

                instance ?: DownloadRepository(
                    context.applicationContext
                ).also {
                    instance = it
                }
            }
        }
    }
}
