package com.fam4k007.videoplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.ui.components.PreferenceCard
import com.fam4k007.videoplayer.ui.components.PreferenceDivider
import com.fam4k007.videoplayer.ui.components.PreferenceSectionHeader
import com.fam4k007.videoplayer.ui.components.SwitchItem
import com.fam4k007.videoplayer.ui.theme.spacing

/**
 * 其他媒体设置页面
 * 包含 .nomedia 规则、隐藏文件夹扫描、视频显示模式等开关设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    var includeNoMedia by remember { mutableStateOf(prefs.getIncludeNoMediaFolders()) }
    var scanHiddenEnabled by remember { mutableStateOf(prefs.isScanHiddenFoldersEnabled()) }
    var currentDisplayMode by remember { mutableStateOf(prefs.getVideoDisplayMode()) }

    // 警告弹窗状态
    var showWarningDialog by remember { mutableStateOf(false) }

    // 用户开启任一逆向扫描开关时，检测是否需要弹出警告
    fun checkShowWarning() {
        if (!prefs.getDontShowNomediaWarning()) {
            showWarningDialog = true
        }
    }

    // 逆向扫描警告弹窗
    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = {
                Text(
                    text = "警告",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "此功能将突破 Android 系统的原生限制，扫描系统默认跳过的目录。作者已尽力优化，但受限于平台机制，开启后可能出现但不限于以下问题：\n\n"
                            + "1. 显示异常\n"
                            + "2. 视频数量统计不准确\n"
                            + "3. 扫描或加载卡顿\n"
                            + "4. 刷新状态不及时等",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { showWarningDialog = false }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        prefs.setDontShowNomediaWarning(true)
                        showWarningDialog = false
                    }
                ) {
                    Text("不再提示")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "其他媒体设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 扫描规则
            item {
                PreferenceSectionHeader("扫描规则")
            }

            item {
                PreferenceCard {
                    SwitchItem(
                        title = "扫描包含 .nomedia 的文件夹",
                        subtitle = if (includeNoMedia) "同时扫描 .nomedia 文件夹中的视频" else "跳过包含 .nomedia 文件的文件夹",
                        checked = includeNoMedia,
                        onCheckedChange = { enabled ->
                            includeNoMedia = enabled
                            prefs.setIncludeNoMediaFolders(enabled)
                            if (enabled) checkShowWarning()
                        }
                    )

                    PreferenceDivider()

                    SwitchItem(
                        title = "扫描隐藏文件夹",
                        subtitle = if (scanHiddenEnabled) "允许扫描 .开头 的隐藏文件夹" else "跳过 .开头 的隐藏文件夹",
                        checked = scanHiddenEnabled,
                        onCheckedChange = { enabled ->
                            scanHiddenEnabled = enabled
                            prefs.setScanHiddenFoldersEnabled(enabled)
                            if (enabled) checkShowWarning()
                        }
                    )
                }
            }

            // 显示模式
            item {
                PreferenceSectionHeader("显示模式")
            }

            item {
                PreferenceCard {
                    DisplayModeSelector(
                        currentMode = currentDisplayMode,
                        nomediaEnabled = !includeNoMedia,
                        scanHiddenEnabled = scanHiddenEnabled,
                        onModeChange = { mode ->
                            prefs.setVideoDisplayMode(mode)
                            currentDisplayMode = mode
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(MaterialTheme.spacing.medium)) }
        }
    }
}

@Composable
private fun DisplayModeSelector(
    currentMode: String,
    nomediaEnabled: Boolean,
    scanHiddenEnabled: Boolean,
    onModeChange: (String) -> Unit
) {
    // 当 .nomedia 关闭或隐藏文件夹开启时，禁止切换到视频列表
    val flatDisabled = !nomediaEnabled || scanHiddenEnabled

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.small
            )
    ) {
        Text(
            "视频显示模式",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(MaterialTheme.spacing.small))

        val modes = listOf(
            "folder" to "文件夹视图",
            "flat" to "视频列表"
        )

        modes.forEach { (mode, label) ->
            val isSelected = currentMode == mode
            val isDisabled = mode == "flat" && flatDisabled
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isDisabled) { onModeChange(mode) }
                    .padding(
                        vertical = MaterialTheme.spacing.small,
                        horizontal = MaterialTheme.spacing.small
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { if (!isDisabled) onModeChange(mode) },
                    enabled = !isDisabled,
                    modifier = Modifier.size(24.dp),
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    else if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }

        // 禁用时的说明文字
        if (flatDisabled) {
            Spacer(Modifier.height(MaterialTheme.spacing.small))
            Text(
                text = "禁用 .nomedia 或开启隐藏文件夹扫描时，视频列表模式将无法完整扫描文件，且性能会大幅下降，故已禁用此切换。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
            )
        }
    }
}
