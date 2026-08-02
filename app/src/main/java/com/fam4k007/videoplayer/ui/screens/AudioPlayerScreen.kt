package com.fam4k007.videoplayer.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.presentation.PlayerViewModel
import com.fam4k007.videoplayer.presentation.RepeatMode
import com.fam4k007.videoplayer.utils.ThumbnailCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AudioPlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val videoTitle by viewModel.videoTitle.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val position by viewModel.precisePosition.collectAsState()
    val paused by viewModel.paused.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val hasNext by viewModel.hasNext.collectAsState()
    val hasPrevious by viewModel.hasPrevious.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val videoList by viewModel.videoList.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()

    val textColor = Color.White
    val textSecondary = Color.White.copy(alpha = 0.6f)
    val accentColor = Color(0xFF64B5F6)
    val sheetBg = Color(0xFFF5F5F5)
    val sheetText = Color(0xFF1A1A1A)

    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(currentIndex) {
        val video = videoList.getOrNull(currentIndex) ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val cm = ThumbnailCacheManager.getInstance(context)
                coverBitmap = cm.getThumbnail(context, Uri.parse(video.uri), video.duration)
            } catch (_: Exception) { }
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(if (duration > 0) (position / duration).toFloat() else 0f) }
    val displayFraction = if (isDragging) dragFraction
        else if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f

    var showSpeedSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val speedSheetState = remember { MutableTransitionState(false) }
    val playlistSheetState = remember { MutableTransitionState(false) }
    speedSheetState.targetState = showSpeedSheet
    playlistSheetState.targetState = showPlaylistSheet

    Box(Modifier.fillMaxSize()) {
        // 毛玻璃背景层：封面完整铺满 + 模糊 + 深色蒙层
        if (coverBitmap != null && !coverBitmap!!.isRecycled) {
            Image(
                bitmap = coverBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp)
            )
        } else {
            // 无封面时的渐变降级
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F0F1A))
                        )
                    )
            )
        }
        // 深色蒙层
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
        )

        // 主内容层
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically)
        ) {
            Spacer(Modifier.weight(0.3f))

            // 封面 16:9
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null && !coverBitmap!!.isRecycled) {
                    Image(
                        coverBitmap!!.asImageBitmap(),
                        null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.top_speed_24_regular),
                        null,
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // 标题
            Text(
                videoTitle.ifBlank { "未命名视频" },
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(28.dp))

            // 进度条
            Column(Modifier.fillMaxWidth()) {
                Slider(
                    value = displayFraction,
                    onValueChange = { isDragging = true; dragFraction = it },
                    onValueChangeFinished = {
                        viewModel.seekTo((dragFraction * duration).toInt())
                        isDragging = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = textColor.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatTimeAudio(if (isDragging) (dragFraction * duration).toLong() else position.toLong()),
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                    Text(formatTimeAudio(duration.toLong()), color = textSecondary, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(28.dp))

            // 底部控制卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.08f)
                )
            ) {
                Column(
                    Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                ) {
                    // 五个按钮：倍速 | 上一集 | 播放/暂停 | 下一集 | 播放列表
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AudioControlButton(
                            R.drawable.top_speed_24_regular,
                            "${speed}x",
                            iconTint = textColor
                        ) {
                            showSpeedSheet = true
                        }

                        AudioControlButton(
                            R.drawable.ic_player_previous1,
                            iconTint = textColor,
                            enabled = hasPrevious
                        ) {
                            viewModel.previousVideo()
                        }

                        // 播放/暂停
                        IconButton(
                            { viewModel.togglePlayPause() },
                            Modifier.size(48.dp)
                        ) {
                            Icon(
                                painterResource(
                                    if (paused == true) R.drawable.ic_player_play1
                                    else R.drawable.ic_player_pause1
                                ),
                                null,
                                tint = textColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        AudioControlButton(
                            R.drawable.ic_player_next1,
                            iconTint = textColor,
                            enabled = hasNext
                        ) {
                            viewModel.nextVideo()
                        }

                        AudioControlButton(
                            R.drawable.ic_apps_list_20_filled,
                            iconTint = textColor
                        ) {
                            showPlaylistSheet = true
                        }
                    }
                }
            }

            Spacer(Modifier.weight(0.1f))
        }

        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painterResource(R.drawable.arrow_left_48_regular),
                    "返回",
                    tint = textSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text("听视频", color = textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(48.dp))
        }
    }

    AnimatedVisibility(
        visibleState = speedSheetState,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        SpeedBottomSheet(
            speed, sheetBg, sheetText,
            { viewModel.setSpeed(it.toDouble()); showSpeedSheet = false }
        ) { showSpeedSheet = false }
    }
    AnimatedVisibility(
        visibleState = playlistSheetState,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        PlaylistBottomSheet(
            videoList, currentIndex, sheetBg, sheetText,
            repeatMode, shuffleEnabled, textColor, accentColor,
            { idx -> viewModel.playVideoAtIndex(idx); showPlaylistSheet = false },
            { viewModel.cycleRepeatMode() },
            { viewModel.setShuffleEnabled(!shuffleEnabled) }
        ) { showPlaylistSheet = false }
    }
}

@Composable
private fun AudioControlButton(
    icon: Int,
    label: String? = null,
    iconTint: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painterResource(icon),
                null,
                tint = iconTint.copy(alpha = if (enabled) 1f else 0.3f),
                modifier = Modifier.size(26.dp)
            )
        }
        if (label != null && enabled) {
            Text(
                label,
                color = iconTint.copy(alpha = 0.75f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun SpeedBottomSheet(
    currentSpeed: Float,
    bg: Color,
    text: Color,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    BottomSheet("播放速度", bg, text, onDismiss) {
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f).forEach { s ->
            val cur = s == currentSpeed
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(s) }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${s}x",
                    color = if (cur) Color(0xFF64B5F6) else text.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal
                )
                if (cur) Icon(
                    painterResource(R.drawable.ic_player_pause1),
                    null,
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(20.dp)
                )
            }
            if (s != 3.0f) HorizontalDivider(color = Color(0xFFE0E0E0))
        }
    }
}

@Composable
private fun PlaylistBottomSheet(
    videoList: List<com.fam4k007.videoplayer.VideoFileParcelable>,
    currentIndex: Int,
    bg: Color,
    text: Color,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    controlTextColor: Color,
    accentColor: Color,
    onSelect: (Int) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onDismiss: () -> Unit
) {
    val repeatLabel = when (repeatMode) {
        RepeatMode.OFF -> "列表循环"
        RepeatMode.ONE -> "单曲循环"
        RepeatMode.ALL -> "列表循环"
    }
    val repeatIcon = when (repeatMode) {
        RepeatMode.ONE -> R.drawable.ic_repeat_one_48_regular
        else -> R.drawable.ic_repeat_all_48_regular
    }
    val repeatActive = repeatMode != RepeatMode.OFF
    val dividerColor = Color(0xFFE0E0E0)
    val cardBorderColor = Color(0xFFD9D9DE)
    val cardBgColor = Color.White.copy(alpha = 0.85f)
    val footerInactiveColor = Color(0xFF1A1A1A)

    BottomSheet("播放列表", bg, text, onDismiss,
        footer = {
            HorizontalDivider(color = dividerColor)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 随机播放
                Surface(
                    onClick = { onToggleShuffle() },
                    modifier = Modifier.widthIn(min = 84.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_shuffle_48_regular),
                            contentDescription = "随机播放",
                            tint = if (shuffleEnabled) accentColor else footerInactiveColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "随机播放",
                            color = if (shuffleEnabled) accentColor else footerInactiveColor,
                            fontSize = 11.sp
                        )
                    }
                }
                // 循环模式
                Surface(
                    onClick = { onCycleRepeatMode() },
                    modifier = Modifier.widthIn(min = 84.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painterResource(repeatIcon),
                            contentDescription = repeatLabel,
                            tint = if (repeatActive) accentColor else footerInactiveColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            repeatLabel,
                            color = if (repeatActive) accentColor else footerInactiveColor,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    ) {
        videoList.forEachIndexed { i, v ->
            val cur = i == currentIndex
            val name = v.name.ifBlank { "视频 ${i + 1}" }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 当前播放项：等化器动画
                if (cur) {
                    EqualizerBars(
                        modifier = Modifier.padding(end = 8.dp),
                        color = accentColor
                    )
                }

                // 标题
                Text(
                    name,
                    color = if (cur) accentColor else text.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (i != videoList.lastIndex) HorizontalDivider(color = dividerColor)
        }
    }
}

/**
 * 等化器跳动动画：三根竖条随机变长变短
 */
@Composable
private fun EqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF64B5F6)
) {
    val infiniteTransition = rememberInfiniteTransition()
    val barCount = 3
    val barAnimations = List(barCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 400 + index * 180,
                    easing = LinearEasing
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            )
        )
    }

    Row(
        modifier = modifier.size(width = 14.dp, height = 14.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        barAnimations.forEach { barHeight ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(barHeight.value)
                    .background(color, RoundedCornerShape(1.5.dp))
            )
        }
    }
}


@Composable
private fun BottomSheet(
    title: String,
    bg: Color,
    text: Color,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        Surface(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = bg
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                Text(
                    title,
                    color = text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp
                    )
                )
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    content()
                }
                footer()
                HorizontalDivider(color = Color(0xFFE0E0E0))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("关闭", color = text.copy(alpha = 0.65f), fontSize = 14.sp)
                }
            }
        }
    }
}

internal fun formatTimeAudio(seconds: Long): String {
    val t = seconds.coerceAtLeast(0); val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
