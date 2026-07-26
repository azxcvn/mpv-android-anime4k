package com.fam4k007.videoplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fam4k007.videoplayer.dandanplay.AnimeSearchInfo
import com.fam4k007.videoplayer.dandanplay.EpisodeInfo
import com.fam4k007.videoplayer.preferences.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 网络弹幕搜索对话框 - 右侧抽屉式
 * 参考字幕设置和弹幕设置的样式
 */
@Composable
fun DanDanPlaySearchDialog(
    onDismiss: () -> Unit,
    onEpisodeSelected: (episodeId: Int, animeTitle: String, episodeTitle: String, animeId: Int, serverUrl: String?, episodes: List<EpisodeInfo>) -> Unit
) {
    val preferencesManager: PreferencesManager = koinInject()
    var isVisible by remember { mutableStateOf(false) }
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
                    .width(380.dp)  // 比弹幕设置稍宽一点，因为需要显示搜索结果
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { /* 阻止点击穿透 */ }
            ) {
                // 半透明背景层（与字幕/弹幕设置风格一致）
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
                DanDanPlaySearchContent(
                    onDismiss = {
                        isVisible = false
                        coroutineScope.launch {
                            delay(300)
                            onDismiss()
                        }
                    },
                    onEpisodeSelected = onEpisodeSelected
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DanDanPlaySearchContent(
    onDismiss: () -> Unit,
    onEpisodeSelected: (episodeId: Int, animeTitle: String, episodeTitle: String, animeId: Int, serverUrl: String?, episodes: List<EpisodeInfo>) -> Unit
) {
    val preferencesManager: PreferencesManager = koinInject()
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AnimeSearchInfo>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedAnime by remember { mutableStateOf<AnimeSearchInfo?>(null) }
    var selectedAnimeServerUrl by remember { mutableStateOf<String?>(null) }  // 选中番剧的来源服务器
    var animeServerMap by remember { mutableStateOf(mapOf<Int, String?>()) }  // animeId → serverUrl
    val coroutineScope = rememberCoroutineScope()

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
                text = if (selectedAnime == null) "Online Danmaku" else "Select Episode",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 返回按钮（在选择剧集界面显示）
                if (selectedAnime != null) {
                    TextButton(
                        onClick = { selectedAnime = null },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF64B5F6)
                        )
                    ) {
                        Text("Back", fontSize = 13.sp)
                    }
                }
                
                // 关闭按钮
                IconButton(
                    onClick = onDismiss,
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

        Spacer(modifier = Modifier.height(12.dp))

        // 搜索框（仅在搜索界面显示）
        if (selectedAnime == null) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("输入番剧名称", color = Color(0xFF888888)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (searchText.isNotBlank() && !isSearching) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                isSearching = true
                                errorMessage = null
                                try {
                                    android.util.Log.d("DanDanPlayUI", "开始搜索: $searchText")
                                    val enabledServers = preferencesManager.getEnabledDanmakuServers()
                                    if (enabledServers.isEmpty()) {
                                        errorMessage = "No danmaku servers enabled, please configure in Settings"
                                        return@launch
                                    }
                                    
                                    val allResults = mutableListOf<AnimeSearchInfo>()
                                    val serverMap = mutableMapOf<Int, String?>()  // animeId → serverUrl
                                    val errors = mutableListOf<String>()
                                    
                                    for (server in enabledServers) {
                                        try {
                                            val serverUrl = if (server.isDefault) null else server.url
                                            if (serverUrl != null && !serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                                                android.util.Log.w("DanDanPlayUI", "跳过无效服务器 URL: ${server.name} -> $serverUrl")
                                                errors.add("${server.name}: URL 格式无效")
                                                continue
                                            }
                                            val api = com.fam4k007.videoplayer.dandanplay.DanDanPlayApi(serverUrl)
                                            val result = api.searchAnime(searchText)
                                            result.fold(
                                                onSuccess = { response ->
                                                    android.util.Log.d("DanDanPlayUI", "${server.name}: 返回 ${response.animes.size} 个结果")
                                                    for (anime in response.animes) {
                                                        if (allResults.none { it.animeId == anime.animeId }) {
                                                            allResults.add(anime)
                                                            serverMap[anime.animeId] = serverUrl
                                                        }
                                                    }
                                                },
                                                onFailure = { e ->
                                                    android.util.Log.e("DanDanPlayUI", "${server.name} 搜索失败: ${e.message}")
                                                    errors.add("${server.name}: ${e.message}")
                                                }
                                            )
                                        } catch (e: Exception) {
                                            android.util.Log.e("DanDanPlayUI", "${server.name} 异常: ${e.message}")
                                            errors.add("${server.name}: ${e.message}")
                                        }
                                    }
                                    
                                    searchResults = allResults
                                    animeServerMap = serverMap
                                    if (allResults.isEmpty()) {
                                        errorMessage = if (errors.isNotEmpty()) {
                                            "Search failed:\n${errors.joinToString("\n")}"
                                        } else {
                                            "No matching anime found, try different keywords"
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("DanDanPlayUI", "搜索异常", e)
                                    errorMessage = "Search error: ${e.message}\nPlease check network connection"
                                } finally {
                                    isSearching = false
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "搜索",
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF64B5F6),
                            strokeWidth = 2.dp
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF64B5F6),
                    unfocusedBorderColor = Color(0xFF555555),
                    cursorColor = Color(0xFF64B5F6)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 错误提示
        if (errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C1810), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚠️",
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = errorMessage!!,
                    fontSize = 13.sp,
                    color = Color(0xFFFF9800)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 加载指示器
        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF64B5F6),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Searching...",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
        } else if (selectedAnime != null) {
            // 显示剧集列表
            EpisodeList(
                anime = selectedAnime!!,
                onEpisodeSelected = { episode ->
                    onEpisodeSelected(
                        episode.episodeId,
                        selectedAnime!!.animeTitle,
                        episode.episodeTitle,
                        selectedAnime!!.animeId,
                        selectedAnimeServerUrl,
                        selectedAnime!!.episodes
                    )
                    onDismiss()
                }
            )
        } else if (searchResults.isNotEmpty()) {
            // 显示搜索结果
            SearchResultsList(
                results = searchResults,
                onAnimeSelected = { anime ->
                    selectedAnime = anime
                    selectedAnimeServerUrl = animeServerMap[anime.animeId]
                }
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<AnimeSearchInfo>,
    onAnimeSelected: (AnimeSearchInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results) { anime ->
            AnimeCard(anime = anime, onClick = { onAnimeSelected(anime) })
        }
    }
}

@Composable
private fun AnimeCard(
    anime: AnimeSearchInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A2332)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.animeTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${anime.typeDescription} · ${anime.episodes.size} 集",
                    fontSize = 12.sp,
                    color = Color(0xFF9E9E9E)
                )
            }
        }
    }
}

@Composable
private fun EpisodeList(
    anime: AnimeSearchInfo,
    onEpisodeSelected: (EpisodeInfo) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 番剧信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A2332)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                        text = anime.animeTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${anime.typeDescription} · ${anime.episodes.size} 集",
                        fontSize = 12.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 剧集列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(anime.episodes) { episode ->
                EpisodeCard(episode = episode, onClick = { onEpisodeSelected(episode) })
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: EpisodeInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A2332)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = episode.episodeTitle,
                fontSize = 15.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            // 箭头图标
            Text(
                text = "→",
                fontSize = 18.sp,
                color = Color(0xFF64B5F6)
            )
        }
    }
}
