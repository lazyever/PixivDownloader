package com.pixiv.downloader.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pixiv.downloader.ui.theme.PixivBlue
import com.pixiv.downloader.util.PixivImageLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val loginError by viewModel.loginError.collectAsState()

    var showLoginDialog by remember { mutableStateOf(false) }
    var currentLoginUrl by remember { mutableStateOf("") }
    var currentVerifier by remember { mutableStateOf("") }

    // SAF 目录选择器
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
                viewModel.setCustomDirectory(uri)
            } catch (e: Exception) {
                // 部分第三方文件管理器若不支持 persistable 权限
                viewModel.setCustomDirectory(uri)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 1. Pixiv 账号登录状态卡片
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pixiv 账号",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (userProfile.isLoggedIn) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!userProfile.avatarUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = PixivImageLoader.buildRequest(context, userProfile.avatarUrl!!),
                                    imageLoader = PixivImageLoader.get(context),
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(PixivBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userProfile.userName ?: "Pixiv 用户",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "UID: ${userProfile.userId ?: ""} (@${userProfile.account ?: ""})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.logout() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("退出")
                            }
                        }
                    } else {
                        Text(
                            text = "未登录。公开作品支持免登录直接下载；登录后可下载仅会员可见或需鉴权的作品。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val (url, verifier) = viewModel.getLoginUrlWithVerifier()
                                currentLoginUrl = url
                                currentVerifier = verifier
                                showLoginDialog = true
                            },
                            enabled = !isLoggingIn,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isLoggingIn) "正在授权登录..." else "登录 Pixiv 账号")
                        }
                    }

                    if (loginError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = loginError ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // 2. Pixiv 下载认证策略设置
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "下载认证策略",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "公开作品优先匿名下载",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "默认开启。无论是否已登录，公开作品均走隔离匿名链路（不附带 Token 与 Cookie）；仅当作品明确需要鉴权时才自动切换已登录账号。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = settingsState.preferAnonymous,
                            onCheckedChange = { viewModel.setPreferAnonymous(it) }
                        )
                    }
                }
            }
        }

        // 3. 存储目录与文件规则设置
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "保存位置与规则",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "当前保存路径:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (!settingsState.customUriString.isNullOrEmpty()) {
                            settingsState.customUriString!!
                        } else {
                            "Pictures/Pixiv/ (默认公开相册目录)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "结构严格采用：Pictures/Pixiv/作者名称-作者ID/作品ID_p0.jpg，多页补齐，同一作者归并保存。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { folderPickerLauncher.launch(null) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("更改保存目录 (SAF)")
                        }

                        if (!settingsState.customUriString.isNullOrEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.resetToDefaultDirectory() },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("恢复默认")
                            }
                        }
                    }
                }
            }
        }

        // 3. Ugoira 动图默认格式设置
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ugoira 动图默认格式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "解析 Ugoira 动图时默认预选的合成格式（下载前仍可针对单独作品切换）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = settingsState.defaultUgoiraFormat == "MP4",
                            onClick = { viewModel.setDefaultUgoiraFormat("MP4") },
                            label = { Text("MP4 视频（体积小，硬解流畅，保真帧率）") }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = settingsState.defaultUgoiraFormat == "GIF",
                            onClick = { viewModel.setDefaultUgoiraFormat("GIF") },
                            label = { Text("GIF 动图（通用动图图片，保真每帧延迟）") }
                        )
                    }
                }
            }
        }

        // 4. 关于应用
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "关于 Pixiv Downloader",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "版本: 1.0.0\n专为 Pixiv 插画与动图设计的 Android 原生应用。支持单图、多图原画质批量下载与作者目录分类，内置原生硬件编码器合成 Ugoira 动图，支持系统分享菜单接收与后台前台服务下载队列。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showLoginDialog && currentLoginUrl.isNotEmpty()) {
        PixivLoginDialog(
            loginUrl = currentLoginUrl,
            codeVerifier = currentVerifier,
            onAuthSuccess = { code, verifier ->
                showLoginDialog = false
                viewModel.handleAuthCode(code, verifier) { success ->
                    // 授权结果由 ViewModel 处理
                }
            },
            onDismiss = { showLoginDialog = false }
        )
    }
}
