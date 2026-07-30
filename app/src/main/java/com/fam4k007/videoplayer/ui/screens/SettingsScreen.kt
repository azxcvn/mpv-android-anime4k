package com.fam4k007.videoplayer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fam4k007.videoplayer.bilibili.auth.BiliBiliAuthManager
import com.fam4k007.videoplayer.ui.components.ClickableItem
import com.fam4k007.videoplayer.ui.components.PreferenceCard
import com.fam4k007.videoplayer.ui.components.PreferenceDivider
import com.fam4k007.videoplayer.ui.components.PreferenceSectionHeader
import com.fam4k007.videoplayer.ui.theme.DarkMode
import com.fam4k007.videoplayer.ui.theme.ThemeController
import com.fam4k007.videoplayer.ui.theme.spacing
import com.fam4k007.videoplayer.ui.screens.dialogs.DarkModeSelectionDialog
import com.fam4k007.videoplayer.ui.screens.dialogs.DisplayModeSelectionDialog
import com.fam4k007.videoplayer.ui.screens.dialogs.ThemeSelectionDialog
import com.fam4k007.videoplayer.preferences.PreferencesManager
import org.koin.compose.koinInject

/**
 * 设置页面
 * 使用新的主题系统和组件库
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlaybackSettings: () -> Unit = {},
    onNavigateToPlaybackHistory: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToBiliBiliDanmaku: () -> Unit = {},
    onNavigateToDownload: () -> Unit = {},
    onNavigateToSubtitleSearch: () -> Unit = {},
    onNavigateToFolderBlacklist: () -> Unit = {},
    onNavigateToMediaSettings: () -> Unit = {},
    onNavigateToDeviceInfo: () -> Unit = {},
    onNavigateToDanmakuServer: () -> Unit = {},
) {
    val context = LocalContext.current
    val authManager: BiliBiliAuthManager = koinInject()
    val preferencesManager: PreferencesManager = koinInject()
    val themeController = remember { ThemeController.from(context) }
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showDisplayModeDialog by remember { mutableStateOf(false) }
    
    // 获取当前显示模式
    val currentDisplayMode = remember { mutableStateOf(preferencesManager.getVideoDisplayMode()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 外观设置
            item {
                PreferenceSectionHeader(title = "外观")
            }
            
            item {
                PreferenceCard {
                    val currentTheme = themeController.getCurrentTheme()
                    ClickableItem(
                        title = "应用主题",
                        subtitle = stringResource(currentTheme.titleRes),
                        icon = Icons.Default.Palette,
                        onClick = { showThemeDialog = true }
                    )
                    
                    PreferenceDivider()
                    
                    val currentDarkMode = themeController.getDarkMode()
                    ClickableItem(
                        title = "暗色模式",
                        subtitle = when (currentDarkMode) {
                            DarkMode.Light -> "关闭"
                            DarkMode.Dark -> "开启"
                            DarkMode.Amoled -> "AMOLED模式"
                            DarkMode.System -> "跟随系统"
                        },
                        icon = Icons.Default.DarkMode,
                        onClick = { showDarkModeDialog = true }
                    )
                }
            }
            
            // 播放设置
            item {
                PreferenceSectionHeader(title = "播放")
            }
            
            item {
                PreferenceCard {
                    ClickableItem(
                        title = "播放设置",
                        subtitle = "修改与播放有关的参数配置",
                        icon = Icons.Default.PlayCircle,
                        onClick = onNavigateToPlaybackSettings
                    )
                    
                    PreferenceDivider()
                    
                    ClickableItem(
                        title = "播放历史记录",
                        subtitle = "查看最近播放的视频",
                        icon = Icons.Default.History,
                        onClick = onNavigateToPlaybackHistory
                    )
                }
            }
            
            // 弹幕
            item {
                PreferenceSectionHeader(title = "弹幕")
            }
            
            item {
                PreferenceCard {
                    ClickableItem(
                        title = "弹幕服务器",
                        subtitle = "管理弹幕搜索服务器",
                        icon = Icons.Default.Storage,
                        onClick = onNavigateToDanmakuServer
                    )
                }
            }
            
            // 媒体
            item {
                PreferenceSectionHeader(title = "媒体")
            }
            
            item {
                PreferenceCard {
                    ClickableItem(
                        title = "文件夹黑名单",
                        subtitle = "屏蔽指定文件夹，不再扫描其中的视频",
                        icon = Icons.Default.Warning,
                        onClick = onNavigateToFolderBlacklist
                    )
                    
                    PreferenceDivider()
                    
                    ClickableItem(
                        title = "其他媒体设置",
                        subtitle = ".nomedia 规则、隐藏文件夹扫描等",
                        icon = Icons.Default.Tune,
                        onClick = onNavigateToMediaSettings
                    )
                }
            }
            
            // 下载功能
            item {
                PreferenceSectionHeader(title = "下载")
            }
            
            item {
                PreferenceCard {
                    ClickableItem(
                        title = "哔哩哔哩弹幕下载",
                        subtitle = "下载B站视频弹幕",
                        icon = Icons.Default.Comment,
                        onClick = {
                            if (authManager.isLoggedIn()) {
                                onNavigateToBiliBiliDanmaku()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "请先在主页左上角登录哔哩哔哩账号",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    
                    PreferenceDivider()
                    
                    ClickableItem(
                        title = "哔哩哔哩视频下载",
                        subtitle = "下载B站视频/番剧",
                        icon = Icons.Default.Download,
                        onClick = {
                            if (authManager.isLoggedIn()) {
                                onNavigateToDownload()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "请先在主页左上角登录哔哩哔哩账号",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    
                    PreferenceDivider()
                    
                    ClickableItem(
                        title = "字幕搜索下载",
                        subtitle = "搜索并下载在线字幕",
                        icon = Icons.Default.Subtitles,
                        onClick = {
                            onNavigateToSubtitleSearch()
                        }
                    )
                }
            }
            
            // 其他
            item {
                PreferenceSectionHeader(title = "其他")
            }
            
            item {
                PreferenceCard {
                    ClickableItem(
                        title = "使用说明",
                        subtitle = "点击跳转外部在线文档查看",
                        icon = Icons.Default.Help,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.qq.com/doc/p/d190d78dc5ac1d3a718ea244775c34e8b1dc9559?nlc=1"))
                            context.startActivity(intent)
                        }
                    )

                    PreferenceDivider()

                    ClickableItem(
                        title = "设备信息",
                        subtitle = "查看HDR支持、编解码器等硬件信息",
                        icon = Icons.Default.Devices,
                        onClick = onNavigateToDeviceInfo
                    )
                    
                    PreferenceDivider()
                    
                    ClickableItem(
                        title = "关于",
                        subtitle = "应用信息与许可",
                        icon = Icons.Default.Info,
                        onClick = onNavigateToAbout
                    )
                }
            }
            
            // 底部留白
            item {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
            }
        }
    }
    
    // 主题选择对话框
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = themeController.getCurrentTheme(),
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { theme ->
                themeController.setTheme(theme)
                showThemeDialog = false
                // 重启 Activity 应用主题
                (context as? android.app.Activity)?.recreate()
            }
        )
    }
    
    // 暗色模式选择对话框
    if (showDarkModeDialog) {
        DarkModeSelectionDialog(
            currentMode = themeController.getDarkMode(),
            onDismiss = { showDarkModeDialog = false },
            onModeSelected = { mode ->
                themeController.setDarkMode(mode)
                showDarkModeDialog = false
                // 重启 Activity 应用主题
                (context as? android.app.Activity)?.recreate()
            }
        )
    }
    
    // 显示模式选择对话框
    if (showDisplayModeDialog) {
        DisplayModeSelectionDialog(
            currentMode = currentDisplayMode.value,
            onDismiss = { showDisplayModeDialog = false },
            onModeSelected = { mode ->
                preferencesManager.setVideoDisplayMode(mode)
                currentDisplayMode.value = mode
                showDisplayModeDialog = false
                android.widget.Toast.makeText(
                    context,
                    "已切换到${if (mode == "folder") "文件夹视图" else "视频列表"}模式",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

}
