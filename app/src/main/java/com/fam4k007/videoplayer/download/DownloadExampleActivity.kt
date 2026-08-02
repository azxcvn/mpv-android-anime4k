package com.fam4k007.videoplayer.download

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * B站视频/番剧下载示例Activity
 * 
 * 功能：
 * 1. 输入视频/番剧链接
 * 2. 自动识别类型并解析
 * 3. 如果是番剧，显示集数列表供用户选择
 * 4. 开始下载并显示进度
 */
class DownloadExampleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                DownloadScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DownloadScreen(
        viewModel: BilibiliDownloadViewModel = viewModel()
    ) {
        var url by remember { mutableStateOf("") }
        var isParsing by remember { mutableStateOf(false) }
        var parseError by remember { mutableStateOf<String?>(null) }
        var episodes by remember { mutableStateOf<List<EpisodeInfo>>(emptyList()) }
        var selectedSeasonId by remember { mutableStateOf<String?>(null) }

        val downloadItems by viewModel.downloadItems.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Bilibili Video Download") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // 链接输入
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Enter video/bangumi link") },
                    placeholder = { Text("https://www.bilibili.com/video/BV... or ss12345") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isParsing
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 解析按钮
                Button(
                    onClick = {
                        if (url.isNotBlank()) {
                            isParsing = true
                            parseError = null
                            episodes = emptyList()
                            lifecycleScope.launch {
                                try {
                                    val result = viewModel.parseMediaUrlSync(url)
                                    when (result.type) {
                                        MediaType.Video -> {
                                            // 普通视频，直接下载
                                            viewModel.addDownloadByMediaParse(result)
                                            Toast.makeText(
                                                this@DownloadExampleActivity,
                                                "Added to download queue: ${result.title}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        MediaType.Bangumi -> {
                                            // 番剧，获取集数列表
                                            val episodesResult = viewModel.getBangumiEpisodesSync(result.aid)
                                            if (episodesResult.isSuccess) {
                                                episodes = episodesResult.getOrThrow()
                                                selectedSeasonId = result.seasonId
                                                Toast.makeText(
                                                    this@DownloadExampleActivity,
                                                    "Found ${episodes.size} episodes, please select which to download",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                parseError = episodesResult.exceptionOrNull()?.message
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    parseError = e.message ?: "Parse failed"
                                } finally {
                                    isParsing = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    enabled = !isParsing && url.isNotBlank()
                ) {
                    if (isParsing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isParsing) "Parsing..." else "Parse Link")
                }

                // 错误提示
                if (parseError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: $parseError",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 番剧集数列表
                if (episodes.isNotEmpty()) {
                    Text(
                        text = "Select Episodes (${episodes.size} total)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(episodes) { episode ->
                            EpisodeCard(
                                episode = episode,
                                onClick = {
                                    selectedSeasonId?.let { seasonId ->
                                        viewModel.addDownloadByEpisode(episode, seasonId)
                                        Toast.makeText(
                                            this@DownloadExampleActivity,
                                            "Added to download queue: ${episode.longTitle.ifEmpty { episode.title }}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                } else {
                    // 下载列表
                    Text(
                        text = "Download List",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (downloadItems.isEmpty()) {
                        Text(
                            text = "No download tasks yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(downloadItems) { item ->
                                DownloadItemCard(item = item)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EpisodeCard(
        episode: EpisodeInfo,
        onClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Episode ${episode.index}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (episode.longTitle.isNotEmpty()) {
                        Text(
                            text = episode.longTitle,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (episode.badge.isNotEmpty()) {
                        Text(
                            text = episode.badge,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (episode.badgeType) {
                                1 -> MaterialTheme.colorScheme.primary
                                2 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Icon(
                    painter = painterResource(android.R.drawable.stat_sys_download),
                    contentDescription = "Download"
                )
            }
        }
    }

    @Composable
    fun DownloadItemCard(item: DownloadItem) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 状态和进度
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (item.status) {
                            "pending" -> "Pending"
                            "downloading" -> "Downloading"
                            "paused" -> "Paused"
                            "completed" -> "Completed"
                            "failed" -> "Failed"
                            "merging" -> "Merging"
                            else -> item.status
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (item.status) {
                            "completed" -> MaterialTheme.colorScheme.primary
                            "failed" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = "${item.progress}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 进度条
                LinearProgressIndicator(
                    progress = item.progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )

                // 错误信息
                if (item.errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Error: ${item.errorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
