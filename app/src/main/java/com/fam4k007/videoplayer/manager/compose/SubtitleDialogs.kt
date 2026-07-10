package com.fam4k007.videoplayer.manager.compose

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import kotlin.math.roundToInt
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.yubyf.truetypeparser.TTFFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import `is`.xyz.mpv.MPVLib
import java.io.File
import java.util.Locale

/**
 * 自定义字体条目
 */
data class CustomFontEntry(
    val familyName: String,
    val file: File
)

/**
 * 从用户选择的目录复制字体文件到应用内部存储
 */
private suspend fun copyFontsFromDirectory(
    context: android.content.Context,
    uriString: String
): Int = withContext(Dispatchers.IO) {
    var count = 0
    runCatching {
        val destinationPath = context.filesDir.path + "/fonts"
        val destinationDir = File(destinationPath)
        if (!destinationDir.exists()) destinationDir.mkdirs()

        val sourceDir = DocumentFile.fromTreeUri(context, Uri.parse(uriString))
        sourceDir?.listFiles()?.forEach { file ->
            if (file.isFile) {
                val fileName = file.name ?: return@forEach
                if (fileName.lowercase(Locale.ROOT).matches(".*\\.[ot]tf$".toRegex())) {
                    val inputStream = context.contentResolver.openInputStream(file.uri) ?: return@forEach
                    val outputFile = File(destinationPath, fileName)
                    outputFile.outputStream().use { outputStream ->
                        inputStream.use { it.copyTo(outputStream) }
                    }
                    count++
                }
            }
        }
    }.onFailure { e ->
        Log.e("SubtitleDialogs", "Error copying fonts", e)
    }
    count
}

/**
 * 从应用内部字体目录加载字体条目（字体族名 + 文件），按族名去重排序
 */
private suspend fun loadCustomFontEntries(context: android.content.Context): List<CustomFontEntry> =
    withContext(Dispatchers.IO) {
        val fontsDir = File(context.filesDir, "fonts")
        if (!fontsDir.exists()) return@withContext emptyList()

        val fontFiles = fontsDir.listFiles()
            ?.filter { it.isFile && it.name.lowercase(Locale.ROOT).matches(".*\\.[ot]tf$".toRegex()) }
            .orEmpty()

        val entries = mutableListOf<CustomFontEntry>()
        val seenFamilies = mutableSetOf<String>()

        for (fontFile in fontFiles) {
            val familyName = runCatching {
                fontFile.inputStream().use { input ->
                    TTFFile.open(input).families.values.firstOrNull()
                }
            }.getOrNull()

            if (!familyName.isNullOrBlank() && seenFamilies.add(familyName)) {
                entries += CustomFontEntry(familyName, fontFile)
            }
        }

        entries.sortedBy { it.familyName }
    }

/**
 * 右侧抽屉式字幕设置面板
 */
@Composable
fun SubtitleSettingsDrawer(
    currentDelay: Double,
    currentScale: Float,
    currentPosition: Float,
    currentBorderSize: Int,
    currentTextColor: String,
    currentBorderColor: String,
    currentBackColor: String,
    currentBorderStyle: String,
    onDelayChange: (Double) -> Unit,
    onScaleChange: (Float) -> Unit,
    onPositionChange: (Float) -> Unit,
    onBorderSizeChange: (Int) -> Unit,
    onTextColorChange: (String) -> Unit,
    onBorderColorChange: (String) -> Unit,
    onBackColorChange: (String) -> Unit,
    onBorderStyleChange: (String) -> Unit,
    composeOverlayManager: ComposeOverlayManager,
    onDismiss: () -> Unit
) {
    var expandedSection by remember { mutableStateOf<String?>(null) }
    var isVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
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

    // 当展开状态改变时，自动滚动到对应的item（延迟等待展开动画）
    LaunchedEffect(expandedSection) {
        expandedSection?.let { section ->
            val index = when (section) {
                "delay" -> 0
                "style" -> 1
                "misc" -> 2
                "font" -> 3
                else -> null
            }
            index?.let {
                // 延迟100ms等待展开动画开始
                delay(100)
                listState.animateScrollToItem(it)
            }
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
                    kotlinx.coroutines.delay(300)
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
                    // 标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "更多设置",
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
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    // 使用LazyColumn实现可滚动列表，最多显示3个，支持未来扩展
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(20.dp) // 增大间距
                    ) {
                        // 字幕延迟设置
                        item {
                            ExpandableSection(
                                title = "字幕延迟设置",
                                isExpanded = expandedSection == "delay",
                                onToggle = { expandedSection = if (expandedSection == "delay") null else "delay" }
                            ) {
                                SubtitleDelayContent(
                                    currentDelay = currentDelay,
                                    onDelayChange = onDelayChange
                                )
                            }
                        }
                        
                        // 字幕样式设置
                        item {
                            ExpandableSection(
                                title = "字幕样式设置",
                                isExpanded = expandedSection == "style",
                                onToggle = { expandedSection = if (expandedSection == "style") null else "style" }
                            ) {
                                SubtitleStyleContent(
                                    currentBorderSize = currentBorderSize,
                                    currentTextColor = currentTextColor,
                                    currentBorderColor = currentBorderColor,
                                    currentBackColor = currentBackColor,
                                    currentBorderStyle = currentBorderStyle,
                                    onBorderSizeChange = onBorderSizeChange,
                                    onTextColorChange = onTextColorChange,
                                    onBorderColorChange = onBorderColorChange,
                                    onBackColorChange = onBackColorChange,
                                    onBorderStyleChange = onBorderStyleChange
                                )
                            }
                        }
                        
                        // 字幕杂项设置
                        item {
                            ExpandableSection(
                                title = "字幕杂项设置",
                                isExpanded = expandedSection == "misc",
                                onToggle = { expandedSection = if (expandedSection == "misc") null else "misc" }
                            ) {
                                SubtitleMiscContent(
                                    currentScale = currentScale,
                                    currentPosition = currentPosition,
                                    onScaleChange = onScaleChange,
                                    onPositionChange = onPositionChange
                                )
                            }
                        }
                        
                        // 字幕字体设置
                        item {
                            ExpandableSection(
                                title = "字幕字体设置",
                                isExpanded = expandedSection == "font",
                                onToggle = { expandedSection = if (expandedSection == "font") null else "font" }
                            ) {
                                SubtitleFontContent(composeOverlayManager)
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

/**
 * 可展开折叠的分组
 */
@Composable
fun ExpandableSection(
    title: String,
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
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        // 内容区域 - 优化动画性能
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

/**
 * 字幕延迟内容
 */
@Composable
fun SubtitleDelayContent(
    currentDelay: Double,
    onDelayChange: (Double) -> Unit
) {
    var delay by remember { mutableStateOf(currentDelay) }
    var inputText by remember { mutableStateOf(String.format("%.2f", currentDelay)) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 当前值显示（可编辑）
        androidx.compose.foundation.text.BasicTextField(
            value = inputText,
            onValueChange = { newValue ->
                inputText = newValue
                newValue.toDoubleOrNull()?.let { newDelay ->
                    if (newDelay in -60.0..60.0) { // 限制范围
                        delay = newDelay
                        onDelayChange(newDelay)
                    }
                }
            },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64B5F6),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                .padding(12.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            )
        )
        
        Text(
            text = "范围: -60.0 ~ +60.0 秒",
            fontSize = 11.sp,
            color = Color(0x99FFFFFF),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 对称布局：上下两行
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 第一行：-0.5s 和 +0.5s
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickButton("-0.5s") {
                    delay -= 0.5
                    delay = delay.coerceIn(-60.0, 60.0)
                    inputText = String.format("%.2f", delay)
                    onDelayChange(delay)
                }
                QuickButton("+0.5s") {
                    delay += 0.5
                    delay = delay.coerceIn(-60.0, 60.0)
                    inputText = String.format("%.2f", delay)
                    onDelayChange(delay)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 第二行：-0.1s 和 +0.1s
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickButton("-0.1s") {
                    delay -= 0.1
                    delay = delay.coerceIn(-60.0, 60.0)
                    inputText = String.format("%.2f", delay)
                    onDelayChange(delay)
                }
                QuickButton("+0.1s") {
                    delay += 0.1
                    delay = delay.coerceIn(-60.0, 60.0)
                    inputText = String.format("%.2f", delay)
                    onDelayChange(delay)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 重置按钮
        TextButton(
            onClick = {
                delay = 0.0
                inputText = "0.00"
                onDelayChange(0.0)
            },
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color(0xFF64B5F6)
            )
        ) {
            Text("重置为 0")
        }
    }
}

/**
 * 字幕样式内容
 */
@Composable
fun SubtitleStyleContent(
    currentBorderSize: Int,
    currentTextColor: String,
    currentBorderColor: String,
    currentBackColor: String,
    currentBorderStyle: String,
    onBorderSizeChange: (Int) -> Unit,
    onTextColorChange: (String) -> Unit,
    onBorderColorChange: (String) -> Unit,
    onBackColorChange: (String) -> Unit,
    onBorderStyleChange: (String) -> Unit
) {
    var expandedColorSection by remember { mutableStateOf<String?>(null) }
    
    // 管理各个颜色区域的选中状态
    var textColorSelectedIndex by remember { mutableStateOf<Int?>(null) }
    var borderColorSelectedIndex by remember { mutableStateOf<Int?>(null) }
    var backColorSelectedIndex by remember { mutableStateOf<Int?>(null) }
    
    val context = LocalContext.current
    val preferencesManager = remember { 
        com.fam4k007.videoplayer.preferences.PreferencesManager.getInstance(context)
    }
    var assOverrideEnabled by remember { mutableStateOf(preferencesManager.isAssOverrideEnabled()) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 样式覆盖开关
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1A2332)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "样式覆盖",
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (assOverrideEnabled) "已开启，将强制使用自定义样式" else "关闭时使用字幕文件内嵌样式",
                        fontSize = 11.sp,
                        color = Color(0xFF9E9E9E),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(
                    checked = assOverrideEnabled,
                    onCheckedChange = { enabled ->
                        assOverrideEnabled = enabled
                        preferencesManager.setAssOverrideEnabled(enabled)
                        // 立即应用到播放引擎，并重新加载字幕使其生效
                        MPVLib.setPropertyString("sub-ass-override", if (enabled) "force" else "scale")
                        MPVLib.command("sub-reload")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF64B5F6),
                        uncheckedThumbColor = Color(0xFF9E9E9E),
                        uncheckedTrackColor = Color(0xFF2A3A4A)
                    )
                )
            }
        }
        
        // 提示文本
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0x1A64B5F6),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ℹ️",
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = "内嵌ASS字幕需开启样式覆盖才能应用自定义样式",
                fontSize = 11.sp,
                color = Color(0xFFCCCCCC),
                maxLines = 2,
                softWrap = true
            )
        }
        
        // 字幕颜色
        ColorPickerSection(
            title = "字幕颜色",
            currentColor = currentTextColor,
            isExpanded = expandedColorSection == "text",
            onToggle = { expandedColorSection = if (expandedColorSection == "text") null else "text" },
            presetColors = listOf(
                Color.White, Color.Black, Color.Red, Color.Green,
                Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta
            ),
            selectedColorIndex = textColorSelectedIndex,
            onSelectedIndexChange = { textColorSelectedIndex = it },
            onColorSelected = { color -> 
                onTextColorChange(colorToHexString(color))
            }
        )
        
        // 字幕背景颜色
        ColorPickerSection(
            title = "背景颜色",
            currentColor = currentBackColor,
            isExpanded = expandedColorSection == "background",
            onToggle = { expandedColorSection = if (expandedColorSection == "background") null else "background" },
            presetColors = listOf(
                Color.Transparent, Color.Black, Color.White, Color(0x80FF0000),
                Color(0x8000FF00), Color(0x800000FF), Color(0x80FFFF00), Color(0x80808080)
            ),
            selectedColorIndex = backColorSelectedIndex,
            onSelectedIndexChange = { backColorSelectedIndex = it },
            onColorSelected = { color ->
                onBackColorChange(colorToHexString(color))
            }
        )
        
        // 描边颜色
        ColorPickerSection(
            title = "描边颜色",
            currentColor = currentBorderColor,
            isExpanded = expandedColorSection == "border",
            onToggle = { expandedColorSection = if (expandedColorSection == "border") null else "border" },
            presetColors = listOf(
                Color.White, Color.Black, Color.Red, Color.Green,
                Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta
            ),
            selectedColorIndex = borderColorSelectedIndex,
            onSelectedIndexChange = { borderColorSelectedIndex = it },
            onColorSelected = { color ->
                onBorderColorChange(colorToHexString(color))
            }
        )
        
        // 描边粗细大小
        BorderSizeSection(
            currentSize = currentBorderSize,
            onSizeChange = onBorderSizeChange
        )
        
        // 描边模式选择
        BorderStyleSection(
            currentStyle = currentBorderStyle,
            onStyleSelected = onBorderStyleChange
        )
        
        // 重置所有颜色按钮
        TextButton(
            onClick = {
                // 重置颜色值
                onTextColorChange("#FFFFFFFF")      // 白色
                onBorderColorChange("#FF000000")    // 黑色
                onBackColorChange("#00000000")      // 透明
                onBorderSizeChange(3)
                onBorderStyleChange("outline-and-shadow")
                
                // 清空所有选中状态
                textColorSelectedIndex = null
                borderColorSelectedIndex = null
                backColorSelectedIndex = null
            },
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color(0xFFFF6666)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("重置所有样式为默认值")
        }
    }
}

/**
 * 将Compose Color转换为Hex字符串 (#AARRGGBB格式)
 */
fun colorToHexString(color: Color): String {
    val alpha = (color.alpha * 255).toInt()
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return String.format("#%02X%02X%02X%02X", alpha, red, green, blue)
}

/**
 * 颜色选择器分组
 */
@Composable
fun ColorPickerSection(
    title: String,
    currentColor: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    presetColors: List<Color>,
    selectedColorIndex: Int?,
    onSelectedIndexChange: (Int?) -> Unit,
    onColorSelected: (Color) -> Unit
) {
    // 初始化时根据当前颜色匹配预设色块
    LaunchedEffect(currentColor) {
        val matchedIndex = presetColors.take(4).indexOfFirst { color ->
            colorToHexString(color).equals(currentColor, ignoreCase = true)
        }
        onSelectedIndexChange(if (matchedIndex >= 0) matchedIndex else null)
    }
    
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
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        // 标题行：带预设色块和展开按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            
            // 预设色块
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presetColors.take(4).forEachIndexed { index, color ->
                    val isSelected = selectedColorIndex == index
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color, CircleShape)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color(0xFF64B5F6) else Color(0x33FFFFFF),
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onSelectedIndexChange(index)
                                onColorSelected(color)
                            }
                    )
                }
            }
            
            // 展开按钮
            Text(
                text = "▶",
                fontSize = 12.sp,
                color = Color(0xFF64B5F6),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .rotate(rotationAngle)
                    .clickable { onToggle() }
            )
        }
        
        // RGBA调节滑块（展开时显示）
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                RGBASliders(
                    onColorChanged = { 
                        onSelectedIndexChange(null) // 清除预设选中状态
                        onColorSelected(it) 
                    }
                )
            }
        }
    }
}

/**
 * RGBA滑块组件
 */
@Composable
fun RGBASliders(onColorChanged: (Color) -> Unit) {
    var r by remember { mutableStateOf(255) }
    var g by remember { mutableStateOf(255) }
    var b by remember { mutableStateOf(255) }
    var a by remember { mutableStateOf(255) }
    
    fun updateColor() {
        val color = Color(r, g, b, a)
        onColorChanged(color)
    }
    
    // R 滑块
    SliderRow(label = "R", value = r, color = Color(0xFFFF6666)) { value ->
        r = value
        updateColor()
    }
    
    // G 滑块
    SliderRow(label = "G", value = g, color = Color(0xFF66FF66)) { value ->
        g = value
        updateColor()
    }
    
    // B 滑块
    SliderRow(label = "B", value = b, color = Color(0xFF6666FF)) { value ->
        b = value
        updateColor()
    }
    
    // A 滑块
    SliderRow(label = "A", value = a, color = Color.White) { value ->
        a = value
        updateColor()
    }
}

/**
 * 单个滑块行
 */
@Composable
fun SliderRow(
    label: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(20.dp)
        )
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6),
                inactiveTrackColor = Color(0xFF555555)
            )
        )
        
        Text(
            text = value.toString(),
            fontSize = 12.sp,
            color = Color.White,
            modifier = Modifier.width(35.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

/**
 * 描边粗细大小设置
 */
@Composable
fun BorderSizeSection(
    currentSize: Int,
    onSizeChange: (Int) -> Unit
) {
    var borderSize by remember { mutableStateOf(currentSize.toFloat()) }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0x1AFFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "描边粗细：",
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )

            if (isEditing) {
                androidx.compose.foundation.text.BasicTextField(
                    value = editText,
                    onValueChange = { newValue: String ->
                        if (newValue.isEmpty() || (newValue.all { c -> c.isDigit() } && (newValue.toIntOrNull() ?: 100) <= 99)) {
                            editText = newValue
                        }
                    },
                    modifier = Modifier
                        .width(52.dp)
                        .background(Color.Transparent)
                        .border(1.dp, Color(0xFF64B5F6), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            val value = editText.toIntOrNull()
                            if (value != null && value in 0..99) {
                                borderSize = value.toFloat()
                                onSizeChange(value)
                                isEditing = false
                            }
                        }
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF64B5F6))
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        val value = editText.toIntOrNull()
                        if (value != null && value in 0..99) {
                            borderSize = value.toFloat()
                            onSizeChange(value)
                            isEditing = false
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "确定",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { isEditing = false },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "取消",
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Text(
                    text = "${borderSize.roundToInt()}",
                    fontSize = 14.sp,
                    color = Color(0xFF64B5F6),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        editText = borderSize.roundToInt().toString()
                        isEditing = true
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = borderSize,
            onValueChange = {
                borderSize = it
                onSizeChange(it.roundToInt())
            },
            valueRange = 0f..10f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6),
                inactiveTrackColor = Color(0xFF555555)
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    borderSize = 3f
                    onSizeChange(3)
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF64B5F6)
                )
            ) {
                Text("重置为 3")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "点击笔可输入 0-99 的精确值",
                fontSize = 11.sp,
                color = Color(0xFF888888)
            )
        }
    }
}

/**
 * 描边模式选择
 */
@Composable
fun BorderStyleSection(
    currentStyle: String,
    onStyleSelected: (String) -> Unit
) {
    var selectedStyle by remember { mutableStateOf(currentStyle) }
    
    // 监听外部状态变化，同步更新内部状态
    LaunchedEffect(currentStyle) {
        selectedStyle = currentStyle
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0x1AFFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "描边模式",
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 三种模式的单选按钮（添加切换动画）
        BorderStyleOption(
            title = "模式A",
            description = "通过描边颜色项修改",
            isSelected = selectedStyle == "outline-and-shadow",
            onClick = {
                selectedStyle = "outline-and-shadow"
                onStyleSelected("outline-and-shadow")
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        BorderStyleOption(
            title = "模式B",
            description = "通过描边颜色项修改",
            isSelected = selectedStyle == "opaque-box",
            onClick = {
                selectedStyle = "opaque-box"
                onStyleSelected("opaque-box")
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        BorderStyleOption(
            title = "模式C",
            description = "通过背景颜色项修改",
            isSelected = selectedStyle == "background-box",
            onClick = {
                selectedStyle = "background-box"
                onStyleSelected("background-box")
            }
        )
    }
}

/**
 * 单个描边模式选项（添加动画效果）
 */
@Composable
fun BorderStyleOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // 背景颜色动画
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0x33FFFFFF) else Color.Transparent,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "background"
    )
    
    // 边框颜色动画
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF64B5F6) else Color(0xFF666666),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "border"
    )
    
    // 内圆缩放动画
    val innerCircleScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 单选圆圈
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = 2.dp,
                    color = borderColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // 内圆有缩放动画
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(innerCircleScale)
                    .background(Color(0xFF64B5F6), CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color(0x99FFFFFF)
            )
        }
    }
}

/**
 * 字幕大小位置内容
 */
@Composable
fun SubtitleMiscContent(
    currentScale: Float,
    currentPosition: Float,
    onScaleChange: (Float) -> Unit,
    onPositionChange: (Float) -> Unit
) {
    var scale by remember { mutableStateOf(currentScale) }
    var position by remember { mutableStateOf(currentPosition) }
    
    // 使用LaunchedEffect监听外部状态变化
    LaunchedEffect(currentScale) {
        scale = currentScale
    }
    
    LaunchedEffect(currentPosition) {
        position = currentPosition
    }
    
    // 添加动画 - 优化性能
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    Column {
        // 字幕大小
        Text(
            text = "字幕大小：${(animatedScale * 100).toInt()}%",
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = "范围: 50% ~ 300%",
            fontSize = 11.sp,
            color = Color(0x99FFFFFF),
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = scale,
            onValueChange = {
                scale = it
                onScaleChange(it)
            },
            valueRange = 0.5f..3.0f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6),
                inactiveTrackColor = Color(0xFF555555)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 字幕垂直位置
        Text(
            text = "字幕垂直位置：${position.toInt()}%",
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = "范围: 0% (顶部) ~ 100% (底部)",
            fontSize = 11.sp,
            color = Color(0x99FFFFFF),
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = position,
            onValueChange = {
                position = it
                onPositionChange(it)
            },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6),
                inactiveTrackColor = Color(0xFF555555)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                scale = 1.0f
                position = 100f
                onScaleChange(1.0f)
                onPositionChange(100f)
            },
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color(0xFF64B5F6)
            )
        ) {
            Text("重置默认值 (100%, 位置100)")
        }
    }
}

/**
 * 快捷按钮
 */
@Composable
fun QuickButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.width(120.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64B5F6))
    ) {
        Text(text, fontSize = 14.sp)
    }
}

/**
 * 字幕字体设置内容
 */
@Composable
fun SubtitleFontContent(composeOverlayManager: ComposeOverlayManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesManager = remember { 
        com.fam4k007.videoplayer.preferences.PreferencesManager.getInstance(context)
    }
    
    var currentFontName by remember { mutableStateOf(preferencesManager.getSubtitleFontName()) }
    var fontDirUri by remember { mutableStateOf(preferencesManager.getFontDirectoryUri()) }
    var fontEntries by remember { mutableStateOf<List<CustomFontEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var expandedFontList by remember { mutableStateOf(false) }
    
    // 加载字体列表
    LaunchedEffect(fontDirUri) {
        isLoading = true
        fontEntries = loadCustomFontEntries(context)
        isLoading = false
    }
    
    // SAF 目录选择器
    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        // 持久化 URI 权限
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val uriString = uri.toString()
        preferencesManager.setFontDirectoryUri(uriString)
        fontDirUri = uriString
        
        // 复制字体到内部目录
        coroutineScope.launch {
            isLoading = true
            val count = copyFontsFromDirectory(context, uriString)
            fontEntries = loadCustomFontEntries(context)
            isLoading = false
            Toast.makeText(context, "已复制 $count 个字体文件，加载了 ${fontEntries.size} 种字体", Toast.LENGTH_SHORT).show()
        }
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 字体目录选择
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { dirPickerLauncher.launch(null) },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF2A3A4A)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "选择字体目录",
                        fontSize = 15.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    if (fontDirUri.isNotBlank()) {
                        Text(
                            text = if (isLoading) "正在加载..." else "已加载 ${fontEntries.size} 种字体",
                            fontSize = 12.sp,
                            color = if (fontEntries.isNotEmpty()) Color(0xFF81C784) else Color(0xFF9E9E9E),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Text(
                            text = "点击选择包含 .ttf/.otf 字体文件的目录",
                            fontSize = 12.sp,
                            color = Color(0xFF9E9E9E),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                
                // 刷新和清除按钮
                if (fontDirUri.isNotBlank()) {
                    Row {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    copyFontsFromDirectory(context, fontDirUri)
                                    fontEntries = loadCustomFontEntries(context)
                                    isLoading = false
                                    Toast.makeText(context, "已刷新，加载了 ${fontEntries.size} 种字体", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                preferencesManager.setFontDirectoryUri("")
                                fontDirUri = ""
                                // 清空内部字体目录
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        File(context.filesDir, "fonts").listFiles()?.forEach { it.delete() }
                                    }
                                    fontEntries = emptyList()
                                    // 如果当前字体不是默认的，重置
                                    currentFontName = ""
                                    preferencesManager.setSubtitleFontName("")
                                    Toast.makeText(context, "已清除字体目录", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清除",
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // 字体选择（合并当前字体显示和选择列表）
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        if (fontEntries.isNotEmpty()) expandedFontList = !expandedFontList 
                    },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2A3A4A)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "当前字体",
                            fontSize = 12.sp,
                            color = Color(0xFF9E9E9E)
                        )
                        Text(
                            text = currentFontName.ifBlank { "默认字体" },
                            fontSize = 15.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (fontEntries.isNotEmpty()) {
                        Text(
                            text = if (expandedFontList) "▲" else "▼",
                            fontSize = 14.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    } else {
                        Text(
                            text = "请先选择字体目录",
                            fontSize = 11.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }
            }
                
                AnimatedVisibility(
                    visible = expandedFontList,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "默认字体"选项
                        val isDefaultSelected = currentFontName.isBlank()
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentFontName = ""
                                    preferencesManager.setSubtitleFontName("")
                                    expandedFontList = false
                                    Toast.makeText(context, "已切换到: 默认字体\n重新播放生效", Toast.LENGTH_SHORT).show()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDefaultSelected) Color(0xFF405060) else Color(0xFF1A2332)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(
                                            if (isDefaultSelected) Color(0xFF64B5F6) else Color.Transparent,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDefaultSelected) {
                                        Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("默认字体", fontSize = 14.sp, color = Color.White)
                            }
                        }
                        
                        // 自定义字体列表
                        fontEntries.forEach { entry ->
                            val isSelected = currentFontName == entry.familyName
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentFontName = entry.familyName
                                        preferencesManager.setSubtitleFontName(entry.familyName)
                                        expandedFontList = false
                                        Toast.makeText(context, "已切换到: ${entry.familyName}\n重新播放生效", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF405060) else Color(0xFF1A2332)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(
                                                if (isSelected) Color(0xFF64B5F6) else Color.Transparent,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(entry.familyName, fontSize = 14.sp, color = Color.White)
                                        Text(
                                            text = entry.file.name,
                                            fontSize = 11.sp,
                                            color = Color(0xFF9E9E9E)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        
        // 提示信息
        Text(
            text = "💡 提示：字体更改需要重新播放视频才能生效\n💡 内嵌ASS字幕需开启样式覆盖后字体设置才会生效",
            fontSize = 12.sp,
            color = Color(0xFF9E9E9E),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                .padding(12.dp)
        )
    }
}


// 保留旧的Dialog组件（兼容性）
@Composable
fun SubtitleDelayDialog(
    currentDelay: Double,
    onDelayChange: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    // 已废弃，保留以防其他地方调用
}

@Composable
fun SubtitleMiscDialog(
    currentScale: Float,
    currentPosition: Float,
    onScaleChange: (Float) -> Unit,
    onPositionChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    // 已废弃
}

@Composable
fun SubtitleStyleDialog(
    currentBorderSize: Int,
    onBorderSizeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 已废弃
}
