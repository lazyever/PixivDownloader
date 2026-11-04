package com.pixiv.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pixiv.downloader.ui.navigation.Screen
import com.pixiv.downloader.ui.screens.download.DownloadScreen
import com.pixiv.downloader.ui.screens.download.DownloadViewModel
import com.pixiv.downloader.ui.screens.history.HistoryScreen
import com.pixiv.downloader.ui.screens.history.HistoryViewModel
import com.pixiv.downloader.ui.screens.queue.QueueScreen
import com.pixiv.downloader.ui.screens.queue.QueueViewModel
import com.pixiv.downloader.ui.screens.settings.SettingsScreen
import com.pixiv.downloader.ui.screens.settings.SettingsViewModel
import com.pixiv.downloader.ui.theme.PixivDownloaderTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val downloadViewModel: DownloadViewModel by viewModels()
    private val queueViewModel: QueueViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // 通知权限回调
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ 请求前台通知权限（加防护）
        try {
            checkNotificationPermission()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // 检查是否由外部应用分享打开（加防护）
        try {
            handleIncomingIntent(intent)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        setContent {
            MainAppContent(
                downloadViewModel = downloadViewModel,
                queueViewModel = queueViewModel,
                historyViewModel = historyViewModel,
                settingsViewModel = settingsViewModel
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        // 接收通过系统分享菜单发来的文本/链接
        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrEmpty()) {
                    downloadViewModel.onInputTextChanged(sharedText)
                    downloadViewModel.parseInputUrls()
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    downloadViewModel: DownloadViewModel,
    queueViewModel: QueueViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel
) {
    PixivDownloaderTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        // 导航未初始化或初次绘制时，安全默认使用 Download 页面路由
        val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Download.route

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentRoute) {
                                Screen.Download.route -> "Pixiv 原图下载"
                                Screen.Queue.route -> "下载队列"
                                Screen.History.route -> "下载历史"
                                Screen.Settings.route -> "设置"
                                else -> "Pixiv 原图下载"
                            }
                        )
                    },
                    actions = {
                        if (currentRoute != Screen.Settings.route) {
                            IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                                Icon(Icons.Default.Settings, contentDescription = "设置")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // 设置页不是底部导航页。点击任一底部标签时，先从返回栈移除设置页，
                                // 再切换到目标主页面，避免设置页继续覆盖在下载/队列/历史页面上。
                                if (currentRoute == Screen.Settings.route) {
                                    navController.popBackStack(
                                        Screen.Settings.route,
                                        inclusive = true
                                    )
                                }

                                val visibleRoute =
                                    navController.currentBackStackEntry?.destination?.route

                                if (visibleRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        // 主页面之间只保留一个底部导航栈；不保存设置页状态。
                                        popUpTo(Screen.Download.route) {
                                            inclusive = false
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Download.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Download.route) {
                    DownloadScreen(viewModel = downloadViewModel)
                }
                composable(Screen.Queue.route) {
                    QueueScreen(viewModel = queueViewModel)
                }
                composable(Screen.History.route) {
                    HistoryScreen(viewModel = historyViewModel)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}
