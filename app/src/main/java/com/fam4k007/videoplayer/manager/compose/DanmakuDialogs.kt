package com.fam4k007.videoplayer.manager.compose

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
/**
 * 右侧抽屉式弹幕设置面板（完全参考字幕设置的样式）
 */
@Composable
fun DanmakuSettingsDrawer(
    hasDanmakuLoaded: Boolean,
    currentSize: Int,
    currentSpeed: Int,
    currentAlpha: Int,
    currentStroke: Int,
    currentShowScroll: Boolean,
    currentShowTop: Boolean,
    currentShowBottom: Boolean,
    currentDisplayArea: Int,
    currentMaxScreenNum: Int,
    currentRandomColor: Boolean,
    currentOffsetTime: Long,
    onSizeChange: (Int) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onAlphaChange: (Int) -> Unit,
    onStrokeChange: (Int) -> Unit,
    onShowScrollChange: (Boolean) -> Unit,
    onShowTopChange: (Boolean) -> Unit,
    onShowBottomChange: (Boolean) -> Unit,
    onDisplayAreaChange: (Int) -> Unit,
    onMaxScreenNumChange: (Int) -> Unit,
    onRandomColorChange: (Boolean) -> Unit,
    onOffsetTimeChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var expandedSection by remember { mutableStateOf<String?>(null) }
    var isVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val skipAnim = com.fam4k007.videoplayer.manager.compose.ComposeOverlayManager.globalDisableAnimations

    // 启动时触发动画
    LaunchedEffect(Unit) {
        isVisible = true
    }

    // 处理返回键
    BackHandler(enabled = isVisible) {
        isVisible = false
        coroutineScope.launch {
            delay(300)
            onDismiss()
        }
    }

    // 点击背景关闭
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { 
                isVisible = false
                coroutineScope.launch {
                    delay(300)
                    onDismiss() 
                }
            }
    ) {
        // 右侧抽屉
        AnimatedVisibility(
            visible = isVisible,
            enter = if (skipAnim) androidx.compose.animation.EnterTransition.None
                    else slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(300)),
            exit = if (skipAnim) androidx.compose.animation.ExitTransition.None
                   else slideOutHorizontally(
                       targetOffsetX = { it },
                       animationSpec = tween(250, easing = FastOutSlowInEasing)
                   ) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
            ) {
                // 半透明背景层（高对比度，亮画面也能看清）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xCC121212), // 左边缘 80% 不透明（更不透明）
                                    Color(0xE6121212)  // 右边缘 90% 不透明
                                )
                            )
                        )
                )
                
                // 内容层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { /* 阻止点击穿透 */ }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // 标题栏（冻结）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Danmaku Settings",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            // 关闭按钮
                            IconButton(
                                onClick = {
                                    isVisible = false
                                    coroutineScope.launch {
                                        delay(300)
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text(
                                    text = "✕",
                                    fontSize = 20.sp,
                                    color = Color(0xFFBBBBBB)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = Color(0x33FFFFFF), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (!hasDanmakuLoaded) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "No danmaku file loaded",
                                fontSize = 14.sp,
                                color = Color(0xFFFF9800),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Please load a danmaku file before configuring",
                                fontSize = 12.sp,
                                color = Color(0x99FFFFFF),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            // 可滚动内容区域
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                        // 弹幕样式设置
                        item {
                            ExpandableSection(
                                title = "Danmaku Style",
                                isExpanded = expandedSection == "style",
                                onToggle = { expandedSection = if (expandedSection == "style") null else "style" }
                            ) {
                                DanmakuStyleContent(
                                    currentSize = currentSize,
                                    currentSpeed = currentSpeed,
                                    currentAlpha = currentAlpha,
                                    currentStroke = currentStroke,
                                    currentRandomColor = currentRandomColor,
                                    onSizeChange = onSizeChange,
                                    onSpeedChange = onSpeedChange,
                                    onAlphaChange = onAlphaChange,
                                    onStrokeChange = onStrokeChange,
                                    onRandomColorChange = onRandomColorChange
                                )
                            }
                        }

                        // 弹幕配置设置
                        item {
                            ExpandableSection(
                                title = "Danmaku Configuration",
                                isExpanded = expandedSection == "config",
                                onToggle = { expandedSection = if (expandedSection == "config") null else "config" }
                            ) {
                                DanmakuConfigContent(
                                    currentShowScroll = currentShowScroll,
                                    currentShowTop = currentShowTop,
                                    currentShowBottom = currentShowBottom,
                                    currentDisplayArea = currentDisplayArea,
                                    currentMaxScreenNum = currentMaxScreenNum,
                                    currentOffsetTime = currentOffsetTime,
                                    onShowScrollChange = onShowScrollChange,
                                    onShowTopChange = onShowTopChange,
                                    onShowBottomChange = onShowBottomChange,
                                    onDisplayAreaChange = onDisplayAreaChange,
                                    onMaxScreenNumChange = onMaxScreenNumChange,
                                    onOffsetTimeChange = onOffsetTimeChange
                                )
                            }
                        }
                    }
                    } // else 块结束
                }
                }
            }
        }
    }
}

/**
 * 弹幕样式设置内容
 */
@Composable
fun DanmakuStyleContent(
    currentSize: Int,
    currentSpeed: Int,
    currentAlpha: Int,
    currentStroke: Int,
    currentRandomColor: Boolean,
    onSizeChange: (Int) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onAlphaChange: (Int) -> Unit,
    onStrokeChange: (Int) -> Unit,
    onRandomColorChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var size by remember { mutableStateOf(com.fam4k007.videoplayer.danmaku.DanmakuConfig.size.toFloat()) }
    var speed by remember { mutableStateOf(com.fam4k007.videoplayer.danmaku.DanmakuConfig.speed.toFloat()) }
    var alpha by remember { mutableStateOf(com.fam4k007.videoplayer.danmaku.DanmakuConfig.alpha.toFloat()) }
    var stroke by remember { mutableStateOf(com.fam4k007.videoplayer.danmaku.DanmakuConfig.stroke.toFloat()) }
    var randomColor by remember { mutableStateOf(currentRandomColor) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 弹幕大小
        Text(
            text = "Danmaku Size: ${size.toInt()}%",
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
        
        Slider(
            value = size,
            onValueChange = {
                size = it
                onSizeChange(it.toInt())
            },
            valueRange = 0f..100f,
            steps = 99,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6),
                inactiveTrackColor = Color(0xFF555555)
            )
        )

        // 弹幕速度
        Text(
            text = "Danmaku Speed: ${speed.toInt()}%",
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = "Higher value = faster movement",
            fontSize = 11.sp,
            color = Color(0x99FFFFFF),
            modifier = Modifier.padding(top = 2.dp)
        )
        
        Slider(
            value = speed,
            onValueChange = {
                speed = it
                onSpeedChange(it.toInt())
            },
            valueRange = 0f..100f,
            steps = 99,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6),
                inactiveTrackColor = Color(0xFF555555)
            )
        )

        // 弹幕透明度
        Text(
            text = "Danmaku Opacity: ${alpha.toInt()}%",
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
        
        Slider(
            value = alpha,
            onValueChange = {
                alpha = it
                onAlphaChange(it.toInt())
            },
            valueRange = 0f..100f,
            steps = 99,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6),
                inactiveTrackColor = Color(0xFF555555)
            )
        )

        // 描边粗细
        Text(
            text = "Stroke Width: ${stroke.toInt()}%",
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
        
        Slider(
            value = stroke,
            onValueChange = {
                stroke = it
                onStrokeChange(it.toInt())
            },
            valueRange = 0f..100f,
            steps = 99,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6),
                inactiveTrackColor = Color(0xFF555555)
            )
        )

        // 弹幕颜色随机渐变开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Use random gradient colors for danmaku",
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "When enabled, ignores file colors and uses random gradients",
                    fontSize = 11.sp,
                    color = Color(0x99FFFFFF),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(
                checked = randomColor,
                onCheckedChange = {
                    randomColor = it
                    onRandomColorChange(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF64B5F6),
                    checkedTrackColor = Color(0xFF448AFF).copy(alpha = 0.5f),
                    uncheckedThumbColor = Color(0xFF888888),
                    uncheckedTrackColor = Color(0xFF555555)
                )
            )
        }

        // 重置按钮
        TextButton(
            onClick = {
                size = 50f
                speed = 50f
                alpha = 100f
                stroke = 50f
                onSizeChange(50)
                onSpeedChange(50)
                onAlphaChange(100)
                onStrokeChange(50)
                Toast.makeText(context, "Reset to defaults", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color(0xFFFF6666)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset All Styles to Default")
        }
    }
}

/**
 * 弹幕配置设置内容
 */
@Composable
fun DanmakuConfigContent(
    currentShowScroll: Boolean,
    currentShowTop: Boolean,
    currentShowBottom: Boolean,
    currentDisplayArea: Int,
    currentMaxScreenNum: Int,
    currentOffsetTime: Long,
    onShowScrollChange: (Boolean) -> Unit,
    onShowTopChange: (Boolean) -> Unit,
    onShowBottomChange: (Boolean) -> Unit,
    onDisplayAreaChange: (Int) -> Unit,
    onMaxScreenNumChange: (Int) -> Unit,
    onOffsetTimeChange: (Long) -> Unit
) {
    var showScroll by remember { mutableStateOf(com.fam4k007.videoplayer.danmaku.DanmakuConfig.showScrollDanmaku) }
    var showTop by remember { mutableStateOf(com.fam4k007.videoplayer.danmaku.DanmakuConfig.showTopDanmaku) }
    var showBottom by remember { mutableStateOf(com.fam4k007.videoplayer.danmaku.DanmakuConfig.showBottomDanmaku) }
    var displayArea by remember { mutableIntStateOf(com.fam4k007.videoplayer.danmaku.DanmakuConfig.displayAreaPercent) }
    var maxScreenNum by remember { mutableStateOf(com.fam4k007.videoplayer.danmaku.DanmakuConfig.maxScreenNum.toFloat()) }

    // 弹幕时间轴偏移（秒）
    var offsetSeconds by remember { mutableIntStateOf((currentOffsetTime / 1000L).toInt()) }
    var showOffsetInput by remember { mutableStateOf(false) }

    val applyOffsetDelta: (Int) -> Unit = { delta ->
        offsetSeconds = (offsetSeconds + delta).coerceIn(-600, 600)
        onOffsetTimeChange(offsetSeconds * 1000L)
    }

    val areaOptions = listOf(10, 25, 50, 75, 100)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 弹幕类型开关
        Text(
            text = "Danmaku Type Display",
            fontSize = 16.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        DanmakuSwitchItem(
            title = "Show Scrolling Danmaku",
            checked = showScroll,
            onCheckedChange = { 
                showScroll = it
                onShowScrollChange(it)
            }
        )

        DanmakuSwitchItem(
            title = "Show Top Danmaku",
            checked = showTop,
            onCheckedChange = { 
                showTop = it
                onShowTopChange(it)
            }
        )

        DanmakuSwitchItem(
            title = "Show Bottom Danmaku",
            checked = showBottom,
            onCheckedChange = { 
                showBottom = it
                onShowBottomChange(it)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 弹幕显示区域
        Text(
            text = "Danmaku Display Area",
            fontSize = 16.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            areaOptions.forEach { percent ->
                val isSelected = displayArea == percent
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected) Color(0xFF64B5F6) else Color(0x1AFFFFFF),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            displayArea = percent
                            onDisplayAreaChange(percent)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$percent%",
                        fontSize = 13.sp,
                        color = if (isSelected) Color(0xFF121212) else Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Text(
            text = when (displayArea) {
                10 -> "Show 1 line only"
                25 -> "Show a few danmaku"
                50 -> "Show moderate danmaku"
                75 -> "Show more danmaku"
                100 -> "Full-screen (Recommended)"
                else -> ""
            },
            fontSize = 11.sp,
            color = Color(0x99FFFFFF),
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Max on-screen: ${if (maxScreenNum.toInt() == 0) "Unlimited" else maxScreenNum.toInt().toString()}",
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )

        Slider(
            value = maxScreenNum,
            onValueChange = {
                maxScreenNum = it
                onMaxScreenNumChange(it.toInt())
            },
            valueRange = 0f..200f,
            steps = 199,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6),
                inactiveTrackColor = Color(0xFF555555)
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ==================== 弹幕时间轴偏移 ====================
        Text(
            text = "Danmaku Timeline Offset",
            fontSize = 16.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Shift all danmaku timing; positive delays, negative advances",
            fontSize = 11.sp,
            color = Color(0x99FFFFFF),
            modifier = Modifier.padding(top = 2.dp)
        )

        // 当前值显示 + 重置
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDanmakuOffset(offsetSeconds),
                fontSize = 15.sp,
                color = if (offsetSeconds == 0) Color(0x99FFFFFF) else Color(0xFF64B5F6),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showOffsetInput = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            TextButton(
                onClick = {
                    offsetSeconds = 0
                    onOffsetTimeChange(0L)
                },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6666))
            ) {
                Text("Reset")
            }
        }

        // 滑轨（粗调）
        Slider(
            value = offsetSeconds.toFloat(),
            onValueChange = {
                offsetSeconds = it.toInt().coerceIn(-600, 600)
                onOffsetTimeChange(offsetSeconds * 1000L)
            },
            valueRange = -600f..600f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6),
                inactiveTrackColor = Color(0xFF555555)
            )
        )

        // 步进按钮（细调/快调）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OffsetStepButton("-10s") { applyOffsetDelta(-10) }
            OffsetStepButton("-5s") { applyOffsetDelta(-5) }
            OffsetStepButton("-1s") { applyOffsetDelta(-1) }
            OffsetStepButton("+1s") { applyOffsetDelta(1) }
            OffsetStepButton("+5s") { applyOffsetDelta(5) }
            OffsetStepButton("+10s") { applyOffsetDelta(10) }
        }

        Text(
            text = "Tap the value above to enter seconds manually (-600 ~ 600)",
            fontSize = 11.sp,
            color = Color(0x99FFFFFF),
            modifier = Modifier.padding(top = 2.dp)
        )
    }

    // 手动输入对话框
    if (showOffsetInput) {
        var inputText by remember { mutableStateOf(offsetSeconds.toString()) }
        AlertDialog(
            onDismissRequest = { showOffsetInput = false },
            title = {
                Text("Danmaku Timeline Offset", color = Color.White)
            },
            text = {
                Column {
                    Text(
                        text = "Enter offset seconds (negative allowed, -600 ~ 600)",
                        fontSize = 12.sp,
                        color = Color(0x99FFFFFF)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        label = { Text("Seconds") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF64B5F6),
                            unfocusedBorderColor = Color(0xFF555555),
                            cursorColor = Color(0xFF64B5F6)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = inputText.trim().toIntOrNull()
                    if (parsed != null) {
                        offsetSeconds = parsed.coerceIn(-600, 600)
                        onOffsetTimeChange(offsetSeconds * 1000L)
                    }
                    showOffsetInput = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showOffsetInput = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(28.dp)
        )
    }
}

/**
 * 弹幕开关项
 */
@Composable
fun DanmakuSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0x1AFFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = Color.White
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF64B5F6),
                uncheckedThumbColor = Color(0xFF999999),
                uncheckedTrackColor = Color(0xFF333333)
            )
        )
    }
}

/**
 * 偏移步进按钮
 */
@Composable
private fun RowScope.OffsetStepButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 格式化弹幕偏移秒数（如 +1min23s）
 */
private fun formatDanmakuOffset(seconds: Int): String {
    if (seconds == 0) return "Offset: 0s"
    val sign = if (seconds > 0) "+" else "-"
    val abs = kotlin.math.abs(seconds)
    val m = abs / 60
    val s = abs % 60
    return if (m > 0) "Offset: $sign${m}min${s}s" else "Offset: $sign${s}s"
}
