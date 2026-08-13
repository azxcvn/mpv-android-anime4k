package com.fam4k007.videoplayer.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.content.Intent
import android.widget.Toast
import com.fam4k007.videoplayer.presentation.PlaybackSettingsViewModel
import com.fam4k007.videoplayer.ui.components.PreferenceCard
import com.fam4k007.videoplayer.ui.components.PreferenceDivider
import com.fam4k007.videoplayer.ui.components.PreferenceSectionHeader
import com.fam4k007.videoplayer.ui.components.SwitchItem
import com.fam4k007.videoplayer.ui.components.SliderItem
import com.fam4k007.videoplayer.ui.components.TextItem
import com.fam4k007.videoplayer.ui.player.CustomSeekbar
import com.fam4k007.videoplayer.ui.player.SeekbarStyle
import com.fam4k007.videoplayer.ui.theme.spacing
import com.fam4k007.videoplayer.domain.player.Anime4KManager
import kotlin.math.roundToInt

/**
 * Compose 版本的播放设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    viewModel: PlaybackSettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.playbackSettings.collectAsState()

    var showSeekTimeDialog by remember { mutableStateOf(false) }
    var showDoubleTapSeekDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var pendingProfile by remember { mutableStateOf<String?>(null) }
    var showGpuNextWarning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "播放设置",
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
            // ===== 解码与画质 =====
            item {
                PreferenceSectionHeader("解码与画质")
            }

            item {
                PreferenceCard {
                    MpvProfileCard(
                        currentProfile = settings.mpvProfile,
                        onProfileChange = { profile ->
                            viewModel.setMpvProfile(profile)
                            pendingProfile = profile
                            showRestartDialog = true
                        }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "GPU Next 渲染",
                        subtitle = if (settings.gpuNext) "配合软解可正确显示杜比视界，与 4K 超分不兼容" else "开启后可改善 HDR 渲染效果",
                        checked = settings.gpuNext,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showGpuNextWarning = true
                            } else {
                                viewModel.setGpuNext(false)
                            }
                        }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "Vulkan 渲染上下文",
                        subtitle = if (settings.useVulkan) "使用 Vulkan 驱动，性能会更好" else "使用 OpenGL ES 驱动",
                        checked = settings.useVulkan,
                        onCheckedChange = { viewModel.setUseVulkan(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "记忆超分模式",
                        subtitle = if (settings.anime4KMemory) "记住上次使用的Anime4K模式" else "每次播放都从关闭状态开始",
                        checked = settings.anime4KMemory,
                        onCheckedChange = { viewModel.setAnime4KMemory(it) }
                    )
                    PreferenceDivider()
                    Anime4KQualitySelector(
                        currentQuality = settings.anime4KQuality,
                        onQualityChange = { viewModel.setAnime4KQuality(it) }
                    )
                }
            }

            // ===== 播放控制 =====
            item {
                PreferenceSectionHeader("播放控制")
            }

            item {
                PreferenceCard {
                    SwitchItem(
                        title = "自动播放下一集",
                        subtitle = if (settings.autoPlayNext) "当前视频结束后自动播放下一个视频" else "播放完当前视频后停止",
                        checked = settings.autoPlayNext,
                        onCheckedChange = { viewModel.setAutoPlayNext(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "播完退出播放器",
                        subtitle = if (settings.closeAfterEOF) "播放完最后一个视频后自动关闭播放器" else "播完后停留在当前画面",
                        checked = settings.closeAfterEOF,
                        onCheckedChange = { viewModel.setCloseAfterEOF(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "记忆播放倍速",
                        subtitle = if (settings.rememberSpeed) "始终使用上次设置的播放倍速" else "每次切换视频恢复到1倍速",
                        checked = settings.rememberSpeed,
                        onCheckedChange = { viewModel.setRememberSpeed(it) }
                    )
                    PreferenceDivider()
                    SliderItem(
                        title = "长按倍速",
                        value = settings.longPressSpeed,
                        valueRange = 1.0f..6.0f,
                        steps = 49,
                        onValueChange = { viewModel.setLongPressSpeed(Math.round(it * 10f) / 10f) },
                        valueFormatter = { String.format("%.1fx", it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "精确进度定位",
                        subtitle = if (settings.preciseSeeking) "定位更准确但可能较慢" else "定位更快但使用关键帧",
                        checked = settings.preciseSeeking,
                        onCheckedChange = { viewModel.setPreciseSeeking(it) }
                    )
                    PreferenceDivider()
                    TextItem(
                        title = "快进/快退时长",
                        value = "${settings.seekTime}秒",
                        onClick = { showSeekTimeDialog = true }
                    )
                }
            }

            // ===== 手势与灵敏度 =====
            item {
                PreferenceSectionHeader("手势与灵敏度")
            }

            item {
                PreferenceCard {
                    DoubleTapModeCard(
                        currentMode = settings.doubleTapMode,
                        onModeChange = { viewModel.setDoubleTapMode(it) }
                    )
                    if (settings.doubleTapMode == 1) {
                        PreferenceDivider()
                        TextItem(
                            title = "双击跳转时长",
                            value = "${settings.doubleTapSeekSeconds}秒",
                            onClick = { showDoubleTapSeekDialog = true }
                        )
                    }
                    PreferenceDivider()
                    SliderItem(
                        title = "亮度灵敏度",
                        value = settings.brightnessSensitivity,
                        valueRange = 0.5f..5.0f,
                        steps = 8,
                        onValueChange = { viewModel.setBrightnessSensitivity(Math.round(it * 10f) / 10f) },
                        valueFormatter = { String.format("%.1fx", it) }
                    )
                    PreferenceDivider()
                    SliderItem(
                        title = "音量灵敏度",
                        value = settings.volumeSensitivity,
                        valueRange = 50f..300f,
                        steps = 24,
                        onValueChange = { viewModel.setVolumeSensitivity(Math.round(it).toFloat()) },
                        valueFormatter = { "${Math.round(it)}" }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "控制系统音量",
                        subtitle = if (settings.controlSystemVolume) "播放中调节的音量退出后保留" else "退出播放后恢复进入前的音量",
                        checked = settings.controlSystemVolume,
                        onCheckedChange = { viewModel.setControlSystemVolume(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "音量增强",
                        subtitle = if (settings.volumeBoost) "音量可超过100%,最高300%" else "音量范围限制在1-100%",
                        checked = settings.volumeBoost,
                        onCheckedChange = { viewModel.setVolumeBoost(it) }
                    )
                }
            }

            // ===== 界面与显示 =====
            item {
                PreferenceSectionHeader("界面与显示")
            }

            item {
                PreferenceCard {
                    SeekbarStyleCard(
                        currentStyle = settings.seekbarStyle,
                        onStyleChange = { viewModel.setSeekbarStyle(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "显示章节进度条",
                        subtitle = if (settings.chapterBarEnabled) "进度条显示章节节点和当前章节名称" else "隐藏章节相关信息",
                        checked = settings.chapterBarEnabled,
                        onCheckedChange = { viewModel.setChapterBarEnabled(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "进度条缩略图预览",
                        subtitle = if (settings.seekbarThumbnailEnabled) "拖动进度条时显示视频画面预览" else "拖动进度条时不显示缩略图",
                        checked = settings.seekbarThumbnailEnabled,
                        onCheckedChange = { viewModel.setSeekbarThumbnailEnabled(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "显示播放进度条",
                        subtitle = if (settings.showVideoProgressBar) "视频列表缩略图上显示观看进度" else "隐藏进度条，仅通过标签显示状态",
                        checked = settings.showVideoProgressBar,
                        onCheckedChange = { viewModel.setShowVideoProgressBar(it) }
                    )
                    PreferenceDivider()
                    SliderItem(
                        title = "已观看阈值",
                        value = settings.watchedThreshold.toFloat(),
                        valueRange = 50f..100f,
                        steps = 9,
                        onValueChange = { viewModel.setWatchedThreshold(it.roundToInt()) },
                        valueFormatter = { "${it.roundToInt()}%" },
                        subtitle = "播放进度超过此值即标记为已观看"
                    )
                    PreferenceDivider()
                    RotationLockSelector(
                        currentMode = settings.rotationLockMode,
                        onModeChange = { viewModel.setRotationLockMode(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "启用播放界面动画",
                        subtitle = if (settings.controlsAnimationEnabled) "控制栏显示/隐藏时带有滑动动画" else "控制栏直接显示/隐藏，无动画",
                        checked = settings.controlsAnimationEnabled,
                        onCheckedChange = { viewModel.setControlsAnimationEnabled(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "启用抽屉界面动画",
                        subtitle = if (settings.drawerAnimationEnabled) "右侧抽屉式面板带有过渡动画" else "抽屉面板直接显示/隐藏，无动画",
                        checked = settings.drawerAnimationEnabled,
                        onCheckedChange = { viewModel.setDrawerAnimationEnabled(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "启用长按倍速提示",
                        subtitle = if (settings.longPressSpeedHintEnabled) "长按时显示当前倍速提示" else "长按时不显示倍速提示",
                        checked = settings.longPressSpeedHintEnabled,
                        onCheckedChange = { viewModel.setLongPressSpeedHintEnabled(it) }
                    )
                }
            }

            item { Spacer(Modifier.height(MaterialTheme.spacing.medium)) }
        }
    }

    // 快进时长选择对话框
    if (showSeekTimeDialog) {
        SeekTimeDialog(
            currentValue = settings.seekTime,
            onDismiss = { showSeekTimeDialog = false },
            onConfirm = { newValue ->
                viewModel.setSeekTime(newValue)
                showSeekTimeDialog = false
            }
        )
    }

    // 双击跳转时长选择对话框
    if (showDoubleTapSeekDialog) {
        DoubleTapSeekDialog(
            currentValue = settings.doubleTapSeekSeconds,
            onDismiss = { showDoubleTapSeekDialog = false },
            onConfirm = { newValue ->
                viewModel.setDoubleTapSeekSeconds(newValue)
                showDoubleTapSeekDialog = false
            }
        )
    }

    // MPV 解码器预设变更 — 重启确认对话框
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = {
                Text(
                    "需要重启应用",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    "修改解码器预设需要重启应用才能生效。是否立即重启？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartDialog = false
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        (context as? android.app.Activity)?.finish()
                    }
                ) {
                    Text("重启", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text("稍后", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // GPU Next 开启警告
    if (showGpuNextWarning) {
        AlertDialog(
            onDismissRequest = { showGpuNextWarning = false },
            title = {
                Text(
                    "开启 GPU Next 渲染",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "GPU Next 是 mpv 的新渲染引擎，可改善 HDR 渲染效果，配合软解可正确显示杜比视界画面。但与 4K 超分（Anime4K）不兼容，开启后超分功能将自动禁用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "注意：部分设备开启后可能出现紫屏，如遇到请启用下方的「Vulkan 渲染上下文」即可解决。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setGpuNext(true)
                        showGpuNextWarning = false
                    }
                ) {
                    Text("继续开启", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpuNextWarning = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DoubleTapModeCard(
    currentMode: Int,
    onModeChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.small
            )
    ) {
        Text(
            "双击手势",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(MaterialTheme.spacing.small))

        // 模式 0: 暂停/播放
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onModeChange(0) }
                .padding(vertical = MaterialTheme.spacing.small, horizontal = MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = currentMode == 0,
                onClick = { onModeChange(0) },
                modifier = Modifier.size(24.dp),
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "暂停/播放",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (currentMode == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (currentMode == 0) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    "双击任意位置暂停或播放",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(MaterialTheme.spacing.extraSmall))

        // 模式 1: 快进/快退
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onModeChange(1) }
                .padding(vertical = MaterialTheme.spacing.small, horizontal = MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = currentMode == 1,
                onClick = { onModeChange(1) },
                modifier = Modifier.size(24.dp),
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "快进/快退",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (currentMode == 1) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (currentMode == 1) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    "双击左半屏快退，右半屏快进",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SeekTimeDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selected by remember { mutableIntStateOf(currentValue) }
    var showCustomInput by remember { mutableStateOf(false) }
    var customInputText by remember { mutableStateOf("") }
    val options = listOf(3, 5, 10, 15, 20, 25, 30)
    val isCustom = selected !in options

    if (showCustomInput) {
        AlertDialog(
            onDismissRequest = { showCustomInput = false },
            title = {
                Text(
                    "自定义快进/快退时长",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        "请输入快进/快退时长（1~300秒）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customInputText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 3) {
                                customInputText = input
                            }
                        },
                        label = { Text("秒数") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val value = customInputText.toIntOrNull()
                        if (value != null && value in 1..300) {
                            showCustomInput = false
                            onConfirm(value)
                        }
                    },
                    enabled = customInputText.toIntOrNull()?.let { it in 1..300 } == true
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomInput = false }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    "快进/快退时长",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    options.forEach { seconds ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selected = seconds }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected == seconds,
                                onClick = { selected = seconds },
                                modifier = Modifier.size(24.dp),
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${seconds}秒",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected == seconds) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected == seconds) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                    // 自定义选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showCustomInput = true }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCustom,
                            onClick = { showCustomInput = true },
                            modifier = Modifier.size(24.dp),
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (isCustom) "自定义（${selected}秒）" else "自定义",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCustom) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isCustom) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onConfirm(selected) }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun DoubleTapSeekDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selected by remember { mutableIntStateOf(currentValue) }
    var showCustomInput by remember { mutableStateOf(false) }
    var customInputText by remember { mutableStateOf("") }
    val options = listOf(5, 10, 15, 20, 30)
    val isCustom = selected !in options

    if (showCustomInput) {
        // 自定义输入对话框
        AlertDialog(
            onDismissRequest = { showCustomInput = false },
            title = {
                Text(
                    "自定义跳转时长",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        "请输入跳转时长（1~300秒）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customInputText,
                        onValueChange = { input ->
                            // 只允许输入数字
                            if (input.all { it.isDigit() } && input.length <= 3) {
                                customInputText = input
                            }
                        },
                        label = { Text("秒数") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val value = customInputText.toIntOrNull()
                        if (value != null && value in 1..300) {
                            showCustomInput = false
                            onConfirm(value)
                        }
                    },
                    enabled = customInputText.toIntOrNull()?.let { it in 1..300 } == true
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomInput = false }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    "双击跳转时长",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    options.forEach { seconds ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selected = seconds }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected == seconds,
                                onClick = { selected = seconds },
                                modifier = Modifier.size(24.dp),
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${seconds}秒",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected == seconds) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected == seconds) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                    // 自定义选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showCustomInput = true }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCustom,
                            onClick = { showCustomInput = true },
                            modifier = Modifier.size(24.dp),
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (isCustom) "自定义（${selected}秒）" else "自定义",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCustom) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isCustom) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onConfirm(selected) }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/** 解码器预设信息 */
private data class MpvProfileOption(
    val value: String,
    val displayName: String,
    val description: String
)

private val mpvProfileOptions = listOf(
    MpvProfileOption("fast", "Fast", "硬解 + bilinear 缩放，整体功耗最低（推荐）"),
    MpvProfileOption("default", "Default", "默认配置，平衡画质与性能"),
    MpvProfileOption("high-quality", "High Quality", "高质量渲染，使用 ewa_lanczossharp 缩放"),
    MpvProfileOption("gpu-hq", "GPU HQ", "GPU 高质量模式，开启去条带等后处理"),
    MpvProfileOption("low-latency", "Low Latency", "低延迟模式，适合直播/在线流媒体"),
    MpvProfileOption("sw-fast", "SW Fast", "强制软解，GPU 负载最低但 CPU 功耗最高"),
)

@Composable
private fun MpvProfileCard(
    currentProfile: String,
    onProfileChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.small
            )
    ) {
        Text(
            "解码器预设",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        mpvProfileOptions.forEach { option ->
            val isSelected = currentProfile == option.value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onProfileChange(option.value) }
                    .padding(vertical = MaterialTheme.spacing.small, horizontal = MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onProfileChange(option.value) },
                    modifier = Modifier.size(24.dp),
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        option.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        option.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SeekbarStyleCard(
    currentStyle: String,
    onStyleChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.small
            )
    ) {
        Text(
            "进度条样式",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(MaterialTheme.spacing.small))

        SeekbarStyle.entries.forEach { style ->
            val isSelected = currentStyle == style.name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onStyleChange(style.name) }
                    .padding(vertical = MaterialTheme.spacing.small, horizontal = MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onStyleChange(style.name) },
                    modifier = Modifier.size(24.dp),
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        style.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    // 样式实时预览（与实际播放效果一致）
                    SeekbarStylePreview(
                        style = style,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * 进度条样式实时预览
 * 复用播放器实际的 CustomSeekbar 组件，驱动 0→100 的往返动画，所见即所得
 */
@Composable
private fun SeekbarStylePreview(
    style: SeekbarStyle,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "seekbar_style_preview")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "seekbar_style_preview_progress",
    )
    CustomSeekbar(
        progress = animatedProgress,
        duration = 100f,
        seekbarStyle = style,
        accentColor = primaryColor,
        paused = false,
        isDragging = false,
        chapters = emptyList(),
        skipSegments = emptyList(),
        onSeek = {},
        onSeekFinished = {},
        modifier = modifier,
    )
}

@Composable
private fun RotationLockSelector(
    currentMode: String,
    onModeChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.small
            )
    ) {
        Text(
            "画面方向",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(MaterialTheme.spacing.small))

        QualityOption(
            label = "自动",
            subtitle = "跟随视频原始宽高比自适应",
            isSelected = currentMode == "AUTO",
            onClick = { onModeChange("AUTO") }
        )
        QualityOption(
            label = "锁定纵向",
            subtitle = "所有视频强制竖屏播放",
            isSelected = currentMode == "PORTRAIT",
            onClick = { onModeChange("PORTRAIT") }
        )
        QualityOption(
            label = "锁定横向",
            subtitle = "所有视频强制横屏播放",
            isSelected = currentMode == "LANDSCAPE",
            onClick = { onModeChange("LANDSCAPE") }
        )
    }
}

@Composable
private fun Anime4KQualitySelector(
    currentQuality: String,
    onQualityChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.small
            )
    ) {
        Text(
            "超分质量",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(MaterialTheme.spacing.small))

        QualityOption(
            label = "流畅",
            subtitle = "GPU负载最低，播放更流畅",
            isSelected = currentQuality == "FAST",
            onClick = { onQualityChange("FAST") }
        )
        QualityOption(
            label = "均衡",
            subtitle = "画质与性能的平衡之选",
            isSelected = currentQuality == "BALANCED",
            onClick = { onQualityChange("BALANCED") }
        )
        QualityOption(
            label = "高清",
            subtitle = "追求最佳画质，GPU开销较大",
            isSelected = currentQuality == "HIGH",
            onClick = { onQualityChange("HIGH") }
        )
    }
}

@Composable
private fun QualityOption(
    label: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.spacing.small, horizontal = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            modifier = Modifier.size(24.dp),
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
