package com.fam4k007.videoplayer.manager.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MIN_RANGE = 10
private const val MAX_RANGE = 600

private fun formatSeconds(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%d:%02d".format(mins, secs)
}

@Composable
fun SkipSettingsDrawer(
    enabled: Boolean,
    currentSkipIntro: Int,
    currentSkipOutro: Int,
    currentIntroRange: Int,
    currentOutroRange: Int,
    getCurrentPosition: () -> Double,
    getDuration: () -> Double,
    onEnabledChange: (Boolean) -> Unit,
    onIntroRangeChange: (Int) -> Unit,
    onOutroRangeChange: (Int) -> Unit,
    onSkipIntroChange: (Int) -> Unit,
    onSkipOutroChange: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val skipAnim = ComposeOverlayManager.globalDisableAnimations
    val focusManager = LocalFocusManager.current

    var toggle by remember { mutableStateOf(enabled) }
    LaunchedEffect(enabled) { toggle = enabled }

    // 本地可变状态，使重置和秒数变更即时反映到 UI
    var introSeconds by remember { mutableIntStateOf(currentSkipIntro) }
    var outroSeconds by remember { mutableIntStateOf(currentSkipOutro) }
    var introRange by remember { mutableIntStateOf(currentIntroRange) }
    var outroRange by remember { mutableIntStateOf(currentOutroRange) }

    LaunchedEffect(currentSkipIntro) { introSeconds = currentSkipIntro }
    LaunchedEffect(currentSkipOutro) { outroSeconds = currentSkipOutro }
    LaunchedEffect(currentIntroRange) { introRange = currentIntroRange }
    LaunchedEffect(currentOutroRange) { outroRange = currentOutroRange }

    LaunchedEffect(Unit) { isVisible = true }

    BackHandler(enabled = isVisible) {
        isVisible = false
        coroutineScope.launch { delay(300); onDismiss() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                isVisible = false
                coroutineScope.launch { delay(300); onDismiss() }
            }
    ) {
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
                    .width(330.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xCC121212), Color(0xE6121212))
                        )
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ===== frozen header =====
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\u7247\u5934\u7247\u5C3E\u8BBE\u7F6E",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(
                            onClick = {
                                isVisible = false
                                coroutineScope.launch { delay(300); onDismiss() }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("\u2715", fontSize = 20.sp, color = Color(0xFFBBBBBB))
                        }
                    }

                    HorizontalDivider(color = Color(0x33FFFFFF), thickness = 1.dp)

                    // ===== scrollable content =====
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 24.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { focusManager.clearFocus() }
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // ---- global toggle (equalizer style) ----
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "\u542F\u7528\u8DF3\u8FC7\u7247\u5934\u7247\u5C3E",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "\u901A\u8FC7\u624B\u52A8\u8BBE\u7F6E\u79D2\u6570\u6765\u8DF3\u8FC7\u7247\u5934\u7247\u5C3E",
                                    fontSize = 12.sp,
                                    color = Color(0x88FFFFFF),
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = toggle,
                                onCheckedChange = {
                                    toggle = it
                                    onEnabledChange(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF64B5F6),
                                    checkedTrackColor = Color(0x8864B5F6),
                                    uncheckedThumbColor = Color(0xFF9E9E9E),
                                    uncheckedTrackColor = Color(0x88757575)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp)

                        AnimatedVisibility(
                            visible = toggle,
                            enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(300)),
                            exit = shrinkVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(250))
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))

                                // ---- intro section ----
                                SkipSection(
                                    label = "\u8DF3\u8FC7\u7247\u5934",
                                    accentColor = Color(0xFF64B5F6),
                                    currentSeconds = introSeconds,
                                    range = introRange,
                                    rangeLabel = "\u7247\u5934\u8303\u56F4",
                                    onSecondsChange = { introSeconds = it; onSkipIntroChange(it) },
                                    onRangeChange = { introRange = it; onIntroRangeChange(it) },
                                    focusManager = focusManager,
                                    getSnapSeconds = { getCurrentPosition().toInt().coerceIn(0, introRange) },
                                    snapButtonText = "\u8BBE\u4E3A\u5F53\u524D\u65F6\u95F4"
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0x18FFFFFF), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(16.dp))

                                // ---- outro section ----
                                SkipSection(
                                    label = "\u8DF3\u8FC7\u7247\u5C3E",
                                    accentColor = Color(0xFFE05666),
                                    currentSeconds = outroSeconds,
                                    range = outroRange,
                                    rangeLabel = "\u7247\u5C3E\u8303\u56F4",
                                    onSecondsChange = { outroSeconds = it; onSkipOutroChange(it) },
                                    onRangeChange = { outroRange = it; onOutroRangeChange(it) },
                                    focusManager = focusManager,
                                    getSnapSeconds = {
                                        val dur = getDuration()
                                        val pos = getCurrentPosition()
                                        if (dur > 0 && pos < dur) (dur - pos).toInt().coerceIn(0, outroRange) else 0
                                    },
                                    snapButtonText = "\u8BBE\u4E3A\u5F53\u524D\u5269\u4F59\u65F6\u95F4"
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = Color(0x18FFFFFF), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(16.dp))

                                // ---- reset button ----
                                TextButton(
                                    onClick = {
                                        introSeconds = 0
                                        outroSeconds = 0
                                        introRange = 180
                                        outroRange = 180
                                        onSkipIntroChange(0)
                                        onSkipOutroChange(0)
                                        onIntroRangeChange(180)
                                        onOutroRangeChange(180)
                                        onReset()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color(0xFF64B5F6)
                                    )
                                ) {
                                    Text(
                                        text = "\u4E00\u952E\u91CD\u7F6E",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipSection(
    label: String,
    accentColor: Color,
    currentSeconds: Int,
    range: Int,
    rangeLabel: String,
    onSecondsChange: (Int) -> Unit,
    onRangeChange: (Int) -> Unit,
    focusManager: FocusManager,
    getSnapSeconds: () -> Int,
    snapButtonText: String
) {
    val initialClamped = currentSeconds.coerceAtMost(range)
    var sliderValue by remember { mutableFloatStateOf(initialClamped.toFloat()) }
    var textValue by remember { mutableStateOf(initialClamped.toString()) }
    var hasUserEditedText by remember { mutableStateOf(false) }

    var rangeText by remember(range) { mutableStateOf(range.toString()) }

    LaunchedEffect(currentSeconds, range) {
        if (!hasUserEditedText) {
            val clamped = currentSeconds.coerceAtMost(range)
            sliderValue = clamped.toFloat()
            textValue = clamped.toString()
        }
    }

    Column {
        // Row 1: label (left) | seconds input + "秒" + mm:ss (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() }
                        textValue = filtered
                        hasUserEditedText = true
                        val num = filtered.toIntOrNull()
                        if (num != null) {
                            val clamped = num.coerceIn(0, range)
                            sliderValue = clamped.toFloat()
                            onSecondsChange(clamped)
                        }
                    },
                    modifier = Modifier.width(52.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp, textAlign = TextAlign.Center, color = Color.White
                    ),
                    cursorBrush = SolidColor(accentColor),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1A2332), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) { innerTextField() }
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "\u79D2", fontSize = 13.sp, color = Color(0x99FFFFFF))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatSeconds(currentSeconds.coerceAtMost(range)),
                    fontSize = 13.sp,
                    color = Color(0x66FFFFFF),
                    modifier = Modifier.width(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Row 2: range label (left) | range input (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rangeLabel, fontSize = 13.sp, color = Color(0xBBFFFFFF)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = rangeText,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() }
                        rangeText = filtered
                        val num = filtered.toIntOrNull() ?: return@BasicTextField
                        val clamped = num.coerceIn(MIN_RANGE, MAX_RANGE)
                        onRangeChange(clamped)
                    },
                    modifier = Modifier.width(52.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp, textAlign = TextAlign.Center, color = Color.White
                    ),
                    cursorBrush = SolidColor(Color(0xFF64B5F6)),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1A2332), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) { innerTextField() }
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "s", fontSize = 12.sp, color = Color(0x77FFFFFF))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 3: stepless slider
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                hasUserEditedText = false
                val secs = it.toInt()
                textValue = secs.toString()
                onSecondsChange(secs)
            },
            valueRange = 0f..range.toFloat(),
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color(0xFF444444)
            ),
            modifier = Modifier.height(20.dp)
        )

        // Row 4: hint
        Text(
            text = "\u62D6\u52A8\u6216\u8F93\u5165\u8BBE\u7F6E\u65F6\u95F4\uFF0C\u53EF\u6309\u9700\u8C03\u6574\u4E0A\u65B9\u8303\u56F4",
            fontSize = 11.sp,
            color = Color(0x44FFFFFF)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Row 5: snap button
        TextButton(
            onClick = {
                val snapped = getSnapSeconds()
                sliderValue = snapped.toFloat()
                textValue = snapped.toString()
                hasUserEditedText = false
                onSecondsChange(snapped)
            },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = snapButtonText, fontSize = 14.sp, color = accentColor)
        }
    }
}
