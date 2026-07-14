package com.fam4k007.videoplayer.manager.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 音频均衡器数据
 */
data class EqualizerState(
    val enabled: Boolean = false,
    val bands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f), // 5 bands, -15 to +15 dB
    val bassBoost: Int = 0,      // 0-100
    val virtualizer: Int = 0     // 0-100
)

/**
 * 音频均衡器右侧抽屉
 * 与章节抽屉和片头片尾抽屉保持相同的动画、样式、UI 风格
 */
@Composable
fun EqualizerDrawer(
    state: EqualizerState,
    onEnabledChange: (Boolean) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onVirtualizerChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val skipAnim = ComposeOverlayManager.globalDisableAnimations

    // 内部状态管理（解决 ComposeOverlayManager.setContent 仅捕获初始值的问题）
    var eqEnabled by remember { mutableStateOf(state.enabled) }
    var eqBands by remember { mutableStateOf(state.bands.toMutableList().toList()) }
    var eqBassBoost by remember { mutableStateOf(state.bassBoost) }
    var eqVirtualizer by remember { mutableStateOf(state.virtualizer) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

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
                interactionSource = remember { MutableInteractionSource() },
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
            enter = if (skipAnim) EnterTransition.None
            else slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(300)),
            exit = if (skipAnim) ExitTransition.None
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
                // 半透明背景层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xCC121212),
                                    Color(0xE6121212)
                                )
                            )
                        )
                )

                // 内容层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* 阻止点击穿透 */ }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // 标题栏（固定不滚动）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Audio Equalizer",
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

                        Divider(
                            color = Color(0x33FFFFFF),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // 以下内容可滚动（含开关）
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                        // 均衡器开关
                        EqualizerSwitch(
                            enabled = eqEnabled,
                            onEnabledChange = { newValue ->
                                eqEnabled = newValue
                                onEnabledChange(newValue)
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        AnimatedVisibility(
                            visible = eqEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                EqualizerBands(
                                    bands = eqBands,
                                    enabled = eqEnabled,
                                    onBandChange = { index, value ->
                                        eqBands = eqBands.toMutableList().also { it[index] = value }
                                        onBandChange(index, value)
                                    }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Divider(
                                    color = Color(0x33FFFFFF),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                // 低音增强
                                BassBoostSection(
                                    value = eqBassBoost,
                                    enabled = eqEnabled,
                                    onValueChange = { value ->
                                        eqBassBoost = value
                                        onBassBoostChange(value)
                                    }
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                // 虚拟器
                                VirtualizerSection(
                                    value = eqVirtualizer,
                                    enabled = eqEnabled,
                                    onValueChange = { value ->
                                        eqVirtualizer = value
                                        onVirtualizerChange(value)
                                    }
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Divider(
                                    color = Color(0x33FFFFFF),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                // 一键重置按钮
                                TextButton(
                                    onClick = {
                                        // 重置所有内部状态
                                        eqBands = listOf(0f, 0f, 0f, 0f, 0f)
                                        eqBassBoost = 0
                                        eqVirtualizer = 0
                                        // 回调通知外部
                                        for (i in 0..4) { onBandChange(i, 0f) }
                                        onBassBoostChange(0)
                                        onVirtualizerChange(0)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color(0xFF64B5F6)
                                    )
                                ) {
                                    Text(
                                        text = "Reset All",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        } // 可滚动 Column
                    }
                }
            }
        }
    }
}

/**
 * 均衡器开关
 */
@Composable
private fun EqualizerSwitch(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    var isChecked by remember { mutableStateOf(enabled) }

    LaunchedEffect(enabled) {
        isChecked = enabled
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Enable Equalizer",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Adjust bands, bass boost, and virtualizer",
                fontSize = 12.sp,
                color = Color(0x88FFFFFF),
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = isChecked,
            onCheckedChange = { newValue ->
                isChecked = newValue
                onEnabledChange(newValue)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF64B5F6),
                checkedTrackColor = Color(0x8864B5F6),
                uncheckedThumbColor = Color(0xFF9E9E9E),
                uncheckedTrackColor = Color(0x88757575)
            )
        )
    }
}

/**
 * 5 频段均衡器（竖向滑块从左到右排列）
 */
@Composable
private fun EqualizerBands(
    bands: List<Float>,
    enabled: Boolean,
    onBandChange: (Int, Float) -> Unit
) {
    val bandLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")

    Column {
        Text(
            text = "Band Adjustment",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bands.forEachIndexed { index, value ->
                VerticalEqSlider(
                    value = value,
                    enabled = enabled,
                    label = bandLabels[index],
                    onValueChange = { onBandChange(index, it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 单个竖向均衡器滑块
 */
@Composable
private fun VerticalEqSlider(
    value: Float,
    enabled: Boolean,
    label: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        sliderValue = value
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 当前 dB 值显示
        Text(
            text = "${sliderValue.roundToInt()}dB",
            fontSize = 11.sp,
            color = if (enabled) Color(0xFF64B5F6) else Color(0x55FFFFFF),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 竖向滑块（使用旋转的横向 Slider）
        Box(
            modifier = Modifier
                .weight(1f)
                .width(40.dp),
            contentAlignment = Alignment.Center
        ) {
            // 旋转 -90 度使滑块变为竖向（上方为正，下方为负）
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                },
                onValueChangeFinished = {
                    // 将值四舍五入到整数
                    val rounded = sliderValue.roundToInt().toFloat()
                    sliderValue = rounded
                    onValueChange(rounded)
                },
                valueRange = -15f..15f,
                steps = 29, // 30 个区间（-15 到 +15 每 1dB 一个刻度）
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = if (enabled) Color(0xFF64B5F6) else Color(0x55FFFFFF),
                    activeTrackColor = if (enabled) Color(0xFF64B5F6) else Color(0x55FFFFFF),
                    inactiveTrackColor = Color(0xFF555555),
                    disabledThumbColor = Color(0x55FFFFFF),
                    disabledActiveTrackColor = Color(0x33FFFFFF),
                    disabledInactiveTrackColor = Color(0x22FFFFFF)
                ),
                modifier = Modifier
                    .requiredWidth(160.dp)
                    .rotate(-90f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 频段标签
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xAAFFFFFF),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 低音增强设置
 */
@Composable
private fun BassBoostSection(
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    var sliderValue by remember { mutableStateOf(value.toFloat()) }

    LaunchedEffect(value) {
        sliderValue = value.toFloat()
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bass Boost",
                fontSize = 16.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )

            Box(
                modifier = Modifier
                    .background(Color(0xFF1A2332), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${sliderValue.roundToInt()}%",
                    fontSize = 15.sp,
                    color = Color(0xFF64B5F6),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
            },
            onValueChangeFinished = {
                val rounded = sliderValue.roundToInt()
                sliderValue = rounded.toFloat()
                onValueChange(rounded)
            },
            valueRange = 0f..100f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) Color(0xFF64B5F6) else Color(0x55FFFFFF),
                activeTrackColor = if (enabled) Color(0xFF64B5F6) else Color(0x55FFFFFF),
                inactiveTrackColor = Color(0xFF555555)
            )
        )
    }
}

/**
 * 虚拟环绕设置
 */
@Composable
private fun VirtualizerSection(
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    var sliderValue by remember { mutableStateOf(value.toFloat()) }

    LaunchedEffect(value) {
        sliderValue = value.toFloat()
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Virtualizer",
                fontSize = 16.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )

            Box(
                modifier = Modifier
                    .background(Color(0xFF1A2332), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${sliderValue.roundToInt()}%",
                    fontSize = 15.sp,
                    color = Color(0xFF64B5F6),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
            },
            onValueChangeFinished = {
                val rounded = sliderValue.roundToInt()
                sliderValue = rounded.toFloat()
                onValueChange(rounded)
            },
            valueRange = 0f..100f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) Color(0xFF64B5F6) else Color(0x55FFFFFF),
                activeTrackColor = if (enabled) Color(0xFF64B5F6) else Color(0x55FFFFFF),
                inactiveTrackColor = Color(0xFF555555)
            )
        )
    }
}
