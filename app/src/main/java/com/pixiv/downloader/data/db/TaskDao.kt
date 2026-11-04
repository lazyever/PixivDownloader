package com.pixiv.downloader.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM download_tasks WHERE status IN ('PENDING', 'DOWNLOADING', 'PAUSED') ORDER BY id ASC")
    fun getQueueTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status IN ('COMPLETED', 'FAILED', 'SKIPPED') ORDER BY COALESCE(completedTime, createTime) DESC")
    fun getHistoryTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM download_tasks ORDER BY id DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Query("SELECT * FROM download_tasks WHERE illustId = :illustId ORDER BY id DESC LIMIT 1")
    suspend fun findTaskByIllustId(illustId: String): TaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status = 'PENDING' ORDER BY id ASC LIMIT 1")
    suspend fun getNextPendingTask(): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM download_tasks WHERE status IN ('COMPLETED', 'SKIPPED')")
    suspend fun clearCompletedHistory()

    @Query("UPDATE download_tasks SET status = 'PENDING' WHERE status = 'DOWNLOADING'")
    suspend fun resetDownloadingToPending()
}
