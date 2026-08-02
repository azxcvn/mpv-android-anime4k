package com.fam4k007.videoplayer.manager.compose

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 默认倍速预设 */
private val DEFAULT_SPEED_PRESETS = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0)

/** 预设最大数量 */
private const val MAX_PRESETS = 20

/**
 * 播放速度调节右侧抽屉
 */
@Composable
fun SpeedDrawer(
    currentSpeed: Double,
    speedPresets: Set<String>,
    onSpeedChanged: (Double) -> Unit,
    onPresetsChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 内部状态
    var activeSpeed by remember { mutableStateOf(currentSpeed) }
    var sliderValue by remember { mutableStateOf(currentSpeed.toFloat()) }
    var presets by remember {
        mutableStateOf(speedPresets.mapNotNull { it.toDoubleOrNull() }.sorted())
    }
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    BackHandler { onDismiss() }

    // 全屏遮罩层
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        // 右侧面板
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(320.dp)
                .align(Alignment.CenterEnd)
        ) {
            // 背景
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xCC121212), Color(0xE6121212))
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
                    ) { /* 阻止穿透 */ }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // ===== 固定标题栏（不随内容滚动）=====
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Playback Speed",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(
                            onClick = { onDismiss() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("✕", fontSize = 20.sp, color = Color(0xFFBBBBBB))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color(0x33FFFFFF), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // ===== 固定：倍速选项标题 + 重置预设 =====
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speed Options",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        TextButton(
                            onClick = {
                                presets = DEFAULT_SPEED_PRESETS
                                onPresetsChanged(presets.map { formatSpeedRaw(it) }.toSet())
                                Toast.makeText(context, "Restored default speeds", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Reset Presets", color = Color(0xFF64B5F6), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ===== 预设列表（独立滚动，带容器背景）=====
                    val presetListState = rememberLazyListState()

                    LazyColumn(
                        state = presetListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(
                                color = Color(0x1AFFFFFF),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(presets) { speed ->
                            val isCurrent = Math.abs(speed - activeSpeed) < 0.001
                            val isOneX = Math.abs(speed - 1.0) < 0.001

                            SpeedPresetItem(
                                speed = speed,
                                isCurrent = isCurrent,
                                canDelete = !isOneX,
                                onClick = {
                                    activeSpeed = speed
                                    sliderValue = speed.toFloat()
                                    onSpeedChanged(speed)
                                    Toast.makeText(context, "Playback speed: ${formatSpeed(speed)}", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    presets = presets.filter { it != speed }
                                    onPresetsChanged(presets.map { formatSpeedRaw(it) }.toSet())
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ===== 精确调速区域（可折叠，固定在底部）=====
                    SpeedAdvancedSection(
                        isExpanded = isAdvancedExpanded,
                        onToggle = { isAdvancedExpanded = !isAdvancedExpanded }
                    ) {
                                // 当前速度显示 / 编辑区
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isEditing) {
                                        OutlinedTextField(
                                            value = editText,
                                            onValueChange = { input ->
                                                if (input.isEmpty() || input.matches(Regex("^\\d{0,2}\\.?\\d{0,2}$"))) {
                                                    editText = input
                                                    val parsed = input.toDoubleOrNull()
                                                    if (parsed != null && parsed in 0.25..4.0) {
                                                        sliderValue = parsed.toFloat()
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    val parsed = editText.toDoubleOrNull()
                                                    if (parsed != null && parsed in 0.25..4.0) {
                                                        sliderValue = roundToStep(parsed).toFloat()
                                                    }
                                                    isEditing = false
                                                    keyboardController?.hide()
                                                }
                                            ),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color(0xFF64B5F6),
                                                unfocusedTextColor = Color(0xFF64B5F6),
                                                focusedBorderColor = Color(0xFF64B5F6),
                                                unfocusedBorderColor = Color(0x55FFFFFF),
                                                cursorColor = Color(0xFF64B5F6)
                                            ),
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier
                                                .width(120.dp)
                                                .focusRequester(focusRequester)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // 确认按钮
                                        IconButton(
                                            onClick = {
                                                val parsed = editText.toDoubleOrNull()
                                                if (parsed != null && parsed in 0.25..4.0) {
                                                    sliderValue = roundToStep(parsed).toFloat()
                                                }
                                                isEditing = false
                                                keyboardController?.hide()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Confirm",
                                                tint = Color(0xFF81C784),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // 取消按钮
                                        IconButton(
                                            onClick = {
                                                isEditing = false
                                                keyboardController?.hide()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                tint = Color(0xFFE57373),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        LaunchedEffect(Unit) {
                                            focusRequester.requestFocus()
                                        }
                                    } else {
                                        Text(
                                            text = formatSpeed(sliderValue.toDouble()),
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64B5F6)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                editText = String.format("%.2f", roundToStep(sliderValue.toDouble()))
                                                    .trimEnd('0').trimEnd('.')
                                                isEditing = true
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = Color(0xAAFFFFFF),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 滑块
                                Slider(
                                    value = sliderValue,
                                    onValueChange = {
                                        sliderValue = it
                                        isEditing = false
                                    },
                                    valueRange = 0.25f..4.0f,
                                    steps = 74,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF64B5F6),
                                        activeTrackColor = Color(0xFF64B5F6),
                                        inactiveTrackColor = Color(0xFF555555)
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("0.25x", fontSize = 10.sp, color = Color(0x66FFFFFF))
                                    Text("4.0x", fontSize = 10.sp, color = Color(0x66FFFFFF))
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 操作按钮行：应用 + 添加 + 归位
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val sliderSpeed = roundToStep(sliderValue.toDouble())
                                    val alreadyExists = sliderSpeed in presets
                                    val atLimit = presets.size >= MAX_PRESETS

                                    Button(
                                        onClick = {
                                            val speed = roundToStep(sliderValue.toDouble())
                                            activeSpeed = speed
                                            onSpeedChanged(speed)
                                            Toast.makeText(context, "Speed: ${formatSpeed(speed)}", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Apply", fontSize = 13.sp)
                                    }

                                    Button(
                                        onClick = {
                                            if (!alreadyExists && !atLimit) {
                                                presets = (presets + sliderSpeed).sorted()
                                                onPresetsChanged(presets.map { formatSpeedRaw(it) }.toSet())
                                                Toast.makeText(context, "Added ${formatSpeed(sliderSpeed)}", Toast.LENGTH_SHORT).show()
                                            } else if (alreadyExists) {
                                                Toast.makeText(context, "${formatSpeed(sliderSpeed)} already exists", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Preset limit reached ($MAX_PRESETS)", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = !alreadyExists && !atLimit,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF64B5F6),
                                            disabledContainerColor = Color(0x33FFFFFF)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Add", fontSize = 13.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { sliderValue = 1.0f },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Reset to 1x", fontSize = 13.sp, color = Color(0xFF64B5F6))
                                    }
                                }

                                // 预设数量提示
                                if (presets.size >= MAX_PRESETS - 2) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Presets ${presets.size}/$MAX_PRESETS",
                                        fontSize = 11.sp,
                                        color = Color(0x88FFFFFF)
                                    )
                                }
                    }
                }
            }
        }
    }
}

// ===== 精确调速折叠区域（复用字幕设置的展开模式）=====

@Composable
private fun SpeedAdvancedSection(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0x1AFFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "▶",
                fontSize = 14.sp,
                color = Color(0xFF64B5F6),
                modifier = Modifier.rotate(rotationAngle)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Fine-tune Speed",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        // 展开内容
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(animationSpec = tween(150))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                content()
            }
        }
    }
}

// ===== 单个倍速项 =====

@Composable
private fun SpeedPresetItem(
    speed: Double,
    isCurrent: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val bgColor = if (isCurrent) Color(0x1A64B5F6) else Color.Transparent
    val textColor = if (isCurrent) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.85f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatSpeed(speed),
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        if (isCurrent) {
            Text(
                text = "Current",
                color = Color(0xFF64B5F6),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Text("✕", fontSize = 13.sp, color = Color(0x88FFFFFF))
            }
        }
    }
}

// ===== 工具函数 =====

private fun roundToStep(value: Double): Double {
    return Math.round(value * 20.0) / 20.0
}

private fun formatSpeed(speed: Double): String {
    val rounded = roundToStep(speed)
    return if (rounded == rounded.toLong().toDouble()) {
        "${rounded.toLong().toInt()}x"
    } else {
        val s = String.format("%.2f", rounded).trimEnd('0').trimEnd('.')
        "${s}x"
    }
}

private fun formatSpeedRaw(speed: Double): String {
    return if (speed == speed.toLong().toDouble()) {
        String.format("%.1f", speed)
    } else {
        String.format("%.2f", speed).trimEnd('0')
    }
}
