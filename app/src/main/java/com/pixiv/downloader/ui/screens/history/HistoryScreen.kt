package com.pixiv.downloader.ui.screens.history

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pixiv.downloader.ui.components.TaskItemCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentFilter by viewModel.currentFilter.collectAsState()
    val tasks by viewModel.filteredHistoryTasks.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // 筛选标签栏与清空历史
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScrollableTabRow(
                selectedTabIndex = currentFilter.ordinal,
                edgePadding = 0.dp,
                modifier = Modifier.weight(1f),
                divider = {}
            ) {
                Tab(
                    selected = currentFilter == HistoryViewModel.HistoryFilter.ALL,
                    onClick = { viewModel.setFilter(HistoryViewModel.HistoryFilter.ALL) },
                    text = { Text("全部") }
                )
                Tab(
                    selected = currentFilter == HistoryViewModel.HistoryFilter.COMPLETED,
                    onClick = { viewModel.setFilter(HistoryViewModel.HistoryFilter.COMPLETED) },
                    text = { Text("成功") }
                )
                Tab(
                    selected = currentFilter == HistoryViewModel.HistoryFilter.FAILED,
                    onClick = { viewModel.setFilter(HistoryViewModel.HistoryFilter.FAILED) },
                    text = { Text("失败") }
                )
                Tab(
                    selected = currentFilter == HistoryViewModel.HistoryFilter.SKIPPED,
                    onClick = { viewModel.setFilter(HistoryViewModel.HistoryFilter.SKIPPED) },
                    text = { Text("已跳过") }
                )
            }

            if (tasks.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "清空记录",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.HistoryEdu,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "暂无下载记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskItemCard(
                        task = task,
                        isHistoryMode = true,
                        onRetryClick = { viewModel.retryTask(task.id) },
                        onDeleteClick = { viewModel.deleteTask(task.id) },
                        onOpenFolderClick = {
                            try {
                                val pixivDir = File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                                    "Pixiv"
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.fromFile(pixivDir), DocumentsContract.Document.MIME_TYPE_DIR)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(Intent.createChooser(intent, "打开保存目录"))
                            } catch (e: Exception) {
                                // 兜底尝试图库
                                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                                    type = "image/*"
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(fallbackIntent)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空历史记录") },
            text = { Text("确定要清除所有已完成和已跳过的下载记录吗？已下载的文件不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
