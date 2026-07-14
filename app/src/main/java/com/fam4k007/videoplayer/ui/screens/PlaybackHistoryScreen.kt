package com.fam4k007.videoplayer.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fam4k007.videoplayer.database.PlaybackHistoryEntity
import com.fam4k007.videoplayer.presentation.PlaybackHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackHistoryScreen(
    viewModel: PlaybackHistoryViewModel,
    onBack: () -> Unit,
    onPlayVideo: (Uri, Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<PlaybackHistoryEntity?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary

    // 显示错误信息
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // 显示错误后自动清除
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playback History", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.historyList.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear All")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    // 加载状态
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.historyList.isEmpty() -> {
                    // 空状态
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = primaryColor.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No playback history",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    // 历史列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.historyList,
                            key = { it.uri }
                        ) { item ->
                            HistoryCard(
                                item = item,
                                onClick = { onPlayVideo(Uri.parse(item.uri), item.position) },
                                onDeleteClick = { itemToDelete = item }
                            )
                        }
                    }
                }
            }

            // 错误提示 Snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(error)
                }
            }
        }
    }

    // 清空全部对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear History",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Clear all playback history? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    // 删除单条对话框
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    text = "Delete Record",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Delete playback record for ${item.fileName}?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteHistory(item.uri)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("取消")
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun HistoryCard(
    item: PlaybackHistoryEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧缩略图
            Box(
                modifier = Modifier
                    .size(90.dp, 60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                // 直接从视频 URI 提取缩略图，传入播放位置
                VideoThumbnail(
                    videoUri = item.uri,
                    positionMs = item.position,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 中间信息 - 使用 Box 实现精确对齐
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp) // 与缩略图同高
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 顶部：视频标题，与缩略图顶部对齐
                    Text(
                        text = item.fileName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // 底部：标签，与缩略图底部对齐
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 播放进度标签
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = formatDuration(item.position),
                                fontSize = 9.sp,
                                color = primaryColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // 最后播放日期标签
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = formatTimestamp(item.lastPlayed),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 右侧删除按钮
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * 格式化播放时长
 */
private fun formatDuration(positionMs: Long): String {
    val seconds = positionMs / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("Played %02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("Played %02d:%02d", minutes, secs)
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestampMs: Long): String {
    val dateFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return dateFormat.format(java.util.Date(timestampMs))
}

/**
 * 视频缩略图组件 - 使用缓存管理器优化
 * 优化版：磁盘缓存，避免重复提取，大幅提升性能
 */
@Composable
private fun VideoThumbnail(
    videoUri: String,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnailManager = remember { com.fam4k007.videoplayer.utils.ThumbnailCacheManager.getInstance(context) }
    
    // 使用 produceState 异步加载缓存的缩略图
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, videoUri, positionMs) {
        value = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(videoUri)
                // 使用缓存管理器获取缩略图（带磁盘缓存）
                val (thumbnail, _) = thumbnailManager.getThumbnailAtPosition(context, uri, positionMs)
                thumbnail
            } catch (e: Exception) {
                android.util.Log.e("VideoThumbnail", "加载缩略图失败: ${e.message}", e)
                null
            }
        }
    }
    
    // 显示缓存的缩略图或占位图
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        // 加载中或失败时显示占位图
        Icon(
            painter = painterResource(android.R.drawable.ic_media_play),
            contentDescription = null,
            modifier = modifier.padding(20.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    }
}
