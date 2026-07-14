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
                        text = "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
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
                PreferenceSectionHeader(title = "Appearance")
            }
            
            item {
                PreferenceCard {
                    val currentTheme = themeController.getCurrentTheme()
                    ClickableItem(
                        title = "App Theme",
                        subtitle = stringResource(currentTheme.titleRes),
                        icon = Icons.Default.Palette,
                        onClick = { showThemeDialog = true }
                    )
                    
                    val currentDarkMode = themeController.getDarkMode()
                    ClickableItem(
                        title = "Dark Mode",
                        subtitle = when (currentDarkMode) {
                            DarkMode.Light -> "Off"
                            DarkMode.Dark -> "On"
                            DarkMode.Amoled -> "AMOLED Mode"
                            DarkMode.System -> "Follow System"
                        },
                        icon = Icons.Default.DarkMode,
                        onClick = { showDarkModeDialog = true }
                    )
                }
            }
            
            // Playback Settings
            item {
                PreferenceSectionHeader(title = "Playback")
            }
            
            item {
                PreferenceCard {
                    ClickableItem(
                        title = "Playback Settings",
                        subtitle = "Configure playback-related parameters",
                        icon = Icons.Default.PlayCircle,
                        onClick = onNavigateToPlaybackSettings
                    )
                    
                    ClickableItem(
                        title = "Playback History",
                        subtitle = "View recently played videos",
                        icon = Icons.Default.History,
                        onClick = onNavigateToPlaybackHistory
                    )
                }
            }
            
            // 弹幕
            item {
                PreferenceSectionHeader(title = "Danmaku")
            }
            
            item {
                PreferenceCard {
                    ClickableItem(
                        title = "Danmaku Servers",
                        subtitle = "Manage danmaku search servers",
                        icon = Icons.Default.Storage,
                        onClick = onNavigateToDanmakuServer
                    )
                }
            }
            
            // 媒体
            item {
                PreferenceSectionHeader(title = "Media")
            }
            
            item {
                PreferenceCard {
                    ClickableItem(
                        title = "Folder Blacklist",
                        subtitle = "Exclude folders from video scanning",
                        icon = Icons.Default.Warning,
                        onClick = onNavigateToFolderBlacklist
                    )
                    
                    ClickableItem(
                        title = "Other Media Settings",
                        subtitle = ".nomedia rules, hidden folder scanning, etc.",
                        icon = Icons.Default.Tune,
                        onClick = onNavigateToMediaSettings
                    )
                }
            }
            
            // 下载功能
            item {
                PreferenceSectionHeader(title = "Download")
            }
            
            item {
                PreferenceCard {
                    ClickableItem(
                        title = "Bilibili Danmaku Download",
                        subtitle = "Download Bilibili danmaku",
                        icon = Icons.Default.Comment,
                        onClick = {
                            if (authManager.isLoggedIn()) {
                                onNavigateToBiliBiliDanmaku()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "Please log in to Bilibili from the home page first",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    
                    ClickableItem(
                        title = "Bilibili Video Download",
                        subtitle = "Download Bilibili videos/bangumi",
                        icon = Icons.Default.Download,
                        onClick = {
                            if (authManager.isLoggedIn()) {
                                onNavigateToDownload()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "Please log in to Bilibili from the home page first",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    
                    ClickableItem(
                        title = "Subtitle Search",
                        subtitle = "Search and download online subtitles",
                        icon = Icons.Default.Subtitles,
                        onClick = {
                            onNavigateToSubtitleSearch()
                        }
                    )
                }
            }
            
            // 其他
            item {
                PreferenceSectionHeader(title = "Other")
            }
            
            item {
                PreferenceCard {
                    ClickableItem(
                        title = "User Guide",
                        subtitle = "View online documentation",
                        icon = Icons.Default.Help,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.qq.com/doc/p/d190d78dc5ac1d3a718ea244775c34e8b1dc9559?nlc=1"))
                            context.startActivity(intent)
                        }
                    )

                    ClickableItem(
                        title = "Device Info",
                        subtitle = "View HDR support, codecs, and other hardware info",
                        icon = Icons.Default.Devices,
                        onClick = onNavigateToDeviceInfo
                    )
                    
                    ClickableItem(
                        title = "About",
                        subtitle = "App info and licenses",
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
                    "Switched to ${if (mode == "folder") "Folder View" else "Video List"} mode",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

}
