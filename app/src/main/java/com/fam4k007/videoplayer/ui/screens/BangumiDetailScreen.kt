package com.fam4k007.videoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fam4k007.videoplayer.bilibili.model.PgcEpisode
import com.fam4k007.videoplayer.bilibili.model.PgcInfoResult
import com.fam4k007.videoplayer.presentation.BangumiDetailUiState
import com.fam4k007.videoplayer.presentation.BangumiDetailViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 番剧详情页
 * 显示番剧简介和集数列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BangumiDetailScreen(
    seasonId: Int,
    isEpId: Boolean = false,
    onBack: () -> Unit,
    viewModel: BangumiDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val seasonInfo by viewModel.seasonInfo.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val context = LocalContext.current

    // 加载数据
    LaunchedEffect(seasonId, isEpId) {
        viewModel.loadSeasonInfo(seasonId, isEpId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = seasonInfo?.title ?: "Bangumi Details",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is BangumiDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            is BangumiDetailUiState.Success -> {
                seasonInfo?.let { info ->
                    BangumiDetailContent(
                        seasonInfo = info,
                        episodes = episodes,
                        onEpisodeClick = { episode ->
                            viewModel.playEpisode(context, episode)
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
            is BangumiDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadSeasonInfo(seasonId) }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 番剧详情内容
 */
@Composable
private fun BangumiDetailContent(
    seasonInfo: PgcInfoResult,
    episodes: List<PgcEpisode>,
    onEpisodeClick: (PgcEpisode) -> Unit,
    modifier: Modifier = Modifier
) {
    var isReversed by remember { mutableStateOf(false) }
    val displayEpisodes = if (isReversed) episodes.reversed() else episodes
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 封面和基本信息
        item {
            BangumiHeader(seasonInfo = seasonInfo)
        }

        // 简介
        if (!seasonInfo.evaluate.isNullOrEmpty()) {
            item {
                BangumiDescription(description = seasonInfo.evaluate)
            }
        }

        // 集数列表 - 网格布局
        if (episodes.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { isReversed = !isReversed }) {
                        Text(
                            text = if (isReversed) "Ascending" else "Descending",
                            fontSize = 13.sp
                        )
                    }
                }
            }
            
            item {
                // 使用网格布局显示选集，居中对齐
                val columns = 4
                val spacing = 8.dp
                
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        val rows = (displayEpisodes.size + columns - 1) / columns
                        for (row in 0 until rows) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(spacing)
                            ) {
                                for (col in 0 until columns) {
                                    val index = row * columns + col
                                    if (index < displayEpisodes.size) {
                                        EpisodeItem(
                                            episode = displayEpisodes[index],
                                            onClick = { onEpisodeClick(displayEpisodes[index]) }
                                        )
                                    } else {
                                        // 占位
                                        Spacer(modifier = Modifier.width(72.dp).height(72.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 底部留白
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 番剧头部信息
 */
@Composable
private fun BangumiHeader(seasonInfo: PgcInfoResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        // 封面和标题
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 封面
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(seasonInfo.cover)
                    .crossfade(true)
                    .build(),
                contentDescription = seasonInfo.title,
                modifier = Modifier
                    .width(120.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(14.dp))

            // 标题和信息 - 顶对齐，底部用Spacer撑满封面高度
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 标题
                Text(
                    text = seasonInfo.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 评分
                seasonInfo.rating?.let { rating ->
                    Text(
                        text = "Rating ${rating.score} (${rating.count} ratings)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 统计信息 - 各占一行
                seasonInfo.stat?.let { stat ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Views ${formatNumber(stat.views)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Danmaku ${formatNumber(stat.danmakus)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Following ${formatNumber(stat.favorites)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 地区 + 状态
                val areaText = seasonInfo.areas?.firstOrNull()?.let { "Region ${it.name}" } ?: ""
                val statusText = seasonInfo.publish?.let { publish ->
                    val parts = mutableListOf<String>()
                    // 完结状态
                    when (publish.isFinish) {
                        1 -> parts.add("Completed")
                        0 -> parts.add("Ongoing")
                    }
                    // 更新时间（如"每周三更新"）
                    publish.timeFromShow?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                    parts.joinToString(" · ")
                } ?: ""
                val fullText = listOfNotNull(
                    if (areaText.isNotEmpty()) areaText else null,
                    if (statusText.isNotEmpty()) statusText else null
                ).joinToString(" · ")
                if (fullText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = fullText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 最新集数
                seasonInfo.new_ep?.let { newEp ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Latest: ${newEp.title?.takeIf { it.isNotEmpty() } ?: newEp.indexShow ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 底部弹性间距（撑满封面高度）
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * 番剧简介
 */
@Composable
private fun BangumiDescription(description: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "Synopsis",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )

        if (description.length > 100) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (expanded) "Collapse" else "Expand",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = !expanded }
            )
        }
    }
}

/**
 * 集数项 - 标签在左上角，圆角背景
 */
@Composable
private fun EpisodeItem(
    episode: PgcEpisode,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (!episode.badge.isNullOrEmpty()) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
    ) {
        // 标签（左上角）
        if (!episode.badge.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(bottomEnd = 3.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = episode.badge,
                    fontSize = 7.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // 内容居中显示
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 集数标题（如 "1"）
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // 副标题（如果有且不同于标题）
            if (!episode.longTitle.isNullOrEmpty() && episode.longTitle != episode.title) {
                Text(
                    text = episode.longTitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 格式化数字
 */
private fun formatNumber(number: Long): String {
    return when {
        number >= 100000000 -> String.format("%.1fB", number / 100000000.0)
        number >= 10000 -> String.format("%.1fK", number / 10000.0)
        else -> number.toString()
    }
}