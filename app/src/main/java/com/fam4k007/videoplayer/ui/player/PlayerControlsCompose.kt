package com.fam4k007.videoplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlin.math.abs
import android.content.res.Configuration
import android.os.BatteryManager
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.presentation.PlayerViewModel
import com.fam4k007.videoplayer.ui.components.LoadingIndicator
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay


@Composable
fun PlayerControls(
    viewModel: PlayerViewModel,
    onBackPress: () -> Unit,
    onAnime4KClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onDanmakuToggle: () -> Unit = {},
    onSubtitleClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onDanmakuClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onAspectRatioClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onMoreClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onAudioClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onScreenshotClick: () -> Unit = {},
    onVideoTitleClick: () -> Unit = {},
    onRestartFromBeginning: () -> Unit = {},
    onRotateClick: () -> Unit = {},
    onSpeedClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onChapterClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    // 收集ViewModel状态
    val paused by viewModel.paused.collectAsState()
    val controlsShown by viewModel.controlsShown.collectAsState()
    val areControlsLocked by viewModel.areControlsLocked.collectAsState()
    val anime4KMode by viewModel.anime4KMode.collectAsState()

    // 当控制面板显示时，启动初始定时器
    LaunchedEffect(controlsShown, paused) {
        if (controlsShown && paused != true) {
            viewModel.resetAutoHideTimer()
        }
    }

    // 播放界面动画开关（从设置读取，默认关闭）
    val controlsAnimationEnabled by viewModel.controlsAnimationEnabled.collectAsState()
    val hasAnimation = controlsAnimationEnabled
    val animEnter = if (hasAnimation)
        androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it })
    else androidx.compose.animation.EnterTransition.None
    val animExit = if (hasAnimation)
        androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it })
    else androidx.compose.animation.ExitTransition.None
    val animEnterFromBottom = if (hasAnimation)
        androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it })
    else androidx.compose.animation.EnterTransition.None
    val animExitToBottom = if (hasAnimation)
        androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
    else androidx.compose.animation.ExitTransition.None

    // 检测屏幕方向
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 手势层（处理点击、双击、滑动）
        GestureHandler(
            viewModel = viewModel
        )

        if (isPortrait) {
            // ═══ 竖屏模式：使用独立的全屏控制组件 ═══
            // 顶部控制面板（带显示/隐藏动画）
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsShown && !areControlsLocked,
                enter = animEnter,
                exit = animExit,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                PortraitTopBar(
                    viewModel = viewModel,
                    onBackPress = onBackPress,
                    onSubtitleClick = onSubtitleClick,
                    onDanmakuClick = onDanmakuClick,
                    onAspectRatioClick = onAspectRatioClick,
                    onMoreClick = onMoreClick,
                    onAudioClick = onAudioClick,
                    onVideoTitleClick = onVideoTitleClick
                )
            }

            // 底部控制面板（带显示/隐藏动画）
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsShown && !areControlsLocked,
                enter = animEnterFromBottom,
                exit = animExitToBottom,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PortraitBottomControls(
                    viewModel = viewModel,
                    onAnime4KClick = onAnime4KClick,
                    onDanmakuToggle = onDanmakuToggle,
                    onRotateClick = onRotateClick,
                    onSpeedClick = onSpeedClick,
                    onChapterClick = onChapterClick
                )
            }
        } else {
            // ═══ 横屏模式：使用现有控制组件 ═══
            // 顶部控制面板（带显示/隐藏动画）
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsShown && !areControlsLocked,
                enter = animEnter,
                exit = animExit,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopControlPanel(
                    viewModel = viewModel,
                    onBackPress = onBackPress,
                    onSubtitleClick = onSubtitleClick,
                    onDanmakuClick = onDanmakuClick,
                    onAspectRatioClick = onAspectRatioClick,
                    onMoreClick = onMoreClick,
                    onAudioClick = onAudioClick,
                    onVideoTitleClick = onVideoTitleClick
                )
            }

            // 底部控制面板（带显示/隐藏动画）
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsShown && !areControlsLocked,
                enter = animEnterFromBottom,
                exit = animExitToBottom,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomControlPanel(
                    viewModel = viewModel,
                    onAnime4KClick = onAnime4KClick,
                    onDanmakuToggle = onDanmakuToggle,
                    onRotateClick = onRotateClick,
                    onSpeedClick = onSpeedClick,
                    onChapterClick = onChapterClick,
                    modifier = Modifier
                )
            }
        }

        // 锁定时：左右解锁按钮
        UnlockButtons(viewModel = viewModel)

        // 右侧叠加控件：截图 + 锁定（控制栏显示或锁定时可见）
        RightSideControls(
            viewModel = viewModel,
            onScreenshotClick = onScreenshotClick,
            hasAnimation = hasAnimation
        )

        // 手势指示器（亮度/音量）
        GestureIndicators(
            viewModel = viewModel
        )

        // Seek指示器（快进/快退按钮点击提示）
        SeekIndicator(
            viewModel = viewModel
        )

        // 水平滑动 Seek 预览（正在滑动时显示目标时间 + 偏移量）
        SwipeSeekOverlay(viewModel = viewModel)

        // 长按倍速提示（正在长按时显示当前倍速）
        LongPressSpeedOverlay(viewModel = viewModel)

        // 恢复播放进度提示
        ResumeProgressToast(
            viewModel = viewModel,
            onRestartFromBeginning = onRestartFromBeginning
        )

        // 暂停指示器（暂停时在屏幕中央短暂显示后淡出）
        PauseIndicator(viewModel = viewModel)

        // 实时网速显示（仅在线播放时显示，位于截图按钮上方）
        DownloadSpeedOverlay(viewModel = viewModel)

        // 加载动画（在线视频缓冲/加载时显示，覆盖在所有控件之上）
        LoadingOverlay(viewModel = viewModel)
    }
}

/**
 * 底部控制面板组件
 *
 * 功能：
 * - 进度条（拖动Seek，支持实时预览；拖动期间暂停自动隐藏计时器）
 * - 时间显示（当前/总时长，支持 MM:SS 和 HH:MM:SS 格式）
 * - 快退N秒按钮（时长从设置读取）
 * - 上一集按钮
 * - 播放/暂停按钮（中央大按钮，64dp）
 * - 下一集按钮
 * - 快进N秒按钮（时长从设置读取）
 * - 倍速按钮（循环切换用户自定义倍速列表）
 * - 弹幕显示/隐藏按钮
 * - Anime4K 超分辨率入口按钮
 */
@Composable
fun BottomControlPanel(
    viewModel: PlayerViewModel,
    onAnime4KClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onDanmakuToggle: () -> Unit = {},
    onRotateClick: () -> Unit = {},
    onSpeedClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onChapterClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    // 收集状态
    val paused by viewModel.paused.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val precisePosition by viewModel.precisePosition.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val hasPrevious by viewModel.hasPrevious.collectAsState()
    val hasNext by viewModel.hasNext.collectAsState()
    val danmakuVisible by viewModel.danmakuVisible.collectAsState()
    val anime4KMode by viewModel.anime4KMode.collectAsState()
    val seekTimeSeconds by viewModel.seekTimeSeconds.collectAsState()
    val seekbarStyleName by viewModel.seekbarStyle.collectAsState()
    val customSpeedPresets by viewModel.customSpeedPresets.collectAsState()
    val showRemainingTime by viewModel.showRemainingTime.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val currentChapterName by viewModel.currentChapterName.collectAsState()
    val hasChapters by viewModel.hasChapters.collectAsState()
    val chapterBarEnabled by viewModel.chapterBarEnabled.collectAsState()
    val gpuNext by viewModel.gpuNext.collectAsState()
    val skipSegments by viewModel.skipSegments.collectAsState()
    val currentSkippableSegment by viewModel.currentSkippableSegment.collectAsState()
    val showSkipChip by viewModel.showSkipChip.collectAsState()

    // 检测屏幕方向
    val configuration = LocalContext.current.resources.configuration
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

    // 用户拖动进度条时的临时位置
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // 缩略图预览状态
    val thumbnailBitmap by viewModel.thumbnailBitmap.collectAsState()
    val thumbnailTimeSec by viewModel.thumbnailTimeSec.collectAsState()
    val thumbnailFraction by viewModel.thumbnailFraction.collectAsState()
    val thumbnailLoading by viewModel.thumbnailLoading.collectAsState()

    // 计算当前显示的进度（拖动时显示临时位置，否则显示实际位置）
    val displayPosition = (if (isDragging) {
        sliderPosition ?: precisePosition.toFloat()
    } else {
        precisePosition.toFloat()
    }).coerceAtLeast(0f)

    // Anime4K 模式缩写文字
    val anime4KLabel = when (anime4KMode) {
        com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.OFF -> "关"
        com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.A -> "A"
        com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.B -> "B"
        com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.C -> "C"
        com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.A_PLUS -> "A+"
        com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.B_PLUS -> "B+"
        com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.C_PLUS -> "C+"
    }
    val anime4KActive = anime4KMode != com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.OFF
    var seekBarWidth by remember { mutableFloatStateOf(0f) }
    val totalMs = (duration * 1000L).coerceAtLeast(1L)

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
            )
            .padding(horizontal = 16.dp, vertical = 0.dp)
    ) {
        // 进度条整体下移
        Spacer(modifier = Modifier.height(12.dp))

        // 章节名称行（仅在有章节信息且启用章节控制时显示）
        if (hasChapters && chapterBarEnabled) {
            var chapterBounds by remember { mutableStateOf(android.graphics.Rect()) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val r = coords.boundsInWindow()
                            chapterBounds = android.graphics.Rect(
                                r.left.toInt(), r.top.toInt(),
                                r.right.toInt(), r.bottom.toInt()
                            )
                        }
                        .clickable {
                            chapterBounds.let { b ->
                                onChapterClick(b.left, b.top, b.width(), b.height())
                            }
                            viewModel.resetAutoHideTimer()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentChapterName ?: "",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "❯",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 缩略图预览浮层 + Skip 按钮（零高度，浮在进度条正上方）
        Box(
            Modifier.fillMaxWidth().height(0.dp)
                .wrapContentHeight(unbounded = true, align = Alignment.Bottom)
        ) {
            val thumbnailChapterTitle = remember(chapters, thumbnailTimeSec) {
                chapters.lastOrNull { it.timeSeconds <= thumbnailTimeSec }?.title
            }
            SeekbarThumbnailPreview(
                bitmap = thumbnailBitmap,
                timeSec = thumbnailTimeSec,
                fraction = thumbnailFraction,
                show = isDragging && (thumbnailBitmap != null || thumbnailLoading),
                isLoading = thumbnailLoading && isDragging,
                chapterTitle = thumbnailChapterTitle,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // Skip 按钮（OP/ED 片段中浮现，进度条右侧上方，左移避开右侧控件）
            val skipChipSegment = currentSkippableSegment
            val skipChipVisible = showSkipChip && skipChipSegment != null
            androidx.compose.animation.AnimatedVisibility(
                visible = skipChipVisible,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, bottom = 28.dp),
            ) {
                val seg = skipChipSegment ?: return@AnimatedVisibility
                androidx.compose.material3.Surface(
                    onClick = { viewModel.skipActiveSegment() },
                    shape = RoundedCornerShape(999.dp),
                    color = seg.type.accentColor.copy(alpha = 0.92f),
                ) {
                    Text(
                        text = seg.type.label,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    )
                }
            }
        }

        // 进度条行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 当前时间
            Text(
                text = formatTime(displayPosition.toInt()),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp)
            )

            // 进度滑块
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { coords ->
                        seekBarWidth = coords.size.width.toFloat()
                    }
            ) {
                val seekbarStyle = SeekbarStyle.fromName(seekbarStyleName)
                val chapterTimes = if (chapterBarEnabled) chapters.map { it.timeSeconds.toFloat() } else emptyList()
                CustomSeekbar(
                    progress = displayPosition,
                    duration = duration.toFloat().coerceAtLeast(1f),
                    seekbarStyle = seekbarStyle,
                    accentColor = MaterialTheme.colorScheme.primary,
                    paused = paused == true,
                    isDragging = isDragging,
                    chapters = chapterTimes,
                    onChapterClick = { index ->
                        viewModel.seekToChapter(index)
                        viewModel.resetAutoHideTimer()
                    },
                    skipSegments = skipSegments,
                    onSeek = { newValue ->
                        if (!isDragging) {
                            isDragging = true
                            viewModel.setSliderDragging(true)
                            viewModel.onSeekbarDragStart(newValue)
                        }
                        sliderPosition = newValue
                        viewModel.onSeekbarDragPosition(newValue, duration.toFloat().coerceAtLeast(1f))
                    },
                    onSeekFinished = {
                        sliderPosition?.let { pos ->
                            viewModel.seekTo(pos.toInt())
                        }
                        isDragging = false
                        sliderPosition = null
                        viewModel.setSliderDragging(false)
                        viewModel.onSeekbarDragEnd()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 总时长/剩余时间（点击切换）
            val durationText = if (showRemainingTime) {
                val remaining = duration - displayPosition.toInt()
                "-${formatTime(remaining)}"
            } else {
                formatTime(duration)
            }
            Text(
                text = durationText,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable { viewModel.toggleRemainingTimeDisplay() }
            )
        }

        Spacer(modifier = Modifier.height(0.dp))

        // 控制按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isPortrait) 12.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧按钮组（超分辨率 + 弹幕开关 + 快退 + 上一集）
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(14.dp))

                // 超分辨率（Anime4K）按钮
                val context = LocalContext.current
                var anime4KBounds by remember { mutableStateOf(android.graphics.Rect()) }
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .onGloballyPositioned { coords ->
                            val r = coords.boundsInWindow()
                            anime4KBounds = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                        }
                        .clickable(enabled = !gpuNext) {
                            anime4KBounds.let { b -> onAnime4KClick(b.left, b.top, b.width(), b.height()) }
                            viewModel.resetAutoHideTimer()
                        }
                        .let { mod ->
                            if (gpuNext) {
                                mod.clickable {
                                    Toast.makeText(context, "已启用 GPU Next 渲染，无法开启超分", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                mod
                            }
                        }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "超分辨率：$anime4KLabel",
                        color = if (anime4KActive) Color.Yellow
                                else if (gpuNext) Color.Gray.copy(alpha = 0.5f)
                                else Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = if (anime4KActive) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 弹幕显示/隐藏按钮
                IconButton(
                    onClick = {
                        onDanmakuToggle()
                        viewModel.resetAutoHideTimer()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (danmakuVisible) R.drawable.ic_danmaku_visible
                            else R.drawable.ic_danmaku_hidden
                        ),
                        contentDescription = if (danmakuVisible) "隐藏弹幕" else "显示弹幕",
                        tint = if (danmakuVisible) Color.White else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 快退N秒按钮
                IconButton(
                    onClick = {
                        viewModel.seekRelative(-seekTimeSeconds)
                        viewModel.resetAutoHideTimer()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rewind_28_filled),
                        contentDescription = "快退${seekTimeSeconds}秒",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 上一集按钮
                IconButton(
                    onClick = {
                        viewModel.previousVideo()
                        viewModel.resetAutoHideTimer()
                    },
                    enabled = hasPrevious,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_player_previous1),
                        contentDescription = "上一集",
                        tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 中央播放/暂停按钮
            IconButton(
                onClick = {
                    viewModel.togglePlayPause()
                    viewModel.resetAutoHideTimer()
                },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (paused == true) R.drawable.ic_player_play1 else R.drawable.ic_player_pause1
                    ),
                    contentDescription = if (paused == true) "播放" else "暂停",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 右侧按钮组（下一集 + 快进 + 倍速）
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 下一集按钮
                IconButton(
                    onClick = {
                        viewModel.nextVideo()
                        viewModel.resetAutoHideTimer()
                    },
                    enabled = hasNext,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_player_next1),
                        contentDescription = "下一集",
                        tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 快进N秒按钮
                IconButton(
                    onClick = {
                        viewModel.seekRelative(seekTimeSeconds)
                        viewModel.resetAutoHideTimer()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fast_forward_28_filled),
                        contentDescription = "快进${seekTimeSeconds}秒",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 倍速按钮（弹出对话框选择）
                var speedBounds by remember { mutableStateOf(android.graphics.Rect()) }
                IconButton(
                    onClick = {
                        speedBounds.let { b -> onSpeedClick(b.left, b.top, b.width(), b.height()) }
                        viewModel.resetAutoHideTimer()
                    },
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val r = coords.boundsInWindow()
                            speedBounds = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                        }
                        .size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.top_speed_24_regular),
                            contentDescription = "倍速",
                            tint = if (speed != 1.0f) Color.Yellow else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        if (speed != 1.0f) {
                            Text(
                                text = formatSpeed(speed),
                                color = Color.Yellow,
                                fontSize = 7.sp,
                                modifier = Modifier.offset(y = 13.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 旋转按钮 — 最右侧（横屏和竖屏都显示）
                IconButton(
                    onClick = {
                        onRotateClick()
                        viewModel.resetAutoHideTimer()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.crop_arrow_rotate_24_filled),
                        contentDescription = "旋转",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        }
    }
}

/**
 * 格式化时间显示（秒 → MM:SS 或 HH:MM:SS）
 */
private fun formatTime(seconds: Int): String {
    if (seconds < 0) return "00:00"
    
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

/**
 * 格式化倍速显示（去除不必要的小数位）
 * 例如：1.0 → "1x"，1.25 → "1.25x"，2.0 → "2x"
 */
private fun formatSpeed(speed: Float): String {
    return if (speed == kotlin.math.floor(speed.toDouble()).toFloat()) {
        "${speed.toInt()}x"
    } else {
        "${speed}x"
    }
}

/**
 * 水平滑动 Seek 预览覆盖层
 * 正在滑动时居中显示：目标时间 + 偏移量（如 "01:23\n[+0:10]"）
 */
@Composable
fun SwipeSeekOverlay(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val swipeSeekPreview by viewModel.swipeSeekPreview.collectAsState()

    androidx.compose.animation.AnimatedVisibility(
        visible = swipeSeekPreview != null,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
        modifier = modifier.fillMaxSize()
    ) {
        val preview = swipeSeekPreview ?: return@AnimatedVisibility
        val targetTime = formatTime(preview.targetSeconds)
        val delta = preview.deltaSeconds
        val sign = if (delta >= 0) "+" else "-"
        val deltaText = formatTime(kotlin.math.abs(delta))

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 170.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = targetTime,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "[$sign$deltaText]",
                    color = if (delta >= 0) Color(0xFF4FC3F7) else Color(0xFFFF8A65),
                    fontSize = 16.sp
                )
            }
        }
    }
}

// =====================================================================
// 顶部控制面板
// =====================================================================

/**
 * 顶部控制面板
 *
 * 功能：
 * - 渐变背景（上深下透明）
 * - 返回按钮
 * - 视频标题（可点击，显示播放列表）
 * - 电量百分比 + 当前时间
 * - 字幕按钮
 * - 画面比例按钮
 * - 锁定按钮
 * - 更多选项按钮
 */
@Composable
fun TopControlPanel(
    viewModel: PlayerViewModel,
    onBackPress: () -> Unit,
    onSubtitleClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onDanmakuClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onAspectRatioClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onMoreClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onAudioClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onVideoTitleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val videoTitle by viewModel.videoTitle.collectAsState()
    val context = LocalContext.current

    // 电量（BroadcastReceiver，跟随 Composable 生命周期注册/注销）
    val batteryLevel by produceState(initialValue = -1, context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (level >= 0 && scale > 0) value = level * 100 / scale
            }
        }
        context.registerReceiver(receiver, filter)
        awaitDispose { context.unregisterReceiver(receiver) }
    }

    // 时钟（每 30 秒刷新一次）
    var clockTime by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            clockTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
    }

    // 追踪各按钮在屏幕中的位置，用于对话框定位
    var subtitleBounds by remember { mutableStateOf(android.graphics.Rect()) }
    var danmakuBounds by remember { mutableStateOf(android.graphics.Rect()) }
    var ratioBounds by remember { mutableStateOf(android.graphics.Rect()) }
    var moreBounds by remember { mutableStateOf(android.graphics.Rect()) }
    
    // 获取屏幕宽度用于坐标约束
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = (configuration.screenWidthDp * context.resources.displayMetrics.density).toInt()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.72f),
                        Color.Black.copy(alpha = 0.45f),
                        Color.Transparent
                    )
                )
            )
            .padding(start = 4.dp, top = 18.dp, end = 80.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回按钮
        IconButton(
            onClick = onBackPress,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_left_48_regular),
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        // 视频标题（占剩余空间，可点击展开视频列表）
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onVideoTitleClick)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = videoTitle,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 电量 + 时间（两行小字）
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(end = 2.dp)
        ) {
            if (batteryLevel >= 0) {
                Text(
                    text = "$batteryLevel%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 12.sp
                )
            }
            Text(
                text = clockTime,
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp
            )
        }

        Spacer(modifier = Modifier.width(18.dp))

        // 字幕按钮
        IconButton(
            onClick = {
                subtitleBounds.let { b -> onSubtitleClick(b.left, b.top, b.width(), b.height()) }
                viewModel.resetAutoHideTimer()
            },
            modifier = Modifier
                .size(38.dp)
                .onGloballyPositioned { coords ->
                    val r = coords.boundsInWindow()
                    // 约束X坐标到屏幕范围内
                    val x = r.left.toInt().coerceAtMost(screenWidthPx - r.width.toInt())
                    val y = r.top.toInt()
                    subtitleBounds = android.graphics.Rect(
                        x, y, x + r.width.toInt(), y + r.height.toInt()
                    )
                }
        ) {
            Icon(
                painter = painterResource(R.drawable.subtitles_24_filled),
                contentDescription = "字幕",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 弹幕按钮
        IconButton(
            onClick = {
                danmakuBounds.let { b -> onDanmakuClick(b.left, b.top, b.width(), b.height()) }
                viewModel.resetAutoHideTimer()
            },
            modifier = Modifier
                .size(38.dp)
                .onGloballyPositioned { coords ->
                    val r = coords.boundsInWindow()
                    val x = r.left.toInt().coerceAtMost(screenWidthPx - r.width.toInt())
                    val y = r.top.toInt()
                    danmakuBounds = android.graphics.Rect(
                        x, y, x + r.width.toInt(), y + r.height.toInt()
                    )
                }
        ) {
            Icon(
                painter = painterResource(R.drawable.comment_note_24_filled),
                contentDescription = "弹幕",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 画面比例按钮
        IconButton(
            onClick = {
                ratioBounds.let { b -> onAspectRatioClick(b.left, b.top, b.width(), b.height()) }
                viewModel.resetAutoHideTimer()
            },
            modifier = Modifier
                .size(38.dp)
                .onGloballyPositioned { coords ->
                    val r = coords.boundsInWindow()
                    val x = r.left.toInt().coerceAtMost(screenWidthPx - r.width.toInt())
                    val y = r.top.toInt()
                    ratioBounds = android.graphics.Rect(
                        x, y, x + r.width.toInt(), y + r.height.toInt()
                    )
                }
        ) {
            Icon(
                painter = painterResource(R.drawable.ratio_one_to_one_24_filled),
                contentDescription = "画面比例",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 音频按钮
        var audioBounds by remember { mutableStateOf(android.graphics.Rect()) }
        IconButton(
            onClick = {
                audioBounds.let { b -> onAudioClick(b.left, b.top, b.width(), b.height()) }
                viewModel.resetAutoHideTimer()
            },
            modifier = Modifier
                .size(38.dp)
                .onGloballyPositioned { coords ->
                    val r = coords.boundsInWindow()
                    val x = r.left.toInt().coerceAtMost(screenWidthPx - r.width.toInt())
                    val y = r.top.toInt()
                    audioBounds = android.graphics.Rect(
                        x, y, x + r.width.toInt(), y + r.height.toInt()
                    )
                }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_music_note_1_20_filled),
                contentDescription = "音频",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 更多选项按钮
        IconButton(
            onClick = {
                moreBounds.let { b -> onMoreClick(b.left, b.top, b.width(), b.height()) }
                viewModel.resetAutoHideTimer()
            },
            modifier = Modifier
                .size(38.dp)
                .onGloballyPositioned { coords ->
                    val r = coords.boundsInWindow()
                    // 约束X坐标到屏幕范围内，防止超出屏幕宽度
                    val x = r.left.toInt().coerceAtMost(screenWidthPx - r.width.toInt())
                    val y = r.top.toInt()
                    moreBounds = android.graphics.Rect(
                        x, y, x + r.width.toInt(), y + r.height.toInt()
                    )
                }
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vertical_48_regular),
                contentDescription = "更多",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// =====================================================================
// 解锁按钮（锁定模式下显示）
// =====================================================================

/**
 * 锁定模式下显示的解锁按钮
 *
 * - 锁定后立即显示，3 秒无操作后自动淡出
 * - 点击任意解锁按钮（左/右）均可解锁
 * - 单击屏幕（手势层会通知 ViewModel showControls）时重新显示
 */
@Composable
fun UnlockButtons(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val areControlsLocked by viewModel.areControlsLocked.collectAsState()
    val unlockButtonsVisible by viewModel.unlockButtonsVisible.collectAsState()

    if (!areControlsLocked) return

    Box(modifier = modifier.fillMaxSize()) {
        // 仅保留左侧解锁按钮（右侧锁已由 RightSideControls 接管）
        androidx.compose.animation.AnimatedVisibility(
            visible = unlockButtonsVisible,
            enter = androidx.compose.animation.EnterTransition.None,
            exit = androidx.compose.animation.ExitTransition.None,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 56.dp)
        ) {
            IconButton(
                onClick = { viewModel.toggleLock() },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0x60000000), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    painter = painterResource(R.drawable.lock_open_48_filled),
                    contentDescription = "解锁",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// =====================================================================
// 右侧叠加控件（截图 + 锁定）
// =====================================================================

/**
 * 播放界面右侧叠加控件：截图按钮 + 锁定按钮
 *
 * - 控制栏未锁定时：截图和锁定按钮始终可见（跟随控制栏显示/隐藏）
 * - 控制栏锁定时：仅显示锁图标（跟随 UnlockButtons 的可见性）
 * - 截图按钮在上方，锁定按钮在下方
 */
@Composable
fun RightSideControls(
    viewModel: PlayerViewModel,
    onScreenshotClick: () -> Unit = {},
    hasAnimation: Boolean = false,
    modifier: Modifier = Modifier
) {
    val controlsShown by viewModel.controlsShown.collectAsState()
    val areControlsLocked by viewModel.areControlsLocked.collectAsState()
    val unlockButtonsVisible by viewModel.unlockButtonsVisible.collectAsState()

    // 控制栏未锁定且显示时，或锁定时解锁按钮可见时，显示右侧控件
    val showRightControls = (!areControlsLocked && controlsShown) || (areControlsLocked && unlockButtonsVisible)

    val animEnter = if (hasAnimation) androidx.compose.animation.fadeIn() else androidx.compose.animation.EnterTransition.None
    val animExit = if (hasAnimation) androidx.compose.animation.fadeOut() else androidx.compose.animation.ExitTransition.None

    Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = showRightControls,
            enter = animEnter,
            exit = animExit,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
        ) {
            // 锁定时只显示解锁按钮，与左侧解锁按钮同一水平线（居中）
            if (areControlsLocked) {
                IconButton(
                    onClick = { viewModel.toggleLock() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0x60000000), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.lock_open_48_filled),
                        contentDescription = "解锁",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // 未锁定时显示截图 + 锁定
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 截图按钮
                    IconButton(
                        onClick = {
                            onScreenshotClick()
                            viewModel.resetAutoHideTimer()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0x60000000), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_screen_cut_20_regular),
                            contentDescription = "截图",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 锁定按钮
                    IconButton(
                        onClick = { viewModel.toggleLock() },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0x60000000), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.lock_closed_48_filled),
                            contentDescription = "锁定",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 长按倍速提示覆盖层
 * 长按时顶部居中显示"正在X.Xx倍速播放"
 * 左右滑动调速时显示速度档位选择条
 */
@Composable
fun LongPressSpeedOverlay(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val preferencesManager: com.fam4k007.videoplayer.preferences.PreferencesManager = org.koin.compose.koinInject()
    val isLongPressing by viewModel.isLongPressing.collectAsState()
    val isDynamicSpeedActive by viewModel.isDynamicSpeedActive.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val speedPresets = viewModel.dynamicSpeedPresets
    val showHint = remember { mutableStateOf(!preferencesManager.hasDynamicSpeedBeenUsed()) }

    // 用户首次使用动态调速后，标记已掌握并隐藏提示
    LaunchedEffect(isDynamicSpeedActive) {
        if (isDynamicSpeedActive && showHint.value) {
            preferencesManager.setDynamicSpeedUsed()
            showHint.value = false
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = isLongPressing,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 15.dp)
            ) {
                // 速度文字提示
                Text(
                    text = if (isDynamicSpeedActive)
                        "正在${String.format("%.2f", speed)}倍速播放"
                    else
                        "正在${String.format("%.1f", speed)}倍速播放",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
                
                // 未掌握动态调速且非动态调速时显示滑动提示
                if (showHint.value && !isDynamicSpeedActive) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "可通过左右滑动，临时调节长按播放的倍数",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                
                // 动态调速时显示速度档位条
                if (isDynamicSpeedActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        speedPresets.forEach { preset ->
                            val isSelected = abs(speed.toFloat() - preset) < 0.01f
                            Text(
                                text = if (preset == preset.toInt().toFloat()) 
                                    "${preset.toInt()}x" 
                                else 
                                    "${String.format("%.1f", preset)}x",
                                color = if (isSelected) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.6f),
                                fontSize = if (isSelected) 14.sp else 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 恢复播放进度提示（左下角小胶囊）
 * 进入视频时自动显示，5秒后自动消失
 */
@Composable
fun ResumeProgressToast(
    viewModel: PlayerViewModel,
    onRestartFromBeginning: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible by viewModel.resumeToastVisible.collectAsState()
    val savedPosition by viewModel.savedPosition.collectAsState()

    LaunchedEffect(visible) {
        if (visible) {
            delay(5_000L)
            viewModel.hideResumeToast()
        }
    }

    val positionText = remember(savedPosition) {
        val totalSecs = savedPosition.toInt()
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)) +
                androidx.compose.animation.slideInHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(300),
                    initialOffsetX = { -it }
                ),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(250)) +
               androidx.compose.animation.slideOutHorizontally(
                   animationSpec = androidx.compose.animation.core.tween(300),
                   targetOffsetX = { -it }
               ),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 20.dp, bottom = 130.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "已为您恢复至 $positionText",
                    color = Color.White,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "重新开始",
                    color = Color(0xFF4FC3F7),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            viewModel.hideResumeToast()
                            onRestartFromBeginning()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "✕",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { viewModel.hideResumeToast() }
                        .padding(4.dp)
                )
            }
        }
    }
}

/**
 * 暂停指示器
 * 进入暂停状态时在屏幕中央短暂出现后淡出
 */
@Composable
fun PauseIndicator(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val paused by viewModel.paused.collectAsState()
    val controlsShown by viewModel.controlsShown.collectAsState()

    var showIcon by remember { mutableStateOf(false) }

    LaunchedEffect(paused) {
        if (paused == true && controlsShown != true) {
            showIcon = true
            delay(800L)
            showIcon = false
        } else {
            showIcon = false
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showIcon,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(500)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_pause_circle_12_regular),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(80.dp)
            )
        }
    }
}

/**
 * 实时网速显示
 * 仅在线播放时、且控制面板可见时显示，位于屏幕右侧中间位置
 */
@Composable
fun DownloadSpeedOverlay(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val isOnline by viewModel.isOnlineVideo.collectAsState()
    val speedKbps by viewModel.downloadSpeedKbps.collectAsState()
    val controlsShown by viewModel.controlsShown.collectAsState()

    if (!isOnline || speedKbps <= 0 || !controlsShown) return

    val speedText = if (speedKbps >= 1024) {
        String.format("%.1f MB/s", speedKbps / 1024.0)
    } else {
        "$speedKbps KB/s"
    }

    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = speedText,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp, bottom = 160.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * 加载动画覆盖层（在线视频缓冲/加载时显示转圈动画）
 */
@Composable
fun LoadingOverlay(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val isLoading by viewModel.isLoading.collectAsState()

    androidx.compose.animation.AnimatedVisibility(
        visible = isLoading,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    }
}
