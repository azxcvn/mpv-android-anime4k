package com.fam4k007.videoplayer.ui.screens

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
import com.fam4k007.videoplayer.ui.components.PreferenceSectionHeader
import com.fam4k007.videoplayer.ui.components.SwitchItem
import com.fam4k007.videoplayer.ui.components.SliderItem
import com.fam4k007.videoplayer.ui.components.TextItem
import com.fam4k007.videoplayer.ui.player.SeekbarStyle
import com.fam4k007.videoplayer.ui.theme.spacing
import com.fam4k007.videoplayer.domain.player.Anime4KManager

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
                        "Playback Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
            // 进度控制
            item {
                PreferenceSectionHeader("Seek Controls")
            }

            item {
                PreferenceCard {
                    SwitchItem(
                        title = "Precise Seeking",
                        subtitle = if (settings.preciseSeeking) "More accurate but may be slower" else "Faster but uses keyframes",
                        checked = settings.preciseSeeking,
                        onCheckedChange = { viewModel.setPreciseSeeking(it) }
                    )
                    TextItem(
                        title = "Seek Duration",
                        value = "${settings.seekTime}s",
                        onClick = { showSeekTimeDialog = true }
                    )
                }
            }

            // MPV 解码器预设
            item {
                PreferenceSectionHeader("MPV Decoder")
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
                    SwitchItem(
                        title = "Vulkan 渲染上下文",
                        subtitle = if (settings.useVulkan) "使用 Vulkan 驱动，性能会更好" else "使用 OpenGL ES 驱动",
                        checked = settings.useVulkan,
                        onCheckedChange = { viewModel.setUseVulkan(it) }
                    )
                }
            }

            // 进度条样式
            item {
                PreferenceSectionHeader("Seekbar Style")
            }

            item {
                PreferenceCard {
                    SeekbarStyleCard(
                        currentStyle = settings.seekbarStyle,
                        onStyleChange = { viewModel.setSeekbarStyle(it) }
                    )
                }
            }

            // 手势控制
            item {
                PreferenceSectionHeader("Gesture Controls")
            }

            item {
                PreferenceCard {
                    DoubleTapModeCard(
                        currentMode = settings.doubleTapMode,
                        onModeChange = { viewModel.setDoubleTapMode(it) }
                    )
                    // 只有在快进/快退模式时才显示秒数设置
                    if (settings.doubleTapMode == 1) {
                        TextItem(
                            title = "Double-tap Seek Duration",
                            value = "${settings.doubleTapSeekSeconds}s",
                            onClick = { showDoubleTapSeekDialog = true }
                        )
                    }
                    SliderItem(
                        title = "亮度灵敏度",
                        value = settings.brightnessSensitivity,
                        valueRange = 0.5f..5.0f,
                        steps = 8,
                        onValueChange = { viewModel.setBrightnessSensitivity(Math.round(it * 10f) / 10f) },
                        valueFormatter = { String.format("%.1fx", it) }
                    )
                    SliderItem(
                        title = "音量灵敏度",
                        value = settings.volumeSensitivity,
                        valueRange = 50f..300f,
                        steps = 24,
                        onValueChange = { viewModel.setVolumeSensitivity(Math.round(it).toFloat()) },
                        valueFormatter = { "${Math.round(it)}" }
                    )
                }
            }

            // 音量控制
            item {
                PreferenceSectionHeader("Volume Controls")
            }

            item {
                PreferenceCard {
                    SwitchItem(
                        title = "Control System Volume",
                        subtitle = if (settings.controlSystemVolume) "Volume changes persist after exit" else "Restore previous volume after exit",
                        checked = settings.controlSystemVolume,
                        onCheckedChange = { viewModel.setControlSystemVolume(it) }
                    )
                    SwitchItem(
                        title = "Volume Boost",
                        subtitle = if (settings.volumeBoost) "Volume can exceed 100%, up to 300%" else "Volume limited to 1-100%",
                        checked = settings.volumeBoost,
                        onCheckedChange = { viewModel.setVolumeBoost(it) }
                    )
                }
            }

            // 倍速控制
            item {
                PreferenceSectionHeader("Speed Controls")
            }

            item {
                PreferenceCard {
                    SwitchItem(
                        title = "Remember Playback Speed",
                        subtitle = if (settings.rememberSpeed) "Always use last set speed" else "Reset to 1x on each video",
                        checked = settings.rememberSpeed,
                        onCheckedChange = { viewModel.setRememberSpeed(it) }
                    )
                    SliderItem(
                        title = "长按倍速",
                        value = settings.longPressSpeed,
                        valueRange = 1.0f..6.0f,
                        steps = 49,
                        onValueChange = { viewModel.setLongPressSpeed(Math.round(it * 10f) / 10f) },
                        valueFormatter = { String.format("%.1fx", it) }
                    )
                }
            }

            // 自动连播
            item {
                PreferenceSectionHeader("Auto Play")
            }

            item {
                PreferenceCard {
                    SwitchItem(
                        title = "Auto Play Next",
                        subtitle = if (settings.autoPlayNext) "Automatically play next video" else "Stop after current video",
                        checked = settings.autoPlayNext,
                        onCheckedChange = { viewModel.setAutoPlayNext(it) }
                    )
                    SwitchItem(
                        title = "Exit Player After Playback",
                        subtitle = if (settings.closeAfterEOF) "Auto close player after last video" else "Stay on current screen",
                        checked = settings.closeAfterEOF,
                        onCheckedChange = { viewModel.setCloseAfterEOF(it) }
                    )
                }
            }

            // 章节控制
            item {
                PreferenceSectionHeader("章节与缩略图")
            }

            item {
                PreferenceCard {
                    SwitchItem(
                        title = "Show Chapter Progress Bar",
                        subtitle = if (settings.chapterBarEnabled) "Show chapter markers and current chapter name on progress bar" else "Hide chapter-related info",
                        checked = settings.chapterBarEnabled,
                        onCheckedChange = { viewModel.setChapterBarEnabled(it) }
                    )
                    SwitchItem(
                        title = "进度条缩略图预览",
                        subtitle = if (settings.seekbarThumbnailEnabled) "拖动进度条时显示视频画面预览" else "拖动进度条时不显示缩略图",
                        checked = settings.seekbarThumbnailEnabled,
                        onCheckedChange = { viewModel.setSeekbarThumbnailEnabled(it) }
                    )
                }
            }

            // 画质增强
            item {
                PreferenceSectionHeader("Image Enhancement")
            }

            item {
                PreferenceCard {
                    SwitchItem(
                        title = "Remember Upscale Mode",
                        subtitle = if (settings.anime4KMemory) "Remember last used Anime4K mode" else "Always start with Anime4K off",
                        checked = settings.anime4KMemory,
                        onCheckedChange = { viewModel.setAnime4KMemory(it) }
                    )
                    Anime4KQualitySelector(
                        currentQuality = settings.anime4KQuality,
                        onQualityChange = { viewModel.setAnime4KQuality(it) }
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
                    "Restart Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    "Changing the decoder preset requires restarting the app. Restart now?",
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
                    Text("Restart", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            "Double-tap Gesture",
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
                    "Pause/Play",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (currentMode == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (currentMode == 0) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    "Double-tap anywhere to pause or play",
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
                    "Seek Forward/Backward",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (currentMode == 1) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (currentMode == 1) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    "Double-tap left to rewind, right to fast-forward",
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
    MpvProfileOption("fast", "Fast", "Hardware decode + bilinear scale, lowest power consumption (Recommended)"),
    MpvProfileOption("default", "Default", "Balanced quality and performance"),
    MpvProfileOption("high-quality", "High Quality", "High quality rendering with ewa_lanczossharp scaling"),
    MpvProfileOption("gpu-hq", "GPU HQ", "GPU high quality mode with debanding and post-processing"),
    MpvProfileOption("low-latency", "Low Latency", "Low latency mode for live/streaming"),
    MpvProfileOption("sw-fast", "SW Fast", "Software decoding, lowest GPU load but highest CPU usage"),
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
            "Decoder Preset",
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
            "Seekbar Style",
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
                Column {
                    Text(
                        style.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Text(
                        when (style) {
                            SeekbarStyle.Standard -> "Classic thin track with dot indicator, clean and clear"
                            SeekbarStyle.Wavy -> "Dynamic wave animation, lively and smooth"
                            SeekbarStyle.Thick -> "Wide track, easy to touch, bold and clear"
                        },
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
            "Upscale Quality",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(MaterialTheme.spacing.small))

        QualityOption(
            label = "Smooth",
            subtitle = "Lowest GPU load for smoother playback",
            isSelected = currentQuality == "FAST",
            onClick = { onQualityChange("FAST") }
        )
        QualityOption(
            label = "Balanced",
            subtitle = "Balanced choice between quality and performance",
            isSelected = currentQuality == "BALANCED",
            onClick = { onQualityChange("BALANCED") }
        )
        QualityOption(
            label = "High Quality",
            subtitle = "Best quality, higher GPU usage",
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
