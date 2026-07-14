package com.fam4k007.videoplayer.manager.compose

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fam4k007.videoplayer.preferences.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 字幕文件选择器对话框 - Compose UI 风格
 * 与弹幕选择器保持一致的右侧抽屉式设计
 */
@Composable
fun SubtitleFilePickerDialog(
    initialPath: String? = null,
    onFileSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    var currentPath by remember { 
        mutableStateOf(
            initialPath ?: Environment.getExternalStorageDirectory().absolutePath
        ) 
    }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var animationDirection by remember { mutableStateOf(0) } // -1=向左(进入子目录), 1=向右(返回上级), 0=无动画
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val skipAnim = com.fam4k007.videoplayer.manager.compose.ComposeOverlayManager.globalDisableAnimations
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager.getInstance(context) }

    // 支持的字幕文件扩展名
    val supportedExtensions = listOf(".ass", ".srt", ".vtt", ".ssa", ".sub", ".idx")

    // 排序状态 - 从 SharedPreferences 读取上次的排序设置
    var sortBy by remember {
        mutableStateOf(
            when (preferencesManager.getSubtitleFileSortBy()) {
                "DATE" -> SortBy.DATE
                else -> SortBy.NAME
            }
        )
    }
    var sortOrder by remember {
        mutableStateOf(
            if (preferencesManager.getSubtitleFileSortOrder() == "DESCENDING")
                SortOrder.DESCENDING
            else
                SortOrder.ASCENDING
        )
    }
    var showSortMenu by remember { mutableStateOf(false) }

    // 排序改变时保存到 SharedPreferences
    LaunchedEffect(sortBy) {
        val sortByName = when (sortBy) {
            SortBy.NAME -> "NAME"
            SortBy.DATE -> "DATE"
            else -> "NAME"
        }
        preferencesManager.setSubtitleFileSortBy(sortByName)
    }

    LaunchedEffect(sortOrder) {
        val orderName = when (sortOrder) {
            SortOrder.ASCENDING -> "ASCENDING"
            SortOrder.DESCENDING -> "DESCENDING"
        }
        preferencesManager.setSubtitleFileSortOrder(orderName)
    }

    // 加载目录文件
    fun loadFiles(path: String, direction: Int = 0) {
        isLoading = true
        animationDirection = direction
        try {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                val rawList = dir.listFiles()?.filter { file ->
                    // 显示文件夹和字幕文件
                    file.isDirectory || supportedExtensions.any { ext ->
                        file.name.endsWith(ext, ignoreCase = true)
                    }
                } ?: emptyList()
                
                // 排序：文件夹始终在前（按名称升序），然后按用户选择的排序方式排序
                val dirs = rawList.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
                val fileList = rawList.filter { !it.isDirectory }.let { files ->
                    when (sortBy) {
                        SortBy.NAME -> {
                            if (sortOrder == SortOrder.ASCENDING)
                                files.sortedBy { it.name.lowercase() }
                            else
                                files.sortedByDescending { it.name.lowercase() }
                        }
                        SortBy.DATE -> {
                            if (sortOrder == SortOrder.ASCENDING)
                                files.sortedBy { it.lastModified() }
                            else
                                files.sortedByDescending { it.lastModified() }
                        }
                        else -> files
                    }
                }
                
                files = dirs + fileList
                currentPath = path
                
                // 滚动到顶部
                coroutineScope.launch {
                    listState.scrollToItem(0)
                }
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                "无法访问目录: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } finally {
            isLoading = false
        }
    }

    // 启动时加载文件
    LaunchedEffect(Unit) {
        isVisible = true
        loadFiles(currentPath, direction = 0)
    }

    // 当排序方式改变时，重新排序当前目录
    LaunchedEffect(sortBy, sortOrder) {
        if (currentPath.isNotEmpty()) {
            loadFiles(currentPath, direction = 0)
        }
    }

    // 处理返回键
    BackHandler(enabled = isVisible) {
        if (currentPath != "/storage/emulated/0" && currentPath != "/") {
            // 返回上一级目录
            val parentPath = File(currentPath).parent
            if (parentPath != null) {
                loadFiles(parentPath, direction = 1) // 向右滑出动画
            } else {
                isVisible = false
                coroutineScope.launch {
                    delay(300)
                    onDismiss()
                }
            }
        } else {
            isVisible = false
            coroutineScope.launch {
                delay(300)
                onDismiss()
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
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { /* 阻止事件穿透 */ }
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 标题栏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 动态标题：根目录显示文件名，非根目录显示返回按钮
                            val isRootPath = currentPath == "/storage/emulated/0" || currentPath == "/"
                            Text(
                                text = if (isRootPath) "Select Subtitle File" else "← Back",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRootPath) Color.White else Color(0xFF64B5F6),
                                modifier = Modifier.clickable(
                                    enabled = !isRootPath,
                                    indication = null,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                ) {
                                    if (!isRootPath) {
                                        val parentPath = File(currentPath).parent
                                        if (parentPath != null) {
                                            loadFiles(parentPath, direction = 1)
                                        }
                                    }
                                }
                            )

                            // 右侧按钮组（排序 + 关闭）
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 排序按钮
                                Box {
                                    IconButton(
                                        onClick = { showSortMenu = !showSortMenu },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sort,
                                            contentDescription = "Sort",
                                            tint = Color(0xFF64B5F6),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // 排序菜单
                                    SubtitleSortMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                        sortBy = sortBy,
                                        sortOrder = sortOrder,
                                        onSortByChange = { sortBy = it },
                                        onSortOrderChange = { sortOrder = it }
                                    )
                                }

                                // 关闭按钮
                                IconButton(
                                    onClick = {
                                        isVisible = false
                                        coroutineScope.launch {
                                            delay(300)
                                            onDismiss()
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "✕",
                                        fontSize = 20.sp,
                                        color = Color(0xFFBBBBBB)
                                    )
                                }
                            }
                        }

                        // 路径导航栏
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x33FFFFFF)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 当前路径显示
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📁 ",
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = currentPath,
                                        fontSize = 12.sp,
                                        color = Color(0xFFCCCCCC),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // 文件列表 - 使用AnimatedContent实现切换动画
                        AnimatedContent(
                            targetState = currentPath,
                            transitionSpec = {
                                if (animationDirection == -1) {
                                    // 进入子目录: 从右向左滑入
                                    slideInHorizontally(
                                        initialOffsetX = { it },
                                        animationSpec = tween(300)
                                    ) + fadeIn(animationSpec = tween(300)) togetherWith
                                    slideOutHorizontally(
                                        targetOffsetX = { -it },
                                        animationSpec = tween(300)
                                    ) + fadeOut(animationSpec = tween(300))
                                } else if (animationDirection == 1) {
                                    // 返回上级: 从左向右滑入
                                    slideInHorizontally(
                                        initialOffsetX = { -it },
                                        animationSpec = tween(300)
                                    ) + fadeIn(animationSpec = tween(300)) togetherWith
                                    slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = tween(300)
                                    ) + fadeOut(animationSpec = tween(300))
                                } else {
                                    // 首次加载: 淡入淡出
                                    fadeIn(animationSpec = tween(200)) togetherWith
                                    fadeOut(animationSpec = tween(200))
                                }
                            },
                            label = "fileListAnimation"
                        ) { path ->
                            if (isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF6200EE)
                                    )
                                }
                            } else if (files.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No subtitle files in this directory",
                                        fontSize = 16.sp,
                                        color = Color(0xFF999999),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    state = listState,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(files, key = { it.absolutePath }) { file ->
                                        SubtitleFileItem(
                                            file = file,
                                            onClick = {
                                                if (file.isDirectory) {
                                                    loadFiles(file.absolutePath, direction = -1) // 向左滑入
                                                } else {
                                                    // 选择文件
                                                    onFileSelected(file.absolutePath)
                                                    isVisible = false
                                                    coroutineScope.launch {
                                                        delay(300)
                                                        onDismiss()
                                                    }
                                                }
                                            }
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
}

@Composable
private fun SubtitleFileItem(
    file: File,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val fileSize = if (file.isFile) {
        val size = file.length()
        when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    } else {
        ""
    }
    val modifiedDate = dateFormat.format(Date(file.lastModified()))

    // 根据文件扩展名显示不同图标
    val fileIcon = when {
        file.isDirectory -> "📁"
        file.name.endsWith(".ass", ignoreCase = true) -> "📝"
        file.name.endsWith(".srt", ignoreCase = true) -> "📄"
        file.name.endsWith(".vtt", ignoreCase = true) -> "📄"
        else -> "📄"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (file.isDirectory) Color(0x22FFFFFF) else Color(0x33FFFFFF)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文件图标
            Text(
                text = fileIcon,
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 12.dp)
            )

            // 文件信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (file.isFile) {
                        Text(
                            text = fileSize,
                            fontSize = 12.sp,
                            color = Color(0xFF999999)
                        )
                    }
                    Text(
                        text = modifiedDate,
                        fontSize = 12.sp,
                        color = Color(0xFF999999)
                    )
                }
            }

            // 箭头指示器（仅文件夹）
            if (file.isDirectory) {
                Text(
                    text = "›",
                    fontSize = 24.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}

/**
 * 字幕文件选择器的排序菜单
 * 仅支持按文件名和添加时间排序
 */
@Composable
private fun SubtitleSortMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    sortBy: SortBy,
    sortOrder: SortOrder,
    onSortByChange: (SortBy) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = Modifier
                .background(Color(0xE6212121))
                .width(160.dp)
        ) {
            // 排序方式分组
            Text(
                text = "Sort By",
                fontSize = 11.sp,
                color = Color(0x99FFFFFF),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SortMenuItem(
                text = "File Name",
                isSelected = sortBy == SortBy.NAME,
                onClick = {
                    onSortByChange(SortBy.NAME)
                    onDismissRequest()
                }
            )

            SortMenuItem(
                text = "Date Added",
                isSelected = sortBy == SortBy.DATE,
                onClick = {
                    onSortByChange(SortBy.DATE)
                    onDismissRequest()
                }
            )

            Divider(
                color = Color(0x33FFFFFF),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // 排序顺序分组
            Text(
                text = "Sort Order",
                fontSize = 11.sp,
                color = Color(0x99FFFFFF),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SortMenuItem(
                text = "Ascending ↑",
                isSelected = sortOrder == SortOrder.ASCENDING,
                onClick = {
                    onSortOrderChange(SortOrder.ASCENDING)
                    onDismissRequest()
                }
            )

            SortMenuItem(
                text = "Descending ↓",
                isSelected = sortOrder == SortOrder.DESCENDING,
                onClick = {
                    onSortOrderChange(SortOrder.DESCENDING)
                    onDismissRequest()
                }
            )
        }
    }
}
