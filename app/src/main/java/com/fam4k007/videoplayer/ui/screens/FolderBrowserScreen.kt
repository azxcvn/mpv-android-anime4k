package com.fam4k007.videoplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.VideoFile
import com.fam4k007.videoplayer.VideoFolder
import com.fam4k007.videoplayer.presentation.LibraryViewModel
import com.fam4k007.videoplayer.ui.components.BatchDeleteConfirmDialog
import com.fam4k007.videoplayer.ui.components.DeleteConfirmDialog
import com.fam4k007.videoplayer.ui.components.MultiSelectActionBar
import com.fam4k007.videoplayer.ui.components.EmptyState
import com.fam4k007.videoplayer.ui.components.RenameDialog
import com.fam4k007.videoplayer.ui.components.SortOption
import com.fam4k007.videoplayer.utils.FileOperationManager
import com.fam4k007.videoplayer.utils.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderBrowserScreen(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenFolder: (VideoFolder) -> Unit,
    viewModel: LibraryViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 观察 ViewModel 状态
    val folderListState by viewModel.folderListState.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val treeHasSubfolders by viewModel.treeHasSubfolders.collectAsState()
    
    var showSortDialog by remember { mutableStateOf(false) }
    
    // 文件操作菜单相关状态
    var selectedFolder by remember { mutableStateOf<VideoFolder?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // 多选编辑模式
    var isEditMode by remember { mutableStateOf(false) }
    var selectedFolders by remember { mutableStateOf<Set<VideoFolder>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    val isTreeView = viewMode == "TREE_VIEW"

    // 树状视图：分离父文件夹自身视频和子文件夹节点
    val allFolders = folderListState.folders
    val selfNode = remember(allFolders, isTreeView) {
        if (isTreeView) allFolders.firstOrNull { it.videos.isNotEmpty() } else null
    }
    val displayFolders = remember(allFolders, selfNode) {
        if (selfNode != null) allFolders.filter { it.videos.isEmpty() } else allFolders
    }

    // 面包屑滚动状态：进入子文件夹时自动滚到最右（显示最新路径）
    val breadcrumbScrollState = rememberScrollState()
    LaunchedEffect(breadcrumbs.size) {
        if (isTreeView && breadcrumbs.size > 1) {
            // 延迟一帧确保布局完成后再滚动
            delay(50)
            breadcrumbScrollState.animateScrollTo(breadcrumbScrollState.maxValue)
        }
    }

    // 树状视图：返回键逐级回退
    androidx.activity.compose.BackHandler(enabled = isTreeView && breadcrumbs.size > 1) {
        viewModel.treeNavigateBack()
    }

    // 初始化加载（仅作为后备，setViewMode/init 已负责主要加载，使用静默模式避免转圈）
    LaunchedEffect(hasPermission, viewMode) {
        if (hasPermission && !folderListState.isLoading) {
            if (isTreeView && folderListState.folders.isEmpty()) {
                viewModel.loadTreeRoot(silent = true)
            } else if (!isTreeView && folderListState.folders.isEmpty()) {
                viewModel.scanVideoFolders(silent = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isTreeView && breadcrumbs.size > 1) {
                        // 树状视图面包屑导航
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(breadcrumbScrollState),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            breadcrumbs.forEachIndexed { index, (path, name) ->
                                if (index > 0) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = name,
                                    fontSize = 15.sp,
                                    color = if (index == breadcrumbs.lastIndex)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (index == breadcrumbs.lastIndex) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { viewModel.navigateToBreadcrumb(index) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            "文件夹",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isTreeView && breadcrumbs.size > 1) {
                            viewModel.treeNavigateBack()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (hasPermission) {
                        // 视图模式切换按钮
                        IconButton(onClick = { 
                            val newMode = if (isTreeView) "FOLDER_VIEW" else "TREE_VIEW"
                            viewModel.setViewMode(newMode)
                            isEditMode = false
                            selectedFolders = emptySet()
                        }) {
                            Icon(
                                imageVector = if (isTreeView) Icons.Default.ViewList else Icons.Default.AccountTree,
                                contentDescription = if (isTreeView) "切换到文件夹视图" else "切换到树状视图"
                            )
                        }
                        // 编辑按钮
                        IconButton(onClick = { 
                            isEditMode = !isEditMode
                            if (!isEditMode) {
                                selectedFolders = emptySet()
                            }
                        }) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Close else Icons.Default.Edit,
                                contentDescription = if (isEditMode) "退出编辑" else "编辑"
                            )
                        }
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "排序"
                            )
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
                .padding(paddingValues)
        ) {
            when {
                !hasPermission -> {
                    PermissionPrompt(onRequestPermission)
                }
                folderListState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                folderListState.folders.isEmpty() -> {
                    EmptyState("未找到视频文件夹")
                }
                else -> {
                    // 用面包屑深度触发过渡动画（树状视图下）
                    // 使用 Pair 包含视图模式，确保模式切换时 key 变化方式不会触发滑入/滑出动画
                    val transitionKey = if (isTreeView) Pair(1, breadcrumbs.size) else Pair(0, 0)
                    AnimatedContent(
                        targetState = transitionKey,
                        transitionSpec = {
                            val (prevMode, prevLevel) = initialState
                            val (currMode, currLevel) = targetState
                            if (prevMode != currMode) {
                                // 模式切换：仅淡入淡出，不播滑入滑出
                                fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                            } else {
                                val direction = currLevel - prevLevel
                                if (direction > 0) {
                                    // 进入子级：新内容从右侧滑入覆盖，旧内容原地消失
                                    (slideInHorizontally { it } + fadeIn(tween(250))) togetherWith
                                    fadeOut(tween(1))
                                } else if (direction < 0) {
                                    // 返回父级：当前页面（上层）渐变淡出并向右滑，露出底部的父级页面
                                    (slideInHorizontally { it / 3 } + fadeIn(tween(300))) togetherWith
                                    (slideOutHorizontally { it } + fadeOut(tween(300)))
                                } else {
                                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                                }
                            }
                        },
                        label = "folderTransition"
                    ) { _ ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    if (folderListState.isRefreshing) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }
                                // 树状视图：父文件夹自身的视频文件
                                if (selfNode != null) {
                                    item {
                                        Text(
                                            text = "视频文件",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                    items(selfNode.videos, key = { it.uri }) { video ->
                                        TreeVideoItem(
                                            video = video,
                                            onClick = {
                                                onOpenFolder(selfNode)
                                            }
                                        )
                                    }
                                    // 子文件夹分隔
                                    if (displayFolders.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "子文件夹",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }
                                }
                                items(displayFolders) { folder ->
                                    FolderItem(
                                        folder = folder,
                                        isSelected = folder == selectedFolder,
                                        isEditMode = isEditMode,
                                        isChecked = selectedFolders.contains(folder),
                                        onClick = { 
                                            if (isEditMode) {
                                                selectedFolders = if (selectedFolders.contains(folder)) {
                                                    selectedFolders - folder
                                                } else {
                                                    selectedFolders + folder
                                                }
                                            } else if (isTreeView && treeHasSubfolders.contains(folder.folderPath)) {
                                                // 树状视图：有子文件夹的节点 → 导航进入
                                                viewModel.navigateToTreeFolder(folder.folderPath, folder.folderName)
                                            } else {
                                                // 叶子节点或文件夹视图 → 打开视频列表
                                                onOpenFolder(folder)
                                            }
                                        }
                                    )
                                }
                            }
                            
                            // 多选操作栏
                            MultiSelectActionBar(
                                visible = isEditMode && selectedFolders.isNotEmpty(),
                                selectedCount = selectedFolders.size,
                                totalCount = displayFolders.size,
                                onSelectAll = {
                                    if (selectedFolders.size == displayFolders.size) {
                                        selectedFolders = emptySet()
                                    } else {
                                        selectedFolders = displayFolders.toSet()
                                    }
                                },
                                onRename = {
                                    if (selectedFolders.size == 1) {
                                        selectedFolder = selectedFolders.first()
                                        showRenameDialog = true
                                    }
                                },
                                onDelete = {
                                    showBatchDeleteDialog = true
                                },
                                onCopy = null,
                                onCancel = {
                                    isEditMode = false
                                    selectedFolders = emptySet()
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
                                    onClick = {
                                        if (isTreeView) viewModel.refreshTreeView()
                                        else viewModel.refreshFolders()
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Icon(Icons.Default.Refresh, "刷新")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        SortDialog(
            currentSortType = when (folderListState.sortType) {
                0 -> "NAME"
                2 -> "VIDEO_COUNT"
                else -> "NAME"
            },
            currentSortOrder = when (folderListState.sortOrder) {
                0 -> "ASCENDING"
                1 -> "DESCENDING"
                else -> "ASCENDING"
            },
            onDismiss = { showSortDialog = false },
            onSortSelected = { newType: String, newOrder: String ->
                // 将字符串类型映射为整数
                val sortType = when (newType) {
                    "NAME" -> 0
                    "VIDEO_COUNT" -> 2
                    else -> 0
                }
                val sortOrder = when (newOrder) {
                    "ASCENDING" -> 0
                    "DESCENDING" -> 1
                    else -> 0
                }
                viewModel.sortFolders(sortType, sortOrder)
                showSortDialog = false
            }
        )
    }
    
    // 重命名对话框
    if (showRenameDialog && selectedFolder != null) {
        RenameDialog(
            visible = true,
            currentName = selectedFolder!!.folderName,
            onDismiss = { 
                showRenameDialog = false
                selectedFolder = null
            },
            onConfirm = { newName ->
                lifecycleOwner.lifecycleScope.launch {
                    val oldPath = selectedFolder!!.folderPath
                    val newPath = FileOperationManager.rename(context, oldPath, newName)
                    if (newPath != null) {
                        // 刷新列表
                        viewModel.refreshFolders()
                    }
                    showRenameDialog = false
                    selectedFolder = null
                }
            }
        )
    }
    
    // 删除确认对话框
    if (showDeleteDialog && selectedFolder != null) {
        DeleteConfirmDialog(
            visible = true,
            fileName = selectedFolder!!.folderName,
            isFolder = true,
            onDismiss = { 
                showDeleteDialog = false
                selectedFolder = null
            },
            onConfirm = {
                lifecycleOwner.lifecycleScope.launch {
                    val success = FileOperationManager.delete(
                        context,
                        selectedFolder!!.folderPath,
                        isFolder = true
                    )
                    if (success) {
                        // 刷新列表
                        viewModel.refreshFolders()
                    }
                    showDeleteDialog = false
                    selectedFolder = null
                }
            }
        )
    }
    
    // 批量删除确认对话框
    if (showBatchDeleteDialog && selectedFolders.isNotEmpty()) {
        BatchDeleteConfirmDialog(
            visible = true,
            count = selectedFolders.size,
            isFolder = true,
            onDismiss = { showBatchDeleteDialog = false },
            onConfirm = {
                lifecycleOwner.lifecycleScope.launch {
                    selectedFolders.forEach { folder ->
                        FileOperationManager.delete(
                            context,
                            folder.folderPath,
                            isFolder = true
                        )
                    }
                    // 刷新列表
                    viewModel.refreshFolders()
                    selectedFolders = emptySet()
                    isEditMode = false
                    showBatchDeleteDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderItem(
    folder: VideoFolder,
    isSelected: Boolean,
    isEditMode: Boolean,
    isChecked: Boolean,
    onClick: () -> Unit
) {
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
                .padding(16.dp)
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
            
            // 文件夹图标
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 文件夹信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = folder.folderName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 30.dp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${folder.videoCount} 个视频",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 右箭头（非编辑模式）
            if (!isEditMode) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOff,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "需要存储权限",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "请授予存储权限以浏览视频文件",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("授予权限")
            }
        }
    }
}



@Composable
private fun SortDialog(
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
                    text = "视频数量 (升序)",
                    isSelected = currentSortType == "VIDEO_COUNT" && currentSortOrder == "ASCENDING",
                    onClick = { onSortSelected("VIDEO_COUNT", "ASCENDING") }
                )
                SortOption(
                    text = "视频数量 (降序)",
                    isSelected = currentSortType == "VIDEO_COUNT" && currentSortOrder == "DESCENDING",
                    onClick = { onSortSelected("VIDEO_COUNT", "DESCENDING") }
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

// ==================== 树状视图视频项 ====================

/**
 * 树状视图中的视频文件项（简化版）
 * 显示视频名称、时长、大小
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeVideoItem(
    video: VideoFile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 视频图标
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 视频信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = video.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 30.dp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (video.duration > 0) {
                        Text(
                            text = FormatUtils.formatDuration(video.duration),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = FormatUtils.formatFileSize(video.size),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 右箭头
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


