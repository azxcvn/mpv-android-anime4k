package com.fam4k007.videoplayer.manager.compose

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fam4k007.videoplayer.VideoFileParcelable
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.utils.NaturalOrderComparator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log10
import kotlin.math.pow

/**
 * 排序方式枚举
 */
enum class SortBy {
    NAME,      // 按文件名
    SIZE,      // 按大小
    DURATION,  // 按时长
    DATE       // 按添加时间
}

/**
 * 排序顺序枚举
 */
enum class SortOrder {
    ASCENDING,  // 升序
    DESCENDING  // 降序
}

/**
 * 右侧抽屉式视频列表面板
 * 样式参考字幕设置和更多设置，完全保持一致
 */
@Composable
fun VideoListDrawer(
    videoList: List<VideoFileParcelable>,
    currentVideoUri: Uri,
    onVideoSelected: (VideoFileParcelable, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager.getInstance(context) }
    
    var isVisible by remember { mutableStateOf(false) }
    
    // 从 SharedPreferences 读取上次的排序设置
    var sortBy by remember { 
        mutableStateOf(
            when (preferencesManager.getVideoListSortBy()) {
                "SIZE" -> SortBy.SIZE
                "DURATION" -> SortBy.DURATION
                "DATE" -> SortBy.DATE
                else -> SortBy.NAME
            }
        )
    }
    var sortOrder by remember { 
        mutableStateOf(
            if (preferencesManager.getVideoListSortOrder() == "DESCENDING") 
                SortOrder.DESCENDING 
            else 
                SortOrder.ASCENDING
        )
    }
    
    var showSortMenu by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // 当排序改变时，保存到 SharedPreferences
    LaunchedEffect(sortBy) {
        val sortByName = when (sortBy) {
            SortBy.NAME -> "NAME"
            SortBy.SIZE -> "SIZE"
            SortBy.DURATION -> "DURATION"
            SortBy.DATE -> "DATE"
        }
        preferencesManager.setVideoListSortBy(sortByName)
    }
    
    LaunchedEffect(sortOrder) {
        val orderName = when (sortOrder) {
            SortOrder.ASCENDING -> "ASCENDING"
            SortOrder.DESCENDING -> "DESCENDING"
        }
        preferencesManager.setVideoListSortOrder(orderName)
    }

    // 排序后的视频列表
    val sortedVideoList = remember(videoList, sortBy, sortOrder) {
        val list = videoList.toMutableList()
        when (sortBy) {
            SortBy.NAME -> list.sortWith(NaturalOrderComparator.comparator { it.name })
            SortBy.SIZE -> list.sortBy { it.size }
            SortBy.DURATION -> list.sortBy { it.duration }
            SortBy.DATE -> list.sortBy { it.dateAdded }
        }
        if (sortOrder == SortOrder.DESCENDING) {
            list.reverse()
        }
        list
    }

    // 找到当前播放视频在排序后列表中的索引
    val currentIndex = remember(currentVideoUri, sortedVideoList) {
        sortedVideoList.indexOfFirst { it.uri == currentVideoUri.toString() }
    }

    val skipAnim = com.fam4k007.videoplayer.manager.compose.ComposeOverlayManager.globalDisableAnimations

    // 启动时触发动画
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    // 当排序改变或当前索引改变时，滚动到当前播放视频
    LaunchedEffect(currentIndex, sortBy, sortOrder) {
        if (currentIndex >= 0) {
            delay(100)
            listState.animateScrollToItem(currentIndex)
        }
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
                // 半透明背景层（高对比度，与字幕设置一致）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xCC121212), // 左边缘 80% 不透明
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
                                text = "Video List",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            
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
                                    DropdownMenuWithStyle(
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
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 视频计数和排序信息
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${sortedVideoList.size} videos",
                                fontSize = 13.sp,
                                color = Color(0xB3FFFFFF)
                            )
                            
                            Text(
                                text = getSortText(sortBy, sortOrder),
                                fontSize = 11.sp,
                                color = Color(0x99FFFFFF)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Divider(
                            color = Color(0x33FFFFFF),
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // 视频列表
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(sortedVideoList) { index, video ->
                                VideoListItem(
                                    video = video,
                                    index = index,
                                    isCurrentPlaying = index == currentIndex,
                                    onClick = {
                                        isVisible = false
                                        coroutineScope.launch {
                                            delay(200)
                                            onVideoSelected(video, index)
                                            onDismiss()
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

/**
 * 视频列表项
 */
@Composable
fun VideoListItem(
    video: VideoFileParcelable,
    index: Int,
    isCurrentPlaying: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isCurrentPlaying) {
        Color(0x4064B5F6)  // 当前播放：蓝色高亮
    } else {
        Color(0x1AFFFFFF)  // 普通项：半透明白色
    }

    val borderColor = if (isCurrentPlaying) {
        Color(0xFF64B5F6)  // 当前播放：蓝色边框
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .then(
                if (isCurrentPlaying) {
                    Modifier.then(
                        Modifier.background(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                    )
                } else {
                    Modifier
                }
            )
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 序号
                Text(
                    text = "${index + 1}.",
                    fontSize = 13.sp,
                    fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentPlaying) Color(0xFF64B5F6) else Color(0xB3FFFFFF),
                    modifier = Modifier.padding(end = 8.dp)
                )

                // 文件名
                Text(
                    text = video.name,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // 当前播放标记
                if (isCurrentPlaying) {
                    Text(
                        text = "Playing",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64B5F6),
                        modifier = Modifier
                            .background(
                                color = Color(0x3364B5F6),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 视频信息（时长、大小）
            if (video.duration > 0 || video.size > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (video.duration > 0) {
                        Text(
                            text = formatDuration(video.duration),
                            fontSize = 11.sp,
                            color = Color(0x99FFFFFF)
                        )
                    }
                    if (video.size > 0) {
                        Text(
                            text = formatFileSize(video.size),
                            fontSize = 11.sp,
                            color = Color(0x99FFFFFF)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 格式化时长（毫秒转为 HH:MM:SS 或 MM:SS）
 */
private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(sizeBytes.toDouble()) / log10(1024.0)).toInt()
    
    return String.format(
        "%.1f %s",
        sizeBytes / 1024.0.pow(digitGroups.toDouble()),
        units[digitGroups]
    )
}

/**
 * 获取排序文本描述
 */
private fun getSortText(sortBy: SortBy, sortOrder: SortOrder): String {
    val sortByText = when (sortBy) {
        SortBy.NAME -> "File Name"
        SortBy.SIZE -> "Size"
        SortBy.DURATION -> "Duration"
        SortBy.DATE -> "Date Added"
    }
    val orderText = when (sortOrder) {
        SortOrder.ASCENDING -> "↑"
        SortOrder.DESCENDING -> "↓"
    }
    return "$sortByText $orderText"
}

/**
 * 自定义样式的排序下拉菜单
 */
@Composable
fun DropdownMenuWithStyle(
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
                text = "Size",
                isSelected = sortBy == SortBy.SIZE,
                onClick = {
                    onSortByChange(SortBy.SIZE)
                    onDismissRequest()
                }
            )
            
            SortMenuItem(
                text = "Duration",
                isSelected = sortBy == SortBy.DURATION,
                onClick = {
                    onSortByChange(SortBy.DURATION)
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
                text = "Order",
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

/**
 * 排序菜单项
 */
@Composable
fun SortMenuItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    fontSize = 14.sp,
                    color = if (isSelected) Color(0xFF64B5F6) else Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                
                if (isSelected) {
                    Text(
                        text = "✓",
                        fontSize = 14.sp,
                        color = Color(0xFF64B5F6),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        onClick = onClick,
        modifier = Modifier.background(
            if (isSelected) Color(0x1A64B5F6) else Color.Transparent
        )
    )
}
