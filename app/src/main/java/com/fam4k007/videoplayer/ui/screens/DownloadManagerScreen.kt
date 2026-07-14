package com.fam4k007.videoplayer.ui.screens

import android.app.Application
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fam4k007.videoplayer.download.DownloadItem
import com.fam4k007.videoplayer.download.DownloadTaskStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 下载管理器 ViewModel
 */
class DownloadManagerViewModel(application: Application) : AndroidViewModel(application) {

    val downloadItems: StateFlow<List<DownloadItem>> = DownloadTaskStore.downloadItems

    fun pauseDownload(item: DownloadItem) {
        // 使用共享存储取消协程和网络请求
        DownloadTaskStore.pauseDownload(item.id)
    }

    fun resumeDownload(item: DownloadItem) {
        // 通过共享回调触发真正的恢复下载逻辑
        DownloadTaskStore.onResumeDownload?.invoke(item)
    }

    fun deleteDownload(item: DownloadItem, deleteFile: Boolean = false) {
        if (deleteFile && item.filePath != null) {
            try {
                val file = java.io.File(item.filePath)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
        // removeItem 内部会取消关联的Job和Call
        DownloadTaskStore.removeItem(item.id)
    }

    fun clearCompleted(deleteFiles: Boolean = false) {
        DownloadTaskStore.clearCompleted(deleteFiles)
    }

    fun clearAll(deleteFiles: Boolean = false) {
        DownloadTaskStore.clearAll(deleteFiles)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    viewModel: DownloadManagerViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val downloadItems by viewModel.downloadItems.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<DownloadItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Download Manager", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (downloadItems.isNotEmpty()) {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            shadowElevation = 4.dp
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Clear Completed",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.clearCompleted(deleteFiles = false)
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Clear All",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showClearDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (downloadItems.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No download tasks",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Parse a URL in the video download page to start",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 统计信息
                item {
                    val activeCount = downloadItems.count { it.status in listOf("downloading", "pending", "merging") }
                    val completedCount = downloadItems.count { it.status == "completed" }
                    val pausedCount = downloadItems.count { it.status == "paused" }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatChip("Downloading", activeCount, MaterialTheme.colorScheme.primary)
                        StatChip("Completed", completedCount, MaterialTheme.colorScheme.tertiary)
                        StatChip("Paused", pausedCount, MaterialTheme.colorScheme.secondary)
                    }
                }

                items(
                    items = downloadItems,
                    key = { it.id }
                ) { item ->
                    DownloadManagerItemCard(
                        item = item,
                        onPause = { viewModel.pauseDownload(item) },
                        onResume = { viewModel.resumeDownload(item) },
                        onDelete = { showDeleteDialog = it }
                    )
                }

                // 底部间距
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // 清除全部对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text("Clear All", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Also delete downloaded local files?")
                    Spacer(modifier = Modifier.height(16.dp))
                    // 操作选项放在内容区，避免与取消按钮重叠
                    Button(
                        onClick = {
                            viewModel.clearAll(deleteFiles = false)
                            showClearDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Text("Clear Records Only", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.clearAll(deleteFiles = true)
                            showClearDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text("Delete Files & Clear", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 删除单个任务对话框
    showDeleteDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text("Delete Task", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Also delete downloaded local files?")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.deleteDownload(item, deleteFile = false)
                            showDeleteDialog = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Text("Remove Record Only", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.deleteDownload(item, deleteFile = true)
                            showDeleteDialog = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text("Delete File & Remove", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatChip(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$count",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = color
            )
        }
    }
}

@Composable
private fun DownloadManagerItemCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: (DownloadItem) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val statusText = when (item.status) {
        "pending" -> "Pending"
        "downloading" -> "Downloading"
        "paused" -> "Paused"
        "completed" -> "Completed"
        "failed" -> "Failed"
        "merging" -> "Merging"
        "cancelled" -> "Cancelled"
        else -> item.status
    }
    val statusColor = when (item.status) {
        "completed" -> MaterialTheme.colorScheme.primary
        "failed" -> MaterialTheme.colorScheme.error
        "downloading" -> MaterialTheme.colorScheme.tertiary
        "merging" -> MaterialTheme.colorScheme.secondary
        "paused" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态图标
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (item.status) {
                                "completed" -> Icons.Default.CheckCircle
                                "failed" -> Icons.Default.Error
                                "paused" -> Icons.Default.PauseCircle
                                "downloading", "merging" -> Icons.Default.Download
                                else -> Icons.Default.HourglassEmpty
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            fontSize = 12.sp
                        )
                        if (item.status == "downloading" && item.progress > 0) {
                            Text(
                                text = " · ${item.progress}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 操作按钮
                if (item.status == "downloading" || item.status == "merging") {
                    IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (item.status == "paused") {
                    IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { onDelete(item) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 进度条
            if (item.status in listOf("downloading", "pending", "paused", "merging")) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // 错误消息
            item.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "⚠ $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
