package com.pixiv.downloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pixiv.downloader.data.db.TaskEntity
import com.pixiv.downloader.data.db.TaskStatus
import com.pixiv.downloader.ui.theme.PixivBlue
import com.pixiv.downloader.util.PixivImageLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskItemCard(
    task: TaskEntity,
    isHistoryMode: Boolean = false,
    onPauseClick: (() -> Unit)? = null,
    onResumeClick: (() -> Unit)? = null,
    onRetryClick: (() -> Unit)? = null,
    onCancelClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onOpenFolderClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 封面
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = PixivImageLoader.buildRequest(context, task.coverUrl),
                        imageLoader = PixivImageLoader.get(context),
                        contentDescription = task.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    val typeLabel = if (task.isUgoira) task.ugoiraFormat else "${task.pageCount}P"
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(topStart = 6.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // 标题与信息
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "作者: ${task.authorName} (${task.authorId})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 状态与认证方式展示
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusBadge(status = task.status)

                        // 实际使用的认证方式徽章（匿名 / 已登录）
                        Surface(
                            color = if (task.authType == "已登录") PixivBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = task.authType.ifEmpty { "匿名" },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (task.authType == "已登录") PixivBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (task.status == TaskStatus.DOWNLOADING) {
                            Text(
                                text = if (task.isUgoira) "动图处理中 ${(task.progress * 100).toInt()}%"
                                else "已下 ${task.downloadedPages}/${task.pageCount} 张",
                                style = MaterialTheme.typography.labelSmall,
                                color = PixivBlue
                            )
                        } else if (task.status == TaskStatus.COMPLETED && !task.savedPath.isNullOrEmpty()) {
                            Text(
                                text = task.savedPath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 进度条（进行中）
            if (task.status == TaskStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { task.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = PixivBlue
                )
            }

            // 错误提示
            if (task.status == TaskStatus.FAILED && !task.errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "失败原因: ${task.errorMessage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 操作按钮条
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isHistoryMode) {
                    when (task.status) {
                        TaskStatus.DOWNLOADING -> {
                            IconButton(onClick = { onPauseClick?.invoke() }) {
                                Icon(Icons.Default.Pause, contentDescription = "暂停", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        TaskStatus.PAUSED -> {
                            IconButton(onClick = { onResumeClick?.invoke() }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "继续", tint = PixivBlue)
                            }
                        }
                        TaskStatus.PENDING -> {
                            // 等待中
                        }
                        else -> {}
                    }
                    IconButton(onClick = { onCancelClick?.invoke() }) {
                        Icon(Icons.Default.Close, contentDescription = "取消/移除", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    // 历史记录模式
                    if (task.status == TaskStatus.FAILED) {
                        TextButton(onClick = { onRetryClick?.invoke() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重新下载")
                        }
                    }

                    if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.SKIPPED) {
                        TextButton(onClick = { onOpenFolderClick?.invoke() }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("查看目录")
                        }
                    }

                    IconButton(onClick = { onDeleteClick?.invoke() }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除记录", tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: TaskStatus) {
    val (bgColor, textColor, label) = when (status) {
        TaskStatus.PENDING -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "排队中")
        TaskStatus.DOWNLOADING -> Triple(PixivBlue.copy(alpha = 0.15f), PixivBlue, "下载中")
        TaskStatus.PAUSED -> Triple(Color(0xFFFFB74D).copy(alpha = 0.2f), Color(0xFFE65100), "已暂停")
        TaskStatus.COMPLETED -> Triple(Color(0xFF81C784).copy(alpha = 0.2f), Color(0xFF2E7D32), "已完成")
        TaskStatus.FAILED -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "失败")
        TaskStatus.SKIPPED -> Triple(Color(0xFF90CAF9).copy(alpha = 0.2f), Color(0xFF1565C0), "已跳过存在")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
