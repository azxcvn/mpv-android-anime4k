package com.fam4k007.videoplayer.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.AppConstants
import com.fam4k007.videoplayer.VideoFileParcelable
import com.fam4k007.videoplayer.database.PlaybackState
import com.fam4k007.videoplayer.database.VideoDatabase
import com.fam4k007.videoplayer.presentation.LibraryViewModel
import com.fam4k007.videoplayer.utils.ThumbnailCacheManager
import com.fam4k007.videoplayer.utils.FileOperationManager
import com.fam4k007.videoplayer.utils.FormatUtils
import com.fam4k007.videoplayer.ui.components.BatchDeleteConfirmDialog
import com.fam4k007.videoplayer.ui.components.CopyDestinationDialog
import com.fam4k007.videoplayer.ui.components.DeleteConfirmDialog
import com.fam4k007.videoplayer.ui.components.EmptyState
import com.fam4k007.videoplayer.ui.components.MultiSelectActionBar
import com.fam4k007.videoplayer.ui.components.RenameDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoListScreen(
    folderName: String,
    folderPath: String,
    preloadedVideos: List<VideoFileParcelable>? = null,  // 预加载的视频列表（flat模式）
    onNavigateBack: () -> Unit,
    onOpenVideo: (VideoFileParcelable, Int, List<VideoFileParcelable>) -> Unit,
    onOpenMediaInfo: (VideoFileParcelable) -> Unit = {},
    viewModel: LibraryViewModel = koinViewModel(),
    preferencesManager: com.fam4k007.videoplayer.preferences.PreferencesManager = koinInject()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 观察 ViewModel 状态
    val videoListState by viewModel.videoListState.collectAsState()
    
    var showSearch by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<VideoFileParcelable?>(null) }
    
    // 文件操作菜单相关状态
    var selectedVideoForOperation by remember { mutableStateOf<VideoFileParcelable?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    
    // 多选编辑模式
    var isEditMode by remember { mutableStateOf(false) }
    var selectedVideos by remember { mutableStateOf<Set<VideoFileParcelable>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    
    // 初始化加载
    LaunchedEffect(folderPath, preloadedVideos) {
        if (preloadedVideos != null) {
            // 如果有预加载的视频列表（flat模式），直接使用
            viewModel.setVideos(preloadedVideos)
        } else if (videoListState.videos.isEmpty()) {
            // 否则从文件夹扫描（folder模式）
            viewModel.scanVideosInFolder(folderPath)
        }
    }
    
    // 加载播放进度数据（屏幕恢复时自动刷新）
    var playbackStates by remember { mutableStateOf<Map<String, PlaybackState>>(emptyMap()) }
    var watchedThreshold by remember { mutableStateOf(AppConstants.Defaults.DEFAULT_WATCHED_THRESHOLD) }
    var showProgressBar by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    LaunchedEffect(videoListState.filteredVideos, refreshTrigger) {
        val states = withContext(Dispatchers.IO) {
            val db = VideoDatabase.getDatabase(context)
            db.playbackHistoryDao().getAllPlaybackStates().associateBy { it.uri }
        }
        playbackStates = states
        watchedThreshold = preferencesManager.getWatchedThreshold()
        showProgressBar = preferencesManager.isShowVideoProgressBarEnabled()
    }

    Scaffold(
        topBar = {
            if (showSearch) {
                SearchTopBar(
                    searchQuery = videoListState.searchQuery,
                    onSearchQueryChange = { viewModel.searchVideos(it) },
                    onCloseSearch = {
                        showSearch = false
                        viewModel.clearSearch()
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = folderPath.substringAfterLast('/'),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                velocity = 30.dp
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            isEditMode = !isEditMode
                            if (!isEditMode) {
                                selectedVideos = emptySet()
                            }
                        }) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Close else Icons.Default.Edit,
                                contentDescription = if (isEditMode) "退出编辑" else "编辑"
                            )
                        }
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索"
                            )
                        }
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "排序"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (videoListState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (videoListState.filteredVideos.isEmpty()) {
                EmptyState(if (videoListState.searchQuery.isEmpty()) "此文件夹中没有视频" else "未找到匹配的视频")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        if (videoListState.isRefreshing) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    itemsIndexed(videoListState.filteredVideos) { index, video ->
                        VideoItem(
                            video = video,
                            isSelected = video == selectedVideoForOperation,
                            isEditMode = isEditMode,
                            isChecked = selectedVideos.contains(video),
                            playbackState = playbackStates[video.uri],
                            watchedThreshold = watchedThreshold,
                            showProgressBar = showProgressBar,
                            displayFields = videoListState.displayFields,
                            onClick = { 
                                if (isEditMode) {
                                    // 编辑模式下切换选中状态
                                    selectedVideos = if (selectedVideos.contains(video)) {
                                        selectedVideos - video
                                    } else {
                                        selectedVideos + video
                                    }
                                } else {
                                    // 正常模式下打开视频
                                    onOpenVideo(video, index, videoListState.filteredVideos)
                                }
                            },
                            onMoreClick = { 
                                if (!isEditMode) selectedVideo = video 
                            }
                        )
                    }
                }
                
                // 多选操作栏
                MultiSelectActionBar(
                    visible = isEditMode && selectedVideos.isNotEmpty(),
                    selectedCount = selectedVideos.size,
                    totalCount = videoListState.filteredVideos.size,
                    onSelectAll = {
                        if (selectedVideos.size == videoListState.filteredVideos.size) {
                            // 取消全选
                            selectedVideos = emptySet()
                        } else {
                            // 全选
                            selectedVideos = videoListState.filteredVideos.toSet()
                        }
                    },
                    onRename = {
                        if (selectedVideos.size == 1) {
                            selectedVideoForOperation = selectedVideos.first()
                            showRenameDialog = true
                        }
                    },
                    onDelete = {
                        showBatchDeleteDialog = true
                    },
                    onCopy = {
                        showCopyDialog = true
                    },
                    onCancel = {
                        isEditMode = false
                        selectedVideos = emptySet()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                )
                
                // 刷新按钮（编辑模式时隐藏）
                AnimatedVisibility(
                    visible = !isEditMode,
                    enter = scaleIn(
                        initialScale = 0.3f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(animationSpec = tween(200)),
                    exit = scaleOut(
                        targetScale = 0.3f,
                        animationSpec = tween(200)
                    ) + fadeOut(animationSpec = tween(200)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.refreshVideos(folderPath) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        VideoSortDialog(
            currentSortType = when (videoListState.sortType) {
                0 -> "NAME"
                1 -> "DATE"
                2 -> "SIZE"
                3 -> "DURATION"
                else -> "NAME"
            },
            currentSortOrder = when (videoListState.sortOrder) {
                0 -> "ASCENDING"
                1 -> "DESCENDING"
                else -> "ASCENDING"
            },
            currentFields = videoListState.displayFields,
            onDismiss = { showSortDialog = false },
            onSortSelected = { newType: String, newOrder: String ->
                // 将字符串类型映射为整数
                val sortType = when (newType) {
                    "NAME" -> 0
                    "DATE" -> 1
                    "SIZE" -> 2
                    "DURATION" -> 3
                    else -> 0
                }
                val sortOrder = when (newOrder) {
                    "ASCENDING" -> 0
                    "DESCENDING" -> 1
                    else -> 0
                }
                viewModel.sortVideos(sortType, sortOrder)
            },
            onFieldsChanged = { fields ->
                viewModel.setVideoDisplayFields(fields)
            }
        )
    }

    // 监听selectedVideo变化，打开媒体信息
    LaunchedEffect(selectedVideo) {
        selectedVideo?.let { video ->
            onOpenMediaInfo(video)
            selectedVideo = null // 重置状态
        }
    }
    
    // 重命名对话框
    if (showRenameDialog && selectedVideoForOperation != null) {
        RenameDialog(
            visible = true,
            currentName = selectedVideoForOperation!!.name,
            onDismiss = { 
                showRenameDialog = false
                selectedVideoForOperation = null
            },
            onConfirm = { newName ->
                lifecycleOwner.lifecycleScope.launch {
                    val videoPath = selectedVideoForOperation!!.path
                    val newPath = FileOperationManager.rename(context, videoPath, newName)
                    if (newPath != null) {
                        // 刷新列表
                        viewModel.refreshVideos(folderPath)
                    }
                    showRenameDialog = false
                    selectedVideoForOperation = null
                }
            }
        )
    }
    
    // 删除确认对话框
    if (showDeleteDialog && selectedVideoForOperation != null) {
        DeleteConfirmDialog(
            visible = true,
            fileName = selectedVideoForOperation!!.name,
            isFolder = false,
            onDismiss = { 
                showDeleteDialog = false
                selectedVideoForOperation = null
            },
            onConfirm = {
                lifecycleOwner.lifecycleScope.launch {
                    val videoPath = selectedVideoForOperation!!.path
                    val success = FileOperationManager.delete(
                        context,
                        videoPath,
                        isFolder = false
                    )
                    if (success) {
                        // 刷新列表
                        viewModel.refreshVideos(folderPath)
                    }
                    showDeleteDialog = false
                    selectedVideoForOperation = null
                }
            }
        )
    }
    
    // 复制对话框
    if (showCopyDialog && selectedVideoForOperation != null) {
        CopyDestinationDialog(
            visible = true,
            fileName = selectedVideoForOperation!!.name,
            onDismiss = { 
                showCopyDialog = false
                selectedVideoForOperation = null
            },
            onConfirm = { destPath ->
                lifecycleOwner.lifecycleScope.launch {
                    val sourcePath = selectedVideoForOperation!!.path
                    val sourceFile = File(sourcePath)
                    val fullDestPath = File(destPath, sourceFile.name).absolutePath
                    val success = FileOperationManager.copy(
                        context,
                        sourcePath,
                        fullDestPath,
                        isFolder = false
                    )
                    if (success) {
                        // 刷新列表
                        viewModel.refreshVideos(folderPath)
                    }
                    showCopyDialog = false
                    selectedVideoForOperation = null
                }
            }
        )
    }
    
    // 批量删除确认对话框
    if (showBatchDeleteDialog && selectedVideos.isNotEmpty()) {
        BatchDeleteConfirmDialog(
            visible = true,
            count = selectedVideos.size,
            isFolder = false,
            onDismiss = { showBatchDeleteDialog = false },
            onConfirm = {
                lifecycleOwner.lifecycleScope.launch {
                    selectedVideos.forEach { video ->
                        FileOperationManager.delete(
                            context,
                            video.path,
                            isFolder = false
                        )
                    }
                    // 刷新列表
                    viewModel.refreshVideos(folderPath)
                    selectedVideos = emptySet()
                    isEditMode = false
                    showBatchDeleteDialog = false
                }
            }
        )
    }
    
    // 多选复制对话框
    if (showCopyDialog && (selectedVideos.isNotEmpty() || selectedVideoForOperation != null)) {
        val fileName = when {
            selectedVideos.size == 1 -> selectedVideos.first().name
            selectedVideos.size > 1 -> "${selectedVideos.size} 个文件"
            else -> selectedVideoForOperation?.name ?: ""
        }
        
        CopyDestinationDialog(
            visible = true,
            fileName = fileName,
            onDismiss = { 
                showCopyDialog = false
                selectedVideoForOperation = null
            },
            onConfirm = { destPath ->
                lifecycleOwner.lifecycleScope.launch {
                    val videosToCode = if (selectedVideos.isNotEmpty()) {
                        selectedVideos.toList()
                    } else {
                        listOfNotNull(selectedVideoForOperation)
                    }
                    
                    videosToCode.forEach { video ->
                        val sourcePath = video.path
                        val sourceFile = File(sourcePath)
                        val fullDestPath = File(destPath, sourceFile.name).absolutePath
                        FileOperationManager.copy(
                            context,
                            sourcePath,
                            fullDestPath,
                            isFolder = false
                        )
                    }
                    
                    // 刷新列表
                    viewModel.refreshVideos(folderPath)
                    showCopyDialog = false
                    selectedVideoForOperation = null
                    selectedVideos = emptySet()
                    isEditMode = false
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun VideoItem(
    video: VideoFileParcelable,
    isSelected: Boolean,
    isEditMode: Boolean,
    isChecked: Boolean,
    playbackState: PlaybackState?,
    watchedThreshold: Int,
    showProgressBar: Boolean,
    displayFields: Set<String>,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var thumbnailBitmap by remember(video.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(video.uri) {
        thumbnailBitmap = null
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cacheManager = ThumbnailCacheManager.getInstance(context)
                val bitmap = cacheManager.getThumbnail(context, Uri.parse(video.uri), video.duration)
                withContext(Dispatchers.Main) {
                    thumbnailBitmap = bitmap
                }
            } catch (e: Exception) { }
        }
    }
    
    // 计算观看状态
    val progressPercent = if (playbackState != null && playbackState.duration > 0) {
        ((playbackState.position.toFloat() / playbackState.duration) * 100).toInt().coerceIn(0, 100)
    } else 0
    val isWatched = playbackState?.hasBeenWatched == true || (progressPercent >= watchedThreshold && progressPercent > 0)
    val isWatching = progressPercent in 1 until watchedThreshold
    val showBar = showProgressBar && progressPercent in 1..99

    // 显示字段开关
    val showSize = "SIZE" in displayFields && video.size > 0
    val showDuration = "DURATION" in displayFields && video.duration > 0
    val showProgress = "PROGRESS" in displayFields
    val showDate = "DATE" in displayFields && video.dateAdded > 0
    val showResolution = "RESOLUTION" in displayFields && video.width > 0 && video.height > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected && !isEditMode) 
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) 
            else 
                MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isSelected && !isEditMode) 
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
        else 
            null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 复选框（编辑模式）
            AnimatedVisibility(
                visible = isEditMode,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(250))
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            
            // 缩略图（带进度条和时长浮层）
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    AsyncImage(
                        model = thumbnailBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // 进度条（底部，3dp高）
                if (showBar) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressPercent / 100f)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                
                // 时长浮层（右下角）
                if (showDuration) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 3.dp, bottom = if (showBar) 4.dp else 1.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = FormatUtils.formatDuration(video.duration),
                            fontSize = 9.sp,
                            color = Color.White,
                            maxLines = 1,
                            lineHeight = 14.sp
                        )
                    }
                }

                // 大小浮层（左下角）
                if (showSize) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 3.dp, bottom = if (showBar) 4.dp else 1.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = FormatUtils.formatFileSize(video.size),
                            fontSize = 9.sp,
                            color = Color.White,
                            maxLines = 1,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 视频信息（随字段行数动态变化，缩略图整体垂直居中）
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // 标题
                Text(
                    text = video.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isWatched)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )

                // 字段标签：观看进度 / 日期 / 分辨率
                if (showProgress || showDate || showResolution) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 观看进度
                        if (showProgress) {
                            VideoFieldTag(
                                icon = Icons.Default.Schedule,
                                text = when {
                                    isWatched -> "已观看"
                                    isWatching -> "观看中 ${progressPercent}%"
                                    else -> "未观看"
                                },
                                color = when {
                                    isWatched -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    isWatching -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        // 日期
                        if (showDate) {
                            VideoFieldTag(
                                icon = Icons.Default.CalendarToday,
                                text = FormatUtils.formatDateOnly(video.dateAdded * 1000),
                                color = if (isWatched)
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 分辨率
                        if (showResolution) {
                            VideoFieldTag(
                                icon = Icons.Default.AspectRatio,
                                text = "${video.width}x${video.height}",
                                color = if (isWatched)
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 更多按钮
            if (!isEditMode) {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多信息",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 视频字段标签（左侧小图标 + 文本）
 */
@Composable
private fun VideoFieldTag(
    icon: ImageVector,
    text: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            maxLines = 1,
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit
) {
    val textStyle = TextStyle(
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    TopAppBar(
        title = {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "搜索视频...",
                                style = textStyle.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            )
                        }
                        innerTextField()
                    }
                },
                singleLine = true
            )
        },
        navigationIcon = {
            IconButton(onClick = onCloseSearch) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
        },
        actions = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清除"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun VideoSortDialog(
    currentSortType: String,
    currentSortOrder: String,
    currentFields: Set<String>,
    onDismiss: () -> Unit,
    onSortSelected: (String, String) -> Unit,
    onFieldsChanged: (Set<String>) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "排序与显示",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 排序方式
                VideoSectionTitle("排序方式")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoSortField.entries.forEach { field ->
                        FilterChip(
                            selected = currentSortType == field.key,
                            onClick = { onSortSelected(field.key, currentSortOrder) },
                            label = { Text(field.label) }
                        )
                    }
                }

                // 排序方向
                VideoSectionTitle("排序方向")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentSortOrder == "ASCENDING",
                        onClick = { onSortSelected(currentSortType, "ASCENDING") },
                        label = { Text("升序") }
                    )
                    FilterChip(
                        selected = currentSortOrder == "DESCENDING",
                        onClick = { onSortSelected(currentSortType, "DESCENDING") },
                        label = { Text("降序") }
                    )
                }

                // 显示字段
                VideoSectionTitle("显示字段")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoDisplayField.entries.forEach { field ->
                        FilterChip(
                            selected = field.key in currentFields,
                            onClick = {
                                val next = if (field.key in currentFields) {
                                    currentFields - field.key
                                } else {
                                    currentFields + field.key
                                }
                                onFieldsChanged(next)
                            },
                            label = { Text(field.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/** 视频排序字段选项 */
private enum class VideoSortField(val key: String, val label: String) {
    NAME("NAME", "名称"),
    DATE("DATE", "日期"),
    SIZE("SIZE", "大小"),
    DURATION("DURATION", "时长")
}

/** 视频显示字段选项 */
private enum class VideoDisplayField(val key: String, val label: String) {
    DURATION("DURATION", "时长"),
    SIZE("SIZE", "大小"),
    DATE("DATE", "日期"),
    RESOLUTION("RESOLUTION", "分辨率"),
    PROGRESS("PROGRESS", "观看进度")
}

@Composable
private fun VideoSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

