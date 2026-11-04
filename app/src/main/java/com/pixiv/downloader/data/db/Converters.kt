package com.pixiv.downloader.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStatus(status: TaskStatus?): String {
        return status?.name ?: TaskStatus.PENDING.name
    }

    @TypeConverter
    fun toStatus(value: String?): TaskStatus {
        return try {
            if (value != null) TaskStatus.valueOf(value) else TaskStatus.PENDING
        } catch (e: Exception) {
            TaskStatus.PENDING
        }
    }
}
