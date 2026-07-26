package com.fam4k007.videoplayer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.PlaybackHistoryManager
import com.fam4k007.videoplayer.VideoPlayerActivity
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.remote.RemotePlaybackHeaders
import com.fam4k007.videoplayer.remote.RemotePlaybackLauncher
import com.fam4k007.videoplayer.remote.RemotePlaybackRequest
import com.fam4k007.videoplayer.remote.RemoteUrlParser
import org.koin.compose.koinInject

/**
 * Compose 版本的主页
 */
@Composable
fun HomeScreen(
    historyManager: PlaybackHistoryManager,
    onNavigateToSettings: () -> Unit,
    onNavigateToWebDav: () -> Unit = {},
    onNavigateToVideoBrowser: () -> Unit = {},
    onNavigateToBiliBiliPlay: () -> Unit = {},
    onNavigateToTVBrowser: () -> Unit = {},
    onNavigateToBiliBiliLogin: () -> Unit = {},
) {
    val context = LocalContext.current
    val preferencesManager: PreferencesManager = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isExpanded by remember { mutableStateOf(false) }
    var showRemoteUrlDialog by remember { mutableStateOf(false) }
    var resumeRefreshKey by remember { mutableLongStateOf(0L) }
    var isScanningFlat by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // 监听生命周期，返回时自动收起
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isExpanded = false // 返回时自动收起
                resumeRefreshKey++   // 触发继续播放状态刷新
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部区域
            TopBar(
                onLoginClick = onNavigateToBiliBiliLogin,
                onSettingsClick = onNavigateToSettings
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Logo 区域（顶部偏下）
            LogoSection(
                historyManager = historyManager,
                refreshKey = resumeRefreshKey,
                onContinuePlay = { lastVideo ->
                    continueLastPlay(context, lastVideo)
                }
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // 播放本地视频按钮（给文本留出空间）
            GradientButton(
                text = "播放本地视频",
                isLoading = isScanningFlat,
                onClick = {
                    if (preferencesManager.getVideoDisplayMode() == "flat") {
                        isScanningFlat = true
                        scope.launch {
                            val videos = withContext(Dispatchers.IO) {
                                scanAllVideosFlat(context)
                            }
                            isScanningFlat = false
                            if (videos.isEmpty()) {
                                android.widget.Toast.makeText(context, "未找到视频文件", android.widget.Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val intent = Intent(context, com.fam4k007.videoplayer.VideoListComposeActivity::class.java)
                            intent.putExtra("folder_name", "所有视频")
                            intent.putExtra("folder_path", "所有视频")
                            intent.putParcelableArrayListExtra("video_list", ArrayList(videos))
                            context.startActivity(intent)
                            (context as? android.app.Activity)?.overridePendingTransition(
                                com.fam4k007.videoplayer.R.anim.slide_in_right,
                                com.fam4k007.videoplayer.R.anim.slide_out_left
                            )
                        }
                    } else {
                        onNavigateToVideoBrowser()
                    }
                }
            )

            
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // 右下角展开/收起按钮和功能区
        ExpandableActionButton(
            isExpanded = isExpanded,
            onToggle = { isExpanded = !isExpanded },
            onTVClick = {
                isExpanded = false
                onNavigateToTVBrowser()
            },
            onBiliBiliClick = {
                isExpanded = false  // 点击后自动收起
                onNavigateToBiliBiliPlay()
            },
            onWebDavClick = {
                isExpanded = false  // 点击后自动收起
                onNavigateToWebDav()
            },
            onNetworkLinkClick = {
                isExpanded = false
                showRemoteUrlDialog = true
            }
        )

        if (showRemoteUrlDialog) {
            RemoteUrlDialog(
                onDismiss = { showRemoteUrlDialog = false },
                onConfirm = { request ->
                    showRemoteUrlDialog = false
                    RemotePlaybackLauncher.start(context, request)
                    (context as? android.app.Activity)?.overridePendingTransition(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                    )
                }
            )
        }
    }
}

/**
 * 顶部栏
 */
@Composable
fun TopBar(
    onLoginClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左上角登录按钮
        IconWithBackground(
            icon = Icons.Default.Person,
            contentDescription = "Login",
            onClick = onLoginClick
        )
        
        // 中间标题
        Text(
            text = "Meow Player",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        // 右上角设置按钮
        IconWithBackground(
            icon = Icons.Default.Settings,
            contentDescription = "Settings",
            onClick = onSettingsClick
        )
    }
}

/**
 * Logo 区域（可点击继续播放）
 */
@Composable
fun LogoSection(
    historyManager: PlaybackHistoryManager,
    refreshKey: Long = 0L,
    onContinuePlay: (PlaybackHistoryManager.HistoryItem) -> Unit
) {
    val context = LocalContext.current
    // refreshKey 变化时重新查询最新播放记录
    val lastVideo = remember(refreshKey) {
        historyManager.getLastPlayedLocalVideo()
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Logo 图片（直接显示图标，不带背景框）
        Icon(
            painter = painterResource(id = R.drawable.ic_continue_play),
            contentDescription = "Resume Playback",
            modifier = Modifier
                .size(120.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable {
                    val video = historyManager.getLastPlayedLocalVideo()
                    if (video != null) {
                        onContinuePlay(video)
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "No playback history",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
            tint = Color.Unspecified
        )
        
        // 提示文字（仅在有播放记录时显示）
        if (lastVideo != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Resume: ${lastVideo.fileName}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .padding(horizontal = 16.dp),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 渐变按钮
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .width(280.dp)
            .height(60.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp,
            hoveredElevation = 10.dp
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = if (isLoading) "正在扫描视频..." else text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
fun RemoteUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (RemotePlaybackRequest) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var sourcePageUrl by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }
    var authorization by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Play Online Video",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                item {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Video URL") },
                        placeholder = { Text("https://example.com/video.mp4 or paste curl/headers") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title (optional)") },
                        placeholder = { Text("Set a title for the video") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (showAdvanced) "Hide Advanced" else "Show Advanced")
                    }
                }
                
                if (showAdvanced) {
                    item {
                        OutlinedTextField(
                            value = referer,
                            onValueChange = { referer = it },
                            label = { Text("Referer (optional)") },
                            placeholder = { Text("HTTP Referer header") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    
                    item {
                        OutlinedTextField(
                            value = origin,
                            onValueChange = { origin = it },
                            label = { Text("Origin (optional)") },
                            placeholder = { Text("HTTP Origin header") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    
                    item {
                        OutlinedTextField(
                            value = cookie,
                            onValueChange = { cookie = it },
                            label = { Text("Cookie (optional)") },
                            placeholder = { Text("HTTP Cookie") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    
                    item {
                        OutlinedTextField(
                            value = authorization,
                            onValueChange = { authorization = it },
                            label = { Text("Authorization (optional)") },
                            placeholder = { Text("HTTP Authorization") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    
                    item {
                        OutlinedTextField(
                            value = userAgent,
                            onValueChange = { userAgent = it },
                            label = { Text("User-Agent (optional)") },
                            placeholder = { Text("HTTP User-Agent") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                
                // 底部按钮
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        
                        Button(
                            onClick = {
                                val parsedInput = RemoteUrlParser.parsePlaybackInput(url)
                                val normalizedSourcePageUrl = sourcePageUrl.trim().ifBlank { referer.trim() }
                                val headers = linkedMapOf<String, String>().apply {
                                    putAll(parsedInput?.headers.orEmpty())
                                }
                                if (referer.isNotBlank()) {
                                    headers["Referer"] = referer.trim()
                                }
                                if (origin.isNotBlank()) {
                                    headers["Origin"] = origin.trim()
                                }
                                if (cookie.isNotBlank()) {
                                    headers["Cookie"] = cookie.trim()
                                }
                                if (authorization.isNotBlank()) {
                                    headers["Authorization"] = authorization.trim()
                                }
                                if (userAgent.isNotBlank()) {
                                    headers["User-Agent"] = userAgent.trim()
                                }

                                onConfirm(
                                    RemotePlaybackRequest(
                                        url = parsedInput?.url ?: url.trim(),
                                        title = title.trim(),
                                        sourcePageUrl = normalizedSourcePageUrl,
                                        headers = RemotePlaybackHeaders.normalize(headers),
                                        source = RemotePlaybackRequest.Source.DIRECT_INPUT
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = url.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Play")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 可展开的操作按钮
 */
@Composable
fun ExpandableActionButton(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onBiliBiliClick: () -> Unit,
    onWebDavClick: () -> Unit,
    onTVClick: () -> Unit,
    onNetworkLinkClick: () -> Unit
) {
    var localIsExpanded by remember { mutableStateOf(isExpanded) }
    var showNetworkSubmenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(isExpanded) {
        localIsExpanded = isExpanded
        if (!isExpanded) {
            showNetworkSubmenu = false
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            // 功能区（使用 AnimatedContent 避免卡片切换时的错位）
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(200)) + 
                        scaleIn(
                            initialScale = 0.8f,
                            animationSpec = tween(200)
                        ),
                exit = fadeOut(animationSpec = tween(200)) + 
                       scaleOut(
                           targetScale = 0.8f,
                           animationSpec = tween(200)
                       )
            ) {
                AnimatedContent(
                    targetState = showNetworkSubmenu,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(150)) togetherWith
                                fadeOut(animationSpec = tween(150))
                    },
                    label = "menu_content"
                ) { isNetworkSubmenu ->
                    Card(
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .wrapContentSize(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            if (isNetworkSubmenu) {
                                // 二级菜单 - 网络功能
                                ActionItem(
                                    icon = Icons.Default.Tv,
                                    label = "Browser",
                                    onClick = {
                                        showNetworkSubmenu = false
                                        onTVClick()
                                    }
                                )
                                
                                ActionItem(
                                    icon = Icons.Default.Link,
                                    label = "URL",
                                    onClick = {
                                        showNetworkSubmenu = false
                                        onNetworkLinkClick()
                                    }
                                )
                            } else {
                                // 主菜单
                                ActionItem(
                                    icon = Icons.Default.Public,
                                    label = "Network",
                                    onClick = { showNetworkSubmenu = true }
                                )
                                
                                ActionItem(
                                    icon = Icons.Default.VideoLibrary,
                                    label = "Bangumi",
                                    onClick = onBiliBiliClick
                                )
                                
                                ActionItem(
                                    icon = Icons.Default.Cloud,
                                    label = "WebDAV",
                                    onClick = onWebDavClick
                                )
                            }
                        }
                    }
                }
            }
            
            // 展开/收起按钮
            FloatingActionButton(
                onClick = onToggle,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(60.dp)
            ) {
                // 旋转动画
                val rotation by animateFloatAsState(
                    targetValue = if (isExpanded) 45f else 0f,
                    animationSpec = tween(300),
                    label = "rotation"
                )
                
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(32.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

/**
 * 功能项（纯图标 + 文字，点击水波纹为圆角方形）
 */
@Composable
fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 纯图标按钮（无背景容器）
 */
@Composable
fun IconWithBackground(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * 继续播放功能
 */
private fun continueLastPlay(
    context: android.content.Context,
    lastVideo: PlaybackHistoryManager.HistoryItem
) {
    try {
        val videoUri = Uri.parse(lastVideo.uri)
        
        com.fam4k007.videoplayer.utils.Logger.d("HomeScreen", "=== Continue Last Play ===")
        com.fam4k007.videoplayer.utils.Logger.d("HomeScreen", "Video URI: $videoUri")
        com.fam4k007.videoplayer.utils.Logger.d("HomeScreen", "Folder name: ${lastVideo.folderName}")
        
        // 【修复】从 URI 获取完整的文件夹路径来扫描视频
        val folderPath = getFullFolderPath(context, videoUri)
        com.fam4k007.videoplayer.utils.Logger.d("HomeScreen", "Full folder path: $folderPath")
        
        val videoList = if (folderPath != null) {
            scanVideosInFolder(context, folderPath)
        } else {
            emptyList()
        }
        
        com.fam4k007.videoplayer.utils.Logger.d("HomeScreen", "Scanned ${videoList.size} videos from folder")
        
        val intent = Intent(context, VideoPlayerActivity::class.java).apply {
            data = videoUri
            action = Intent.ACTION_VIEW
            putExtra("folder_path", lastVideo.folderName)
            putExtra("last_position", lastVideo.position)
            
            if (videoList.isNotEmpty()) {
                putParcelableArrayListExtra("video_list", ArrayList(videoList))
                com.fam4k007.videoplayer.utils.Logger.d("HomeScreen", "Put ${videoList.size} videos into intent")
            } else {
                com.fam4k007.videoplayer.utils.Logger.w("HomeScreen", "No videos found, will use identifySeries fallback")
            }
        }
        
        context.startActivity(intent)
        (context as? android.app.Activity)?.overridePendingTransition(
            R.anim.slide_in_right,
            R.anim.slide_out_left
        )
    } catch (e: Exception) {
        com.fam4k007.videoplayer.utils.Logger.e("HomeScreen", "Failed to continue last play", e)
        android.widget.Toast.makeText(
            context,
            "Cannot play this video: ${e.message}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * 从 URI 获取完整的文件夹路径
 */
private fun getFullFolderPath(context: android.content.Context, uri: Uri): String? {
    val projection = arrayOf(android.provider.MediaStore.Video.Media.DATA)
    
    try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val dataColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
                val fullPath = cursor.getString(dataColumn)
                // 从完整路径中提取文件夹路径
                return fullPath.substringBeforeLast("/")
            }
        }
    } catch (e: Exception) {
        com.fam4k007.videoplayer.utils.Logger.e("HomeScreen", "Failed to get folder path from URI", e)
    }
    
    return null
}

/**
 * 扫描指定文件夹的所有视频文件
 */
private fun scanVideosInFolder(context: android.content.Context, folderPath: String): List<com.fam4k007.videoplayer.VideoFileParcelable> {
    val videos = mutableListOf<com.fam4k007.videoplayer.VideoFileParcelable>()
    val projection = arrayOf(
        android.provider.MediaStore.Video.Media._ID,
        android.provider.MediaStore.Video.Media.DISPLAY_NAME,
        android.provider.MediaStore.Video.Media.DATA,
        android.provider.MediaStore.Video.Media.DURATION,
        android.provider.MediaStore.Video.Media.SIZE,
        android.provider.MediaStore.Video.Media.DATE_ADDED
    )
    
    val selection = "${android.provider.MediaStore.Video.Media.DATA} LIKE ?"
    val selectionArgs = arrayOf("$folderPath%")
    val sortOrder = "${android.provider.MediaStore.Video.Media.DISPLAY_NAME} ASC"
    
    try {
        context.contentResolver.query(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATE_ADDED)
            
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn)
                // 只获取直接在该文件夹下的视频（不包括子文件夹）
                if (path.substringBeforeLast("/") == folderPath) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn)
                    val uri = Uri.withAppendedPath(
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    ).toString()
                    
                    videos.add(
                        com.fam4k007.videoplayer.VideoFileParcelable(
                            uri = uri,
                            name = name,
                            path = path,
                            size = size,
                            duration = duration,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        com.fam4k007.videoplayer.utils.Logger.e("HomeScreen", "Failed to scan videos in folder: $folderPath", e)
    }
    
    return videos
}

/**
 * 扫描全部视频（不分文件夹），自动过滤黑名单文件夹中的视频
 */
private fun scanAllVideosFlat(context: android.content.Context): List<com.fam4k007.videoplayer.VideoFileParcelable> {
    val videos = mutableListOf<com.fam4k007.videoplayer.VideoFileParcelable>()
    val projection = arrayOf(
        android.provider.MediaStore.Video.Media._ID,
        android.provider.MediaStore.Video.Media.DISPLAY_NAME,
        android.provider.MediaStore.Video.Media.DATA,
        android.provider.MediaStore.Video.Media.DURATION,
        android.provider.MediaStore.Video.Media.SIZE,
        android.provider.MediaStore.Video.Media.DATE_ADDED
    )
    
    val sortOrder = "${android.provider.MediaStore.Video.Media.DATE_ADDED} DESC"
    
    // 获取黑名单文件夹列表
    val blacklistedFolders = com.fam4k007.videoplayer.preferences.PreferencesManager.getInstance(context).getBlacklistedFolders()
    
    try {
        context.contentResolver.query(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATE_ADDED)
            
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn)
                val file = java.io.File(path)
                if (!file.exists()) continue
                
                // 检查父文件夹是否在黑名单中
                val parentPath = file.parent
                if (parentPath != null && parentPath in blacklistedFolders) continue
                
                // 检查 ScanFilter（.nomedia 规则、隐藏文件夹）
                if (com.fam4k007.videoplayer.utils.ScanFilter.shouldSkipFile(context, path)) continue
                
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val uri = android.net.Uri.withAppendedPath(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                ).toString()
                
                videos.add(
                    com.fam4k007.videoplayer.VideoFileParcelable(
                        uri = uri,
                        name = name,
                        path = path,
                        size = size,
                        duration = duration,
                        dateAdded = dateAdded
                    )
                )
            }
        }

        // 补充扫描：当 .nomedia 关闭或隐藏文件夹开启时，MediaStore 可能遗漏文件
        val prefs = com.fam4k007.videoplayer.preferences.PreferencesManager.getInstance(context)
        if (prefs.getIncludeNoMediaFolders() || prefs.isScanHiddenFoldersEnabled()) {
            val knownPaths = videos.map { it.path }.toMutableSet()
            val parentDirs = videos.map { java.io.File(it.path).parentFile?.absolutePath }.distinct().filterNotNull()
            for (parentPath in parentDirs) {
                val parentFile = java.io.File(parentPath)
                if (!parentFile.exists() || !parentFile.isDirectory) continue
                parentFile.listFiles()?.forEach { subDir ->
                    if (!subDir.isDirectory || !subDir.canRead()) return@forEach
                    // 隐藏文件夹
                    if (prefs.isScanHiddenFoldersEnabled() && subDir.name.startsWith(".")) {
                        scanSingleFolderFlat(subDir, knownPaths, videos, context)
                    }
                    // .nomedia 关闭时扫子目录
                    if (prefs.getIncludeNoMediaFolders()) {
                        scanSingleFolderFlat(subDir, knownPaths, videos, context)
                    }
                }
            }
        }
    } catch (e: Exception) {
        com.fam4k007.videoplayer.utils.Logger.e("HomeScreen", "Failed to scan all videos", e)
    }
    
    return videos
}

/**
 * 扫描单个文件夹中的视频（flat 模式补充扫描）
 */
private fun scanSingleFolderFlat(
    dir: java.io.File,
    knownPaths: MutableSet<String>,
    videos: MutableList<com.fam4k007.videoplayer.VideoFileParcelable>,
    context: android.content.Context
) {
    try {
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return
        if (com.fam4k007.videoplayer.utils.ScanFilter.shouldSkipFolder(context, dir.absolutePath)) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (!file.isFile || !file.exists()) continue
            val path = file.absolutePath
            if (path in knownPaths) continue
            if (com.fam4k007.videoplayer.utils.ScanFilter.shouldSkipFile(context, path)) continue
            val ext = file.extension.lowercase()
            if (ext in com.fam4k007.videoplayer.AppConstants.Files.SUPPORTED_VIDEO_EXTENSIONS) {
                videos.add(com.fam4k007.videoplayer.VideoFileParcelable(
                    uri = android.net.Uri.fromFile(file).toString(),
                    name = file.name,
                    path = path,
                    size = file.length(),
                    duration = 0L,
                    dateAdded = file.lastModified() / 1000
                ))
                knownPaths.add(path)
            }
        }
    } catch (_: Exception) { }
}
