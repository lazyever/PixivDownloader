package com.pixiv.downloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pixiv.downloader.data.api.models.ArtworkInfo
import com.pixiv.downloader.ui.theme.PixivBlue
import com.pixiv.downloader.util.PixivImageLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkPreviewCard(
    artwork: ArtworkInfo,
    selectedUgoiraFormat: String,
    onUgoiraFormatChange: (String) -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 封面预览
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = PixivImageLoader.buildRequest(context, artwork.coverUrl),
                        imageLoader = PixivImageLoader.get(context),
                        contentDescription = artwork.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 徽章提示（多图或 Ugoira）
                    val badgeText = if (artwork.isUgoira) "动图" else "${artwork.pageCount}P"
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(topStart = 8.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // 详细信息
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = artwork.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "作者: ${artwork.authorName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "作品 ID: ${artwork.id} (UID: ${artwork.authorId})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // 标签类型与认证方式展示
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (artwork.isUgoira) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Ugoira 动图", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Movie,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = PixivBlue
                                    )
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        } else {
                            AssistChip(
                                onClick = {},
                                label = { Text("${artwork.pageCount} 页原图", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        }

                        AssistChip(
                            onClick = {},
                            label = { Text(artwork.authType.ifEmpty { "匿名" }, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            // 若为 Ugoira 动图，展示 MP4 / GIF 导出格式选择
            if (artwork.isUgoira) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "动图合成格式:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedUgoiraFormat == "MP4",
                            onClick = { onUgoiraFormatChange("MP4") },
                            label = { Text("MP4 视频 (推荐)") }
                        )
                        FilterChip(
                            selected = selectedUgoiraFormat == "GIF",
                            onClick = { onUgoiraFormatChange("GIF") },
                            label = { Text("GIF 动图") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 开始下载按钮
            Button(
                onClick = onDownloadClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (artwork.isUgoira) "加入队列下载并合成 $selectedUgoiraFormat" else "加入队列并下载原图 (${artwork.pageCount}P)",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
