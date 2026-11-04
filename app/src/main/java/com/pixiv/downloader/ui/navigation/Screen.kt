package com.pixiv.downloader.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Download : Screen("download", "下载", Icons.Default.Download)
    data object Queue : Screen("queue", "队列", Icons.Default.FormatListBulleted)
    data object History : Screen("history", "历史", Icons.Default.History)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)

    companion object {
        // 使用 getter 动态获取，彻底避免伴生对象静态初始化时单例类尚未完成类加载导致元素为 null
        val bottomNavItems: List<Screen>
            get() = listOf(Download, Queue, History)

        fun find(route: String?): Screen = when (route) {
            Download.route -> Download
            Queue.route -> Queue
            History.route -> History
            Settings.route -> Settings
            else -> Download
        }
    }
}
