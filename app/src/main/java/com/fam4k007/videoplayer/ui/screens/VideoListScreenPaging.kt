package com.fam4k007.videoplayer.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.VideoFileParcelable
import com.fam4k007.videoplayer.database.VideoDatabase
import com.fam4k007.videoplayer.mediainfo.MediaInfoActivity
import com.fam4k007.videoplayer.paging.VideoPagingSource
import com.fam4k007.videoplayer.utils.ThumbnailCacheManager
import com.fam4k007.videoplayer.utils.FileOperationManager
import com.fam4k007.videoplayer.ui.components.BatchDeleteConfirmDialog
import com.fam4k007.videoplayer.ui.components.CopyDestinationDialog
import com.fam4k007.videoplayer.ui.components.DeleteConfirmDialog
import com.fam4k007.videoplayer.ui.components.EmptyState
import com.fam4k007.videoplayer.ui.components.FileOperationMenu
import com.fam4k007.videoplayer.ui.components.MultiSelectActionBar
import com.fam4k007.videoplayer.ui.components.RenameDialog
import com.fam4k007.videoplayer.ui.components.SortOption
import com.fam4k007.videoplayer.utils.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 使用Paging3的视频列表界面
 * 防止大量视频导致OOM
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoListScreenPaging(
    folderName: String,
    folderPath: String,
    onNavigateBack: () -> Unit,
    onOpenVideo: (VideoFileParcelable, List<VideoFileParcelable>) -> Unit,
    onRescanFolder: (() -> Unit) -> Unit,
    preferencesManager: com.fam4k007.videoplayer.preferences.PreferencesManager,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 排序状态
    var sortType by remember { mutableStateOf(preferencesManager.getVideoSortType()) }
    var sortOrder by remember { mutableStateOf(preferencesManager.getVideoSortOrder()) }
    
    // Paging数据流 - 依赖于sortType和sortOrder
    val pager = remember(folderPath, sortType, sortOrder) {
        Pager(
            config = PagingConfig(
                pageSize = VideoPagingSource.PAGE_SIZE,
                prefetchDistance = 10,
                enablePlaceholders = false,
                initialLoadSize = VideoPagingSource.PAGE_SIZE
            ),
            pagingSourceFactory = {
                VideoPagingSource(
                    dao = VideoDatabase.getDatabase(context).videoCacheDao(),
                    folderPath = folderPath,
                    sortType = sortType,
                    sortOrder = sortOrder
                )
            }
        ).flow.cachedIn(coroutineScope)
    }
    
    val lazyPagingItems = pager.collectAsLazyPagingItems()
    
    var showSearch by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<VideoFileParcelable?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    // 文件操作菜单相关状态
    var selectedVideoForOperation by remember { mutableStateOf<VideoFileParcelable?>(null) }
    var showOperationMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // 多选编辑模式
    var isEditMode by remember { mutableStateOf(false) }
    var selectedVideos by remember { mutableStateOf<Set<VideoFileParcelable>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    
    // 当需要刷新时
    fun refreshVideos() {
        isRefreshing = true
        onRescanFolder {
            lazyPagingItems.refresh()
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            if (showSearch) {
                SearchTopBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onCloseSearch = {
                        showSearch = false
                        searchQuery = ""
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            folderName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
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
                                contentDescription = if (isEditMode) "退出编辑" else "编辑",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "排序",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // 根据搜索关键词过滤显示
            val filteredItems = if (searchQuery.isEmpty()) {
                lazyPagingItems
            } else {
                // 搜索时需要过滤，但Paging不支持直接过滤，这里显示提示
                null
            }
            
            if (filteredItems == null || (searchQuery.isNotEmpty() && lazyPagingItems.itemCount == 0)) {
                EmptyState("搜索功能需要在数据库层面实现，请使用排序功能")
            } else if (lazyPagingItems.itemCount == 0) {
                EmptyState("此文件夹中没有视频")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        if (isRefreshing) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    
                    // 使用Paging3的items扩展
                    items(
                        count = lazyPagingItems.itemCount,
                        key = lazyPagingItems.itemKey { it.uri }
                    ) { index ->
                        val video = lazyPagingItems[index]
                        if (video != null) {
                            // 过滤搜索关键词
                            if (searchQuery.isEmpty() || video.name.contains(searchQuery, ignoreCase = true)) {
                                VideoItem(
                                    video = video,
                                    isSelected = video == selectedVideoForOperation,
                                    isEditMode = isEditMode,
                                    isChecked = selectedVideos.contains(video),
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
                                            // 从数据库查询该文件夹的所有视频（使用当前排序方式）
                                            coroutineScope.launch {
                                                val allVideos = withContext(Dispatchers.IO) {
                                                    val dao = VideoDatabase.getDatabase(context).videoCacheDao()
                                                    val entities = when {
                                                        sortType == "NAME" && sortOrder == "ASCENDING" -> 
                                                            dao.getVideosByFolderSortedByNameAsc(folderPath)
                                                        sortType == "NAME" && sortOrder == "DESCENDING" -> 
                                                            dao.getVideosByFolderSortedByNameDesc(folderPath)
                                                        sortType == "DATE" && sortOrder == "ASCENDING" -> 
                                                            dao.getVideosByFolderSortedByDateAsc(folderPath)
                                                        sortType == "DATE" && sortOrder == "DESCENDING" -> 
                                                            dao.getVideosByFolderSortedByDateDesc(folderPath)
                                                        else -> 
                                                            dao.getVideosByFolderSortedByNameAsc(folderPath)
                                                    }
                                                    entities.map { entity ->
                                                        VideoFileParcelable(
                                                            uri = entity.uri,
                                                            name = entity.name,
                                                            path = entity.path,
                                                            size = entity.size,
                                                            duration = entity.duration,
                                                            dateAdded = entity.dateAdded
                                                        )
                                                    }
                                                }
                                                onOpenVideo(video, allVideos)
                                            }
                                        }
                                    },
                                    onMoreClick = { 
                                        if (!isEditMode) selectedVideo = video 
                                    },
                                    onLongClick = {
                                        if (!isEditMode) {
                                            selectedVideoForOperation = video
                                            showOperationMenu = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                // 文件操作菜单（全屏显示）
                FileOperationMenu(
                    visible = showOperationMenu && selectedVideoForOperation != null && !isEditMode,
                    fileName = selectedVideoForOperation?.name ?: "",
                    onDismiss = { 
                        showOperationMenu = false
                        selectedVideoForOperation = null
                    },
                    onRename = { 
                        showRenameDialog = true
                        showOperationMenu = false  // 关闭菜单
                    },
                    onDelete = { 
                        showDeleteDialog = true
                        showOperationMenu = false  // 关闭菜单
                    },
                    onCopy = { 
                        showCopyDialog = true
                        showOperationMenu = false  // 关闭菜单
                    }
                )
                
                // 多选操作栏
                MultiSelectActionBar(
                    visible = isEditMode && selectedVideos.isNotEmpty(),
                    selectedCount = selectedVideos.size,
                    totalCount = lazyPagingItems.itemCount,
                    onSelectAll = {
                        if (selectedVideos.size == lazyPagingItems.itemCount) {
                            // 取消全选
                            selectedVideos = emptySet()
                        } else {
                            // 全选当前加载的所有项
                            val allVideos = mutableSetOf<VideoFileParcelable>()
                            for (i in 0 until lazyPagingItems.itemCount) {
                                lazyPagingItems[i]?.let { allVideos.add(it) }
                            }
                            selectedVideos = allVideos
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
                        onClick = { refreshVideos() },
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
            currentSortType = sortType,
            currentSortOrder = sortOrder,
            onDismiss = { showSortDialog = false },
            onSortSelected = { newType, newOrder ->
                sortType = newType
                sortOrder = newOrder
                preferencesManager.setVideoSortType(newType)
                preferencesManager.setVideoSortOrder(newOrder)
                showSortDialog = false
            }
        )
    }

    // 监听selectedVideo变化，启动MediaInfoActivity
    LaunchedEffect(selectedVideo) {
        selectedVideo?.let { video ->
            MediaInfoActivity.start(context, video.uri, video.name)
            (context as? android.app.Activity)?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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
                        lazyPagingItems.refresh()
                    }
                    showRenameDialog = false
                    selectedVideoForOperation = null
                    // 退出编辑模式
                    if (isEditMode) {
                        selectedVideos = emptySet()
                        isEditMode = false
                    }
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
                        lazyPagingItems.refresh()
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
                        lazyPagingItems.refresh()
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
                    lazyPagingItems.refresh()
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
                    lazyPagingItems.refresh()
                    showCopyDialog = false
                    selectedVideoForOperation = null
                    if (isEditMode) {
                        selectedVideos = emptySet()
                        isEditMode = false
                    }
                }
            }
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
        color = Color.White,
        fontSize = 18.sp
    )
    TopAppBar(
        title = {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textStyle = textStyle,
                cursorBrush = SolidColor(Color.White),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchQuery.isEmpty()) {
                            Text(
                                "搜索视频...",
                                style = textStyle.copy(color = Color.White.copy(alpha = 0.6f))
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
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "关闭搜索",
                    tint = Color.White
                )
            }
        },
        actions = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清除",
                        tint = Color.White
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoItem(
    video: VideoFileParcelable,
    isSelected: Boolean,
    isEditMode: Boolean,
    isChecked: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(video.uri) {
        withContext(Dispatchers.IO) {
            try {
                val cacheManager = ThumbnailCacheManager.getInstance(context)
                val bitmap = cacheManager.getThumbnail(context, Uri.parse(video.uri), video.duration)
                withContext(Dispatchers.Main) {
                    thumbnailBitmap = bitmap
                }
            } catch (e: Exception) {
                // 忽略错误
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected && !isEditMode) 
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) 
            else 
                Color.White
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
                    onCheckedChange = null,  // 点击整行触发
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            
            // 缩略图
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE0E0E0)),
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
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 视频信息
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(68.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 标题（顶部对齐）
                Text(
                    text = video.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp
                )

                // 时长和大小标签（底部对齐）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = FormatUtils.formatFileSize(video.size),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FormatUtils.formatDuration(video.duration),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 更多按钮（非编辑模式）
            if (!isEditMode) {
                IconButton(onClick = onMoreClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}



@Composable
private fun VideoSortDialog(
    currentSortType: String,
    currentSortOrder: String,
    onDismiss: () -> Unit,
    onSortSelected: (String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "排序方式",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                SortOption(
                    text = "名称 (升序)",
                    isSelected = currentSortType == "NAME" && currentSortOrder == "ASCENDING",
                    onClick = { onSortSelected("NAME", "ASCENDING") }
                )
                SortOption(
                    text = "名称 (降序)",
                    isSelected = currentSortType == "NAME" && currentSortOrder == "DESCENDING",
                    onClick = { onSortSelected("NAME", "DESCENDING") }
                )
                SortOption(
                    text = "日期 (升序)",
                    isSelected = currentSortType == "DATE" && currentSortOrder == "ASCENDING",
                    onClick = { onSortSelected("DATE", "ASCENDING") }
                )
                SortOption(
                    text = "日期 (降序)",
                    isSelected = currentSortType == "DATE" && currentSortOrder == "DESCENDING",
                    onClick = { onSortSelected("DATE", "DESCENDING") }
                )
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

