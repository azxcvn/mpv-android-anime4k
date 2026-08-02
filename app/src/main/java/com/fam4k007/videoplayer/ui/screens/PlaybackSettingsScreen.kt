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
                        title = "GPU Next Rendering",
                        subtitle = if (settings.gpuNext) "Enables correct Dolby Vision with software decoding, incompatible with 4K upscaling" else "Improves HDR rendering when enabled",
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
                        title = "Vulkan Rendering Context",
                        subtitle = if (settings.useVulkan) "Uses Vulkan driver, better performance" else "Uses OpenGL ES driver",
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
                        title = "Auto Play Next",
                        subtitle = if (settings.autoPlayNext) "Automatically play next video" else "Stop after current video",
                        checked = settings.autoPlayNext,
                        onCheckedChange = { viewModel.setAutoPlayNext(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "Exit Player After Playback",
                        subtitle = if (settings.closeAfterEOF) "Auto close player after last video" else "Stay on current screen",
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
                        title = "Show Chapter Progress Bar",
                        subtitle = if (settings.chapterBarEnabled) "Show chapter markers and current chapter name on progress bar" else "Hide chapter-related info",
                        checked = settings.chapterBarEnabled,
                        onCheckedChange = { viewModel.setChapterBarEnabled(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "Seekbar Thumbnail Preview",
                        subtitle = if (settings.seekbarThumbnailEnabled) "Show video frame preview when dragging seekbar" else "Hide thumbnail when dragging seekbar",
                        checked = settings.seekbarThumbnailEnabled,
                        onCheckedChange = { viewModel.setSeekbarThumbnailEnabled(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "Show Playback Progress Bar",
                        subtitle = if (settings.showVideoProgressBar) "Show watch progress on video list thumbnails" else "Hide progress bar, show status via label only",
                        checked = settings.showVideoProgressBar,
                        onCheckedChange = { viewModel.setShowVideoProgressBar(it) }
                    )
                    PreferenceDivider()
                    SliderItem(
                        title = "Watched Threshold",
                        value = settings.watchedThreshold.toFloat(),
                        valueRange = 50f..100f,
                        steps = 9,
                        onValueChange = { viewModel.setWatchedThreshold(it.roundToInt()) },
                        valueFormatter = { "${it.roundToInt()}%" },
                        subtitle = "Mark as watched when progress exceeds this value"
                    )
                    PreferenceDivider()
                    RotationLockSelector(
                        currentMode = settings.rotationLockMode,
                        onModeChange = { viewModel.setRotationLockMode(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "Enable Player UI Animation",
                        subtitle = if (settings.controlsAnimationEnabled) "Slide animation when showing/hiding controls" else "Controls show/hide instantly, no animation",
                        checked = settings.controlsAnimationEnabled,
                        onCheckedChange = { viewModel.setControlsAnimationEnabled(it) }
                    )
                    PreferenceDivider()
                    SwitchItem(
                        title = "Enable Drawer UI Animation",
                        subtitle = if (settings.drawerAnimationEnabled) "Right-side drawer panel has transition animation" else "Drawer panel shows/hides instantly, no animation",
                        checked = settings.drawerAnimationEnabled,
                        onCheckedChange = { viewModel.setDrawerAnimationEnabled(it) }
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
                    "Enable GPU Next Rendering",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "GPU Next is mpv's new rendering engine that improves HDR rendering and enables correct Dolby Vision with software decoding. However, it is incompatible with 4K upscaling (Anime4K), which will be automatically disabled when enabled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Note: Some devices may show a purple screen after enabling. If encountered, enable \"Vulkan Rendering Context\" below to resolve.",
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
                    Text("Continue", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpuNextWarning = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    "Custom Seek Duration",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        "Enter seek duration (1~300 seconds)",
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
                        label = { Text("Seconds") },
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
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomInput = false }) {
                    Text("Cancel")
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
                    "Seek Duration",
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
                                "${seconds}s",
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
                            if (isCustom) "Custom (${selected}s)" else "Custom",
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
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
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
                    "Custom Seek Duration",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        "Enter seek duration (1~300 seconds)",
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
                        label = { Text("Seconds") },
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
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomInput = false }) {
                    Text("Cancel")
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
                    "Double-tap Seek Duration",
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
                                "${seconds}s",
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
                            if (isCustom) "Custom (${selected}s)" else "Custom",
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
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
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
