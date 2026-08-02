package com.fam4k007.videoplayer.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 自定义进度条组件，支持三种样式：Standard、Wavy、Thick
 *
 * @param progress 当前播放进度（毫秒）
 * @param duration 视频总时长（毫秒）
 * @param seekbarStyle 进度条样式
 * @param accentColor 进度条主题色（取自当前主题活动色）
 * @param onSeek 用户拖动/点击进度条时的回调
 * @param onSeekFinished 用户完成拖动时的回调
 * @param paused 是否暂停
 * @param isDragging 是否正在拖动中（由外部控制）
 * @param chapters 章节时间点列表（秒），用于在进度条上标记小圆点
 * @param onChapterClick 点击章节标记时的回调，参数为章节索引
 * @param skipSegments OP/ED 跳过片段列表，用于进度条着色
 * @param modifier Modifier
 */
@Composable
fun CustomSeekbar(
    progress: Float,
    duration: Float,
    seekbarStyle: SeekbarStyle,
    accentColor: Color,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    paused: Boolean = false,
    isDragging: Boolean = false,
    chapters: List<Float> = emptyList(),
    onChapterClick: ((Int) -> Unit)? = null,
    skipSegments: List<com.fam4k007.videoplayer.player.SkipSegment> = emptyList(),
    modifier: Modifier = Modifier,
) {
    when (seekbarStyle) {
        SeekbarStyle.Standard -> StandardOrThickSeekbar(
            progress = progress, duration = duration, isThick = false,
            primaryColor = accentColor, paused = paused, isScrubbing = isDragging,
            onSeek = onSeek, onSeekFinished = onSeekFinished,
            chapters = chapters, onChapterClick = onChapterClick,
            skipSegments = skipSegments, modifier = modifier,
        )
        SeekbarStyle.Thick -> StandardOrThickSeekbar(
            progress = progress, duration = duration, isThick = true,
            primaryColor = accentColor, paused = paused, isScrubbing = isDragging,
            onSeek = onSeek, onSeekFinished = onSeekFinished,
            chapters = chapters, onChapterClick = onChapterClick,
            skipSegments = skipSegments, modifier = modifier,
        )
        SeekbarStyle.Wavy -> WavySeekbar(
            progress = progress, duration = duration,
            primaryColor = accentColor, paused = paused, isScrubbing = isDragging,
            onSeek = onSeek, onSeekFinished = onSeekFinished,
            chapters = chapters, onChapterClick = onChapterClick,
            skipSegments = skipSegments, modifier = modifier,
        )
        SeekbarStyle.Slim -> SlimSeekbar(
            progress = progress, duration = duration,
            primaryColor = accentColor, paused = paused, isScrubbing = isDragging,
            onSeek = onSeek, onSeekFinished = onSeekFinished,
            chapters = chapters, modifier = modifier,
        )
    }
}

/**
 * Slim 纤细进度条（移植自 mpvRx）
 * 纤细分段式轨道：暂停时更薄、拖动时加粗，章节位置留出间隙
 */
@Composable
private fun SlimSeekbar(
    progress: Float,
    duration: Float,
    primaryColor: Color,
    paused: Boolean,
    isScrubbing: Boolean,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    chapters: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val trackHeight by animateDpAsState(
        targetValue = when {
            isScrubbing -> 15.dp
            paused -> 6.dp
            else -> 8.dp
        },
        animationSpec = if (isScrubbing) spring(stiffness = 500f, dampingRatio = 0.75f)
        else spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "slim_seekbar_height",
    )
    val chapterGapHalfDp by animateDpAsState(
        targetValue = if (isScrubbing) 2.dp else 1.5.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "slim_chapter_gap",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newPosition = (offset.x / size.width) * duration
                    onSeek(newPosition.coerceIn(0f, duration))
                    onSeekFinished()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = { onSeekFinished() },
                    onDragCancel = { onSeekFinished() },
                ) { change, _ ->
                    change.consume()
                    val newPosition = (change.position.x / size.width) * duration
                    onSeek(newPosition.coerceIn(0f, duration))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight),
        ) {
            val playedFraction = if (duration > 0f) (progress / duration).coerceIn(0f, 1f) else 0f
            val totalWidth = size.width
            val playedPx = totalWidth * playedFraction
            val centerY = size.height / 2f
            val height = size.height
            val outerRadius = height / 2f
            val innerRadius = 2.dp.toPx()
            val gapHalf = chapterGapHalfDp.toPx()

            // 章节位置留出间隙，把轨道拆分为独立分段
            val chapterXs = if (duration > 0f) {
                chapters
                    .map { (it / duration).coerceIn(0f, 1f) * totalWidth }
                    .filter { it > gapHalf && it < totalWidth - gapHalf }
                    .sorted()
            } else emptyList()

            val segments = mutableListOf<Pair<Float, Float>>()
            var segCursor = 0f
            for (chX in chapterXs) {
                val gS = chX - gapHalf
                val gE = chX + gapHalf
                if (gS > segCursor) segments.add(segCursor to gS)
                segCursor = gE
            }
            if (segCursor < totalWidth) segments.add(segCursor to totalWidth)

            fun seg(startX: Float, endX: Float, color: Color, leftR: Float, rightR: Float) {
                if (endX - startX < 0.5f) return
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = startX, top = centerY - outerRadius,
                            right = endX, bottom = centerY + outerRadius,
                            topLeftCornerRadius = CornerRadius(leftR),
                            bottomLeftCornerRadius = CornerRadius(leftR),
                            topRightCornerRadius = CornerRadius(rightR),
                            bottomRightCornerRadius = CornerRadius(rightR),
                        )
                    )
                }
                drawPath(path, color)
            }

            val playedColor = primaryColor
            val unplayedColor = primaryColor.copy(alpha = 0.3f)

            for ((sS, sE) in segments) {
                val lR = if (sS <= 0.5f) outerRadius else innerRadius
                val rR = if (sE >= totalWidth - 0.5f) outerRadius else innerRadius
                when {
                    sE <= playedPx -> seg(sS, sE, playedColor, lR, rR)
                    sS >= playedPx -> seg(sS, sE, unplayedColor, lR, rR)
                    else -> {
                        seg(sS, playedPx, playedColor, lR, 0f)
                        seg(playedPx, sE, unplayedColor, 0f, rR)
                    }
                }
            }
        }
    }
}

/**
 * Standard / Thick 进度条
 * Standard: 8dp 轨道高, 圆形滑块
 * Thick: 16dp 轨道高, 圆角矩形滑块
 *
 * 轨道和滑块（thumb）均在 Canvas 内统一绘制，确保滑块位置跟随进度
 */
@Composable
private fun StandardOrThickSeekbar(
    progress: Float,
    duration: Float,
    isThick: Boolean,
    primaryColor: Color,
    paused: Boolean,
    isScrubbing: Boolean,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    chapters: List<Float> = emptyList(),
    onChapterClick: ((Int) -> Unit)? = null,
    skipSegments: List<com.fam4k007.videoplayer.player.SkipSegment> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var heightFraction by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()

    // 暂停/拖动时轨道高度动画
    LaunchedEffect(paused, isScrubbing) {
        scope.launch {
            val shouldFlatten = paused || isScrubbing
            val targetHeight = if (shouldFlatten) 0.7f else 1f
            val animationDuration = if (shouldFlatten) 550 else 800
            val startDelay = if (shouldFlatten) 0L else 60L

            delay(startDelay)
            val animator = Animatable(heightFraction)
            animator.animateTo(
                targetValue = targetHeight,
                animationSpec = tween(durationMillis = animationDuration, easing = LinearEasing),
            ) { heightFraction = value }
        }
    }

    val baseTrackHeight: Dp = if (isThick) 16.dp else 8.dp
    val trackHeightDp = baseTrackHeight * heightFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newPosition = (offset.x / size.width) * duration
                    onSeek(newPosition.coerceIn(0f, duration))
                    onSeekFinished()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = { onSeekFinished() },
                    onDragCancel = { onSeekFinished() },
                ) { change, _ ->
                    change.consume()
                    val newPosition = (change.position.x / size.width) * duration
                    onSeek(newPosition.coerceIn(0f, duration))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeightDp),
        ) {
            val playedFraction = if (duration > 0f) (progress / duration).coerceIn(0f, 1f) else 0f
            val playedPx = size.width * playedFraction
            val trackH = size.height
            val outerRadius = trackH / 2f
            val innerRadius = if (isThick) outerRadius else 2.dp.toPx()

            // 已播放段（左端圆角，右端小圆角）
            if (playedPx > 0.5f) {
                val pathPlayed = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = 0f, top = 0f,
                            right = playedPx, bottom = trackH,
                            topLeftCornerRadius = CornerRadius(outerRadius),
                            bottomLeftCornerRadius = CornerRadius(outerRadius),
                            topRightCornerRadius = CornerRadius(innerRadius),
                            bottomRightCornerRadius = CornerRadius(innerRadius),
                        )
                    )
                }
                drawPath(pathPlayed, primaryColor)
            }

            // 未播放段（左端小圆角，右端大圆角）
            val unplayedStart = playedPx.coerceAtLeast(0f)
            if (unplayedStart < size.width) {
                val pathUnplayed = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = unplayedStart, top = 0f,
                            right = size.width, bottom = trackH,
                            topLeftCornerRadius = CornerRadius(innerRadius),
                            bottomLeftCornerRadius = CornerRadius(innerRadius),
                            topRightCornerRadius = CornerRadius(outerRadius),
                            bottomRightCornerRadius = CornerRadius(outerRadius),
                        )
                    )
                }
                drawPath(pathUnplayed, primaryColor.copy(alpha = 0.3f))
            }

            // 滑块（thumb）— 在 Canvas 内按进度位置绘制
            val thumbWidthPx = 6.dp.toPx()
            val thumbHeightPx = if (isThick) 32.dp.toPx() else 14.dp.toPx()
            val thumbX = playedPx
            val thumbY = trackH / 2f
            if (isThick) {
                // 圆角矩形
                val radius = thumbWidthPx / 2f
                val thumbShapePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = thumbX - thumbWidthPx / 2f,
                            top = thumbY - thumbHeightPx / 2f,
                            right = thumbX + thumbWidthPx / 2f,
                            bottom = thumbY + thumbHeightPx / 2f,
                            topLeftCornerRadius = CornerRadius(radius),
                            bottomLeftCornerRadius = CornerRadius(radius),
                            topRightCornerRadius = CornerRadius(radius),
                            bottomRightCornerRadius = CornerRadius(radius),
                        )
                    )
                }
                drawPath(thumbShapePath, primaryColor)
            } else {
                // 圆形
                drawCircle(
                    color = primaryColor,
                    radius = thumbHeightPx / 2f,
                    center = Offset(thumbX, thumbY),
                )
            }

            // 章节标记小圆点
            if (duration > 0f) {
                val dotRadius = 3.dp.toPx()
                chapters.forEach { chapterTime ->
                    val fraction = (chapterTime / duration).coerceIn(0f, 1f)
                    val dotX = fraction * size.width
                    // 在滑块上方绘制小圆点
                    val dotY = trackH / 2f
                    val isPlayed = fraction <= playedFraction
                    drawCircle(
                        color = if (isPlayed) Color.White else primaryColor.copy(alpha = 0.7f),
                        radius = dotRadius,
                        center = Offset(dotX, dotY),
                    )
                    // 小圆点描边
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.3f),
                        radius = dotRadius,
                        center = Offset(dotX, dotY),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // OP/ED 跳过片段着色（高度为轨道 55%，居中不突兀）
            if (skipSegments.isNotEmpty() && duration > 0f) {
                val segH = trackH * 0.55f; val segY = (trackH - segH) / 2f
                skipSegments.forEach { seg ->
                    if (!seg.isValid) return@forEach
                    val startFrac = (seg.startSeconds.toFloat() / duration).coerceIn(0f, 1f)
                    val endFrac = (seg.endSeconds.toFloat() / duration).coerceIn(0f, 1f)
                    val segW = (endFrac - startFrac) * size.width
                    if (segW <= 0f) return@forEach
                    val segColor = seg.type.accentColor
                    drawRect(
                        color = Color(red = segColor.red * 0.6f, green = segColor.green * 0.6f,
                            blue = segColor.blue * 0.6f, alpha = 0.45f),
                        topLeft = Offset(startFrac * size.width, segY),
                        size = androidx.compose.ui.geometry.Size(segW, segH),
                    )
                    val edgeX = startFrac * size.width
                    drawLine(segColor.copy(alpha = 0.8f), Offset(edgeX, segY), Offset(edgeX, segY + segH), strokeWidth = 2.dp.toPx())
                    val edgeX2 = endFrac * size.width
                    drawLine(segColor.copy(alpha = 0.8f), Offset(edgeX2, segY), Offset(edgeX2, segY + segH), strokeWidth = 2.dp.toPx())
                }
            }
        }
    }
}

/**
 * Wavy 波浪进度条
 * 使用 Canvas 绘制贝塞尔曲线波浪动画
 */
@Composable
private fun WavySeekbar(
    progress: Float,
    duration: Float,
    primaryColor: Color,
    paused: Boolean,
    isScrubbing: Boolean,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    chapters: List<Float> = emptyList(),
    onChapterClick: ((Int) -> Unit)? = null,
    skipSegments: List<com.fam4k007.videoplayer.player.SkipSegment> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var phaseOffset by remember { mutableFloatStateOf(0f) }
    var heightFraction by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()

    val waveLength = 80f
    val lineAmplitude = 6f
    val phaseSpeed = 10f

    // 暂停/拖动时波浪变平动画
    LaunchedEffect(paused, isScrubbing) {
        scope.launch {
            val shouldFlatten = paused || isScrubbing
            val targetHeight = if (shouldFlatten) 0f else 1f
            val animDuration = if (shouldFlatten) 550 else 800
            val startDelay = if (shouldFlatten) 0L else 60L
            delay(startDelay)
            val animator = Animatable(heightFraction)
            animator.animateTo(
                targetValue = targetHeight,
                animationSpec = tween(durationMillis = animDuration, easing = LinearEasing),
            ) { heightFraction = value }
        }
    }

    // 波浪移动动画
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var lastFrameTime = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { frameTimeMillis ->
                val deltaTime = (frameTimeMillis - lastFrameTime) / 1000f
                phaseOffset += deltaTime * phaseSpeed
                phaseOffset %= waveLength
                lastFrameTime = frameTimeMillis
            }
        }
    }

    // 触摸处理
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newPosition = (offset.x / size.width) * duration
                    onSeek(newPosition.coerceIn(0f, duration))
                    onSeekFinished()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = { onSeekFinished() },
                    onDragCancel = { onSeekFinished() },
                ) { change, _ ->
                    change.consume()
                    val newPosition = (change.position.x / size.width) * duration
                    onSeek(newPosition.coerceIn(0f, duration))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            val strokeWidth = 5.dp.toPx()
            val playedFraction = if (duration > 0f) (progress / duration).coerceIn(0f, 1f) else 0f
            val totalWidth = size.width
            val totalProgressPx = totalWidth * playedFraction
            val centerY = size.height / 2f

            fun computeAmplitude(x: Float, sign: Float): Float {
                val transitionLength = 1.5f * waveLength
                val waveEnd = totalProgressPx
                val coeff = ((waveEnd + transitionLength / 2f - x) / transitionLength).coerceIn(0f, 1f)
                return sign * heightFraction * lineAmplitude * coeff
            }

            // 构建波浪路径
            val path = Path()
            val waveStart = -phaseOffset - waveLength / 2f
            val waveEnd = totalWidth

            path.moveTo(waveStart, centerY)
            var currentX = waveStart
            var waveSign = 1f
            var currentAmp = computeAmplitude(currentX, waveSign)
            val dist = waveLength / 2f

            while (currentX < waveEnd) {
                waveSign = -waveSign
                val nextX = currentX + dist
                val midX = currentX + dist / 2f
                val nextAmp = computeAmplitude(nextX, waveSign)
                path.cubicTo(midX, centerY + currentAmp, midX, centerY + nextAmp, nextX, centerY + nextAmp)
                currentAmp = nextAmp
                currentX = nextX
            }

            // 绘制已播放部分
            val clipTop = lineAmplitude + strokeWidth
            clipRect(left = 0f, top = centerY - clipTop, right = totalProgressPx, bottom = centerY + clipTop) {
                drawPath(path = path, color = primaryColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }

            // 绘制未播放部分（半透明）
            clipRect(left = totalProgressPx, top = centerY - clipTop, right = totalWidth, bottom = centerY + clipTop) {
                drawPath(path = path, color = primaryColor.copy(alpha = 0.3f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }

            // 绘制竖线滑块
            val barHalfHeight = lineAmplitude + strokeWidth
            val barWidth = 5.dp.toPx()
            drawLine(
                color = primaryColor,
                start = Offset(totalProgressPx, centerY - barHalfHeight),
                end = Offset(totalProgressPx, centerY + barHalfHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )

            // 章节标记小圆点
            if (duration > 0f) {
                val dotRadius = 3.dp.toPx()
                chapters.forEach { chapterTime ->
                    val fraction = (chapterTime / duration).coerceIn(0f, 1f)
                    val dotX = fraction * size.width
                    val isPlayed = fraction <= playedFraction
                    drawCircle(
                        color = if (isPlayed) Color.White else primaryColor.copy(alpha = 0.7f),
                        radius = dotRadius,
                        center = Offset(dotX, centerY),
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.3f),
                        radius = dotRadius,
                        center = Offset(dotX, centerY),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // OP/ED 跳过片段着色（高度为轨道 55%，居中）
            if (skipSegments.isNotEmpty() && duration > 0f) {
                val trackH = size.height; val segH = trackH * 0.55f; val segY = (trackH - segH) / 2f
                skipSegments.forEach { seg ->
                    if (!seg.isValid) return@forEach
                    val startFrac = (seg.startSeconds.toFloat() / duration).coerceIn(0f, 1f)
                    val endFrac = (seg.endSeconds.toFloat() / duration).coerceIn(0f, 1f)
                    val segW = (endFrac - startFrac) * size.width
                    if (segW <= 0f) return@forEach
                    val segColor = seg.type.accentColor
                    drawRect(
                        color = Color(red = segColor.red * 0.6f, green = segColor.green * 0.6f,
                            blue = segColor.blue * 0.6f, alpha = 0.45f),
                        topLeft = Offset(startFrac * size.width, segY),
                        size = androidx.compose.ui.geometry.Size(segW, segH),
                    )
                    val edgeX = startFrac * size.width
                    drawLine(segColor.copy(alpha = 0.8f), Offset(edgeX, segY), Offset(edgeX, segY + segH), strokeWidth = 2.dp.toPx())
                    val edgeX2 = endFrac * size.width
                    drawLine(segColor.copy(alpha = 0.8f), Offset(edgeX2, segY), Offset(edgeX2, segY + segH), strokeWidth = 2.dp.toPx())
                }
            }
        }
    }
}
