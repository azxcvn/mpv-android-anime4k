package com.fam4k007.videoplayer.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.presentation.PlayerViewModel
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

/**
 * 竖屏完整顶栏。横竖屏功能完全一致。
 * 从左到右：返回 → 标题（弹性）→ 电量/时间 → 字幕 → 弹幕 → 比例 → 锁定 → 更多。
 * 顶部 padding 增大，远离刘海/状态栏。
 */
@Composable
fun PortraitTopBar(
    viewModel: PlayerViewModel,
    onBackPress: () -> Unit,
    onSubtitleClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onDanmakuClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onAspectRatioClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onMoreClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onVideoTitleClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val videoTitle by viewModel.videoTitle.collectAsState()
    val context = LocalContext.current

    // 电量（BroadcastReceiver）
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

    // 时钟（每 30 秒刷新）
    var clockTime by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            clockTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
    }

    // 各按钮 bounds（用于弹出菜单位置）
    var subtitleBounds by remember { mutableStateOf(android.graphics.Rect()) }
    var danmakuBounds by remember { mutableStateOf(android.graphics.Rect()) }
    var ratioBounds by remember { mutableStateOf(android.graphics.Rect()) }
    var moreBounds by remember { mutableStateOf(android.graphics.Rect()) }

    val screenWidthPx = (context.resources.displayMetrics.widthPixels)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Black.copy(alpha = 0.72f),
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent,
                            ),
                    ),
                )
                // top 预留更大空间远离顶部
                .padding(start = 2.dp, top = 48.dp, end = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 返回按钮
        IconButton(
            onClick = onBackPress,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_left_48_regular),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        // 视频标题（弹性宽度，可点击）
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClick = onVideoTitleClick)
                    .padding(vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = videoTitle,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 电量 + 时间（两行小字）
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(end = 2.dp),
        ) {
            if (batteryLevel >= 0) {
                Text(
                    text = "$batteryLevel%",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 11.sp,
                )
            }
            Text(
                text = clockTime,
                color = Color.White,
                fontSize = 9.sp,
                lineHeight = 11.sp,
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 字幕按钮
        IconButton(
            onClick = {
                subtitleBounds.let { b -> onSubtitleClick(b.left, b.top, b.width(), b.height()) }
                viewModel.resetAutoHideTimer()
            },
            modifier =
                Modifier
                    .size(32.dp)
                    .onGloballyPositioned { coords ->
                        val r = coords.boundsInWindow()
                        val x = r.left.toInt().coerceAtMost(screenWidthPx - r.width.toInt())
                        subtitleBounds =
                            android.graphics.Rect(x, r.top.toInt(), x + r.width.toInt(), r.top.toInt() + r.height.toInt())
                    },
        ) {
            Icon(
                painter = painterResource(R.drawable.subtitles_24_filled),
                contentDescription = "Subtitles",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 弹幕按钮
        IconButton(
            onClick = {
                danmakuBounds.let { b -> onDanmakuClick(b.left, b.top, b.width(), b.height()) }
                viewModel.resetAutoHideTimer()
            },
            modifier =
                Modifier
                    .size(32.dp)
                    .onGloballyPositioned { coords ->
                        val r = coords.boundsInWindow()
                        val x = r.left.toInt().coerceAtMost(screenWidthPx - r.width.toInt())
                        danmakuBounds =
                            android.graphics.Rect(x, r.top.toInt(), x + r.width.toInt(), r.top.toInt() + r.height.toInt())
                    },
        ) {
            Icon(
                painter = painterResource(R.drawable.comment_note_24_filled),
                contentDescription = "Danmaku",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 画面比例按钮
        IconButton(
            onClick = {
                ratioBounds.let { b -> onAspectRatioClick(b.left, b.top, b.width(), b.height()) }
                viewModel.resetAutoHideTimer()
            },
            modifier =
                Modifier
                    .size(32.dp)
                    .onGloballyPositioned { coords ->
                        val r = coords.boundsInWindow()
                        val x = r.left.toInt().coerceAtMost(screenWidthPx - r.width.toInt())
                        ratioBounds =
                            android.graphics.Rect(x, r.top.toInt(), x + r.width.toInt(), r.top.toInt() + r.height.toInt())
                    },
        ) {
            Icon(
                painter = painterResource(R.drawable.ratio_one_to_one_24_filled),
                contentDescription = "Aspect Ratio",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 锁定按钮
        IconButton(
            onClick = { viewModel.toggleLock() },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.lock_closed_48_filled),
                contentDescription = "Lock",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 更多按钮
        IconButton(
            onClick = {
                moreBounds.let { b -> onMoreClick(b.left, b.top, b.width(), b.height()) }
                viewModel.resetAutoHideTimer()
            },
            modifier =
                Modifier
                    .size(32.dp)
                    .onGloballyPositioned { coords ->
                        val r = coords.boundsInWindow()
                        val x = r.left.toInt().coerceAtMost(screenWidthPx - r.width.toInt())
                        moreBounds =
                            android.graphics.Rect(x, r.top.toInt(), x + r.width.toInt(), r.top.toInt() + r.height.toInt())
                    },
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vertical_48_regular),
                contentDescription = "More",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * 竖屏专用底部控制面板，3 行布局。
 *
 *   Row 1: 超分辨率  倍速  弹幕  旋转  （均匀分布，贴近进度条上方）
 *   Row 2: [当前时间] ———滑动条——— [总时长]
 *   Row 3:  上一集  快退   ▶/⏸   快进  下一集  （均匀分布）
 */
@Composable
fun PortraitBottomControls(
    viewModel: PlayerViewModel,
    onAnime4KClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onDanmakuToggle: () -> Unit = {},
    onRotateClick: () -> Unit = {},
    onSpeedClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onChapterClick: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
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
    val showRemainingTime by viewModel.showRemainingTime.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val currentChapterName by viewModel.currentChapterName.collectAsState()
    val hasChapters by viewModel.hasChapters.collectAsState()
    val chapterBarEnabled by viewModel.chapterBarEnabled.collectAsState()
    val gpuNext by viewModel.gpuNext.collectAsState()

    // 拖动进度状态
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // 缩略图预览状态
    val thumbnailBitmap by viewModel.thumbnailBitmap.collectAsState()
    val thumbnailTimeSec by viewModel.thumbnailTimeSec.collectAsState()
    val thumbnailFraction by viewModel.thumbnailFraction.collectAsState()
    val thumbnailLoading by viewModel.thumbnailLoading.collectAsState()
    val displayPosition =
        (if (isDragging) sliderPosition ?: precisePosition.toFloat() else precisePosition.toFloat()).coerceAtLeast(0f)

    // Anime4K 标签
    val anime4KLabel =
        when (anime4KMode) {
            com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.OFF -> "Off"
            com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.A -> "A"
            com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.B -> "B"
            com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.C -> "C"
            com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.A_PLUS -> "A+"
            com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.B_PLUS -> "B+"
            com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.C_PLUS -> "C+"
        }
    val anime4KActive = anime4KMode != com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.OFF

    var anime4KBounds by remember { mutableStateOf(android.graphics.Rect()) }
    var seekBarWidth by remember { mutableFloatStateOf(0f) }
    val totalMs = (duration * 1000L).coerceAtLeast(1L)

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.7f),
                            ),
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .navigationBarsPadding(),
        ) {
            // ── Row 1: 精简辅控（置于进度条上方，贴近进度条）──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 超分辨率文字按钮（靠左）
            val context = LocalContext.current
            Box(
                modifier =
                    Modifier
                        .height(32.dp)
                        .onGloballyPositioned { coords ->
                            val r = coords.boundsInWindow()
                            anime4KBounds =
                                android.graphics.Rect(
                                    r.left.toInt(),
                                    r.top.toInt(),
                                    r.right.toInt(),
                                    r.bottom.toInt(),
                                )
                        }
                        .clickable(enabled = !gpuNext) {
                            anime4KBounds.let { b ->
                                onAnime4KClick(b.left, b.top, b.width(), b.height())
                            }
                            viewModel.resetAutoHideTimer()
                        }
                        .let { mod ->
                            if (gpuNext) {
                                mod.clickable {
                                    Toast
                                        .makeText(
                                            context,
                                            "已启用 GPU Next 渲染，无法开启超分",
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                }
                            } else {
                                mod
                            }
                        }
                        .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Upscale: $anime4KLabel",
                    color =
                        if (anime4KActive) Color.Yellow
                        else if (gpuNext) Color.Gray.copy(alpha = 0.5f)
                        else Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight =
                        if (anime4KActive) FontWeight.Bold
                        else FontWeight.Normal,
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 倍速
            var speedBounds by remember { mutableStateOf(android.graphics.Rect()) }
            IconButton(
                onClick = {
                    speedBounds.let { b -> onSpeedClick(b.left, b.top, b.width(), b.height()) }
                    viewModel.resetAutoHideTimer()
                },
                modifier =
                    Modifier
                        .size(36.dp)
                        .onGloballyPositioned { coords ->
                            val r = coords.boundsInWindow()
                            speedBounds =
                                android.graphics.Rect(
                                    r.left.toInt(),
                                    r.top.toInt(),
                                    r.right.toInt(),
                                    r.bottom.toInt(),
                                )
                        },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.top_speed_24_regular),
                        contentDescription = "Speed",
                        tint = if (speed != 1.0f) Color.Yellow else Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    if (speed != 1.0f) {
                        Text(
                            text = formatSpeedP(speed),
                            color = Color.Yellow,
                            fontSize = 6.sp,
                            modifier = Modifier.offset(y = 12.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 弹幕显隐
            IconButton(
                onClick = {
                    onDanmakuToggle()
                    viewModel.resetAutoHideTimer()
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (danmakuVisible) R.drawable.ic_danmaku_visible
                            else R.drawable.ic_danmaku_hidden,
                        ),
                    contentDescription = if (danmakuVisible) "Hide Danmaku" else "Show Danmaku",
                    tint = if (danmakuVisible) Color.White else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 旋转按钮（靠右）
            IconButton(
                onClick = {
                    onRotateClick()
                    viewModel.resetAutoHideTimer()
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.crop_arrow_rotate_24_filled),
                    contentDescription = "Rotate",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── 章节名称行（仅在有章节信息且启用章节控制时显示）──
        if (hasChapters && chapterBarEnabled) {
            var chapterBounds by remember { mutableStateOf(android.graphics.Rect()) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 1.dp)
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
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentChapterName ?: "",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "❯",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // ── 缩略图预览浮层（零高度，浮在进度条正上方）──
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
            )
        }

        // ── Row 2: 进度条 + 时间 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatTimeP(displayPosition.toInt()),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 6.dp),
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
                        sliderPosition?.let { pos -> viewModel.seekTo(pos.toInt()) }
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
                "-${formatTimeP(remaining)}"
            } else {
                formatTimeP(duration)
            }
            Text(
                text = durationText,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable { viewModel.toggleRemainingTimeDisplay() }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Row 3: 主播放控制（均匀分布）──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 上一集
            IconButton(
                onClick = {
                    viewModel.previousVideo()
                    viewModel.resetAutoHideTimer()
                },
                enabled = hasPrevious,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_player_previous1),
                    contentDescription = "Previous",
                    tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(26.dp),
                )
            }

            // 快退 N 秒
            IconButton(
                onClick = {
                    viewModel.seekRelative(-seekTimeSeconds)
                    viewModel.resetAutoHideTimer()
                },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.rewind_28_filled),
                    contentDescription = "Rewind ${seekTimeSeconds}s",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }

            // 播放 / 暂停（大按钮）
            IconButton(
                onClick = {
                    viewModel.togglePlayPause()
                    viewModel.resetAutoHideTimer()
                },
                modifier = Modifier.size(60.dp),
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (paused == true) R.drawable.ic_player_play1
                            else R.drawable.ic_player_pause1,
                        ),
                    contentDescription = if (paused == true) "Play" else "Pause",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }

            // 快进 N 秒
            IconButton(
                onClick = {
                    viewModel.seekRelative(seekTimeSeconds)
                    viewModel.resetAutoHideTimer()
                },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.fast_forward_28_filled),
                    contentDescription = "Forward ${seekTimeSeconds}s",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }

            // 下一集
            IconButton(
                onClick = {
                    viewModel.nextVideo()
                    viewModel.resetAutoHideTimer()
                },
                enabled = hasNext,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_player_next1),
                    contentDescription = "Next",
                    tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

}

// ================== 工具函数 ==================

private fun formatTimeP(seconds: Int): String {
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

private fun formatSpeedP(speed: Float): String {
    return if (speed == kotlin.math.floor(speed.toDouble()).toFloat()) {
        "${speed.toInt()}x"
    } else {
        "${speed}x"
    }
}
