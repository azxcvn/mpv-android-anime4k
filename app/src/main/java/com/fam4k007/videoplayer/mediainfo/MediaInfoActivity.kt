package com.fam4k007.videoplayer.mediainfo

import com.fam4k007.videoplayer.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.fam4k007.videoplayer.presentation.MediaInfoUiState
import com.fam4k007.videoplayer.presentation.MediaInfoViewModel
import com.fam4k007.videoplayer.ui.theme.ThemeController
import com.fam4k007.videoplayer.ui.theme.VideoPlayerTheme
import com.fam4k007.videoplayer.utils.MediaInfoHelper
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 媒体信息详情页面
 * 显示视频文件的详细信息
 */
class MediaInfoActivity : ComponentActivity() {

    private var themeRevision by mutableStateOf(0)

    companion object {
        private const val TAG = "MediaInfoActivity"
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_NAME = "extra_video_name"

        fun start(context: Context, videoUri: String, videoName: String) {
            val intent = Intent(context, MediaInfoActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URI, videoUri)
                putExtra(EXTRA_VIDEO_NAME, videoName)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用边到边显示（沉浸式状态栏）
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: ""
        val videoName = intent.getStringExtra(EXTRA_VIDEO_NAME) ?: "Unknown Video"

        setContent {
            val revision = themeRevision
            KoinAndroidContext {
                val themeController = ThemeController.from(this@MediaInfoActivity)
                VideoPlayerTheme(
                    appTheme = themeController.getCurrentTheme(),
                    darkMode = themeController.getDarkMode(),
                    amoledMode = themeController.getAmoledMode()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MediaInfoScreen(
                            videoUri = videoUri,
                            videoName = videoName,
                            onBack = {
                                finish()
                                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        themeRevision++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoScreen(
    videoUri: String,
    videoName: String,
    onBack: () -> Unit,
    viewModel: MediaInfoViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // 加载媒体信息
    LaunchedEffect(Unit) {
        viewModel.loadMediaInfo(videoUri, videoName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Media Info",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = videoName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (uiState is MediaInfoUiState.Success) {
                        val successState = uiState as MediaInfoUiState.Success
                        if (successState.fullTextContent != null) {
                            Row(modifier = Modifier.padding(end = 8.dp)) {
                                // 复制按钮
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            copyToClipboard(context, successState.fullTextContent, videoName)
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        "Copy",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                // 分享按钮
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            shareMediaInfo(context, successState.fullTextContent, videoName)
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        "Share",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is MediaInfoUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is MediaInfoUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Load Failed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is MediaInfoUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 标签页选择器
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text("Details", color = MaterialTheme.colorScheme.onSurface) }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = { Text("Raw Info", color = MaterialTheme.colorScheme.onSurface) },
                                enabled = state.fullTextContent != null
                            )
                        }
                        
                        // 标签页内容
                        when (selectedTabIndex) {
                            0 -> MediaInfoContent(mediaInfo = state.mediaInfo)
                            1 -> state.fullTextContent?.let { RawInfoContent(fullText = it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RawInfoContent(fullText: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            )
        ) {
            SelectionContainer {
                Text(
                    text = fullText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MediaInfoContent(mediaInfo: MediaInfoHelper.MediaInfoData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 通用信息
        InfoSection(title = "General") {
            val general = mediaInfo.general
            if (general.format.isNotEmpty()) InfoRow("Format", general.format)
            if (general.formatVersion.isNotEmpty()) InfoRow("Format Version", general.formatVersion)
            if (general.fileSize.isNotEmpty()) InfoRow("File Size", general.fileSize)
            if (general.duration.isNotEmpty()) InfoRow("Duration", general.duration)
            if (general.overallBitRate.isNotEmpty()) InfoRow("Overall Bitrate", general.overallBitRate)
            if (general.frameRate.isNotEmpty()) InfoRow("Frame Rate", general.frameRate)
            if (general.title.isNotEmpty()) InfoRow("Title", general.title)
            if (general.writingApplication.isNotEmpty()) InfoRow("Encoding App", general.writingApplication)
            if (general.writingLibrary.isNotEmpty()) InfoRow("Encoding Library", general.writingLibrary)
            if (general.encodedDate.isNotEmpty()) InfoRow("Encoded Date", general.encodedDate)
        }

        // 视频流信息
        mediaInfo.videoStreams.forEachIndexed { index, stream ->
            InfoSection(title = "Video Stream #${index + 1}") {
                if (stream.format.isNotEmpty()) InfoRow("Codec", stream.format)
                if (stream.formatProfile.isNotEmpty()) InfoRow("Profile", stream.formatProfile)
                if (stream.width.isNotEmpty() && stream.height.isNotEmpty()) {
                    InfoRow("Resolution", "${stream.width} × ${stream.height}")
                }
                if (stream.displayAspectRatio.isNotEmpty()) InfoRow("Aspect Ratio", stream.displayAspectRatio)
                if (stream.frameRate.isNotEmpty()) InfoRow("Frame Rate", stream.frameRate)
                if (stream.frameRateMode.isNotEmpty()) InfoRow("Frame Rate Mode", stream.frameRateMode)
                if (stream.bitRate.isNotEmpty()) InfoRow("Bitrate", stream.bitRate)
                if (stream.bitDepth.isNotEmpty()) InfoRow("Bit Depth", stream.bitDepth)
                if (stream.colorSpace.isNotEmpty()) InfoRow("Color Space", stream.colorSpace)
                if (stream.chromaSubsampling.isNotEmpty()) InfoRow("Chroma Subsampling", stream.chromaSubsampling)
                if (stream.hdrFormat.isNotEmpty()) InfoRow("HDR Format", stream.hdrFormat)
                if (stream.maxCLL.isNotEmpty()) InfoRow("Max CLL", stream.maxCLL)
                if (stream.maxFALL.isNotEmpty()) InfoRow("Max FALL", stream.maxFALL)
                if (stream.encodingLibrary.isNotEmpty()) InfoRow("Encoding Library", stream.encodingLibrary)
                if (stream.streamSize.isNotEmpty()) InfoRow("Stream Size", stream.streamSize)
                if (stream.duration.isNotEmpty()) InfoRow("Duration", stream.duration)
            }
        }

        // 音频流信息
        mediaInfo.audioStreams.forEachIndexed { index, stream ->
            InfoSection(title = "Audio Stream #${index + 1}") {
                if (stream.format.isNotEmpty()) InfoRow("Codec", stream.format)
                if (stream.channels.isNotEmpty()) InfoRow("Channels", stream.channels)
                if (stream.channelLayout.isNotEmpty()) InfoRow("Channel Layout", stream.channelLayout)
                if (stream.samplingRate.isNotEmpty()) InfoRow("Sample Rate", stream.samplingRate)
                if (stream.bitRate.isNotEmpty()) InfoRow("Bitrate", stream.bitRate)
                if (stream.language.isNotEmpty()) InfoRow("Language", stream.language)
                if (stream.title.isNotEmpty()) InfoRow("Title", stream.title)
                if (stream.compressionMode.isNotEmpty()) InfoRow("Compression", stream.compressionMode)
                if (stream.delay.isNotEmpty()) InfoRow("Delay", stream.delay)
                if (stream.streamSize.isNotEmpty()) InfoRow("Stream Size", stream.streamSize)
                if (stream.duration.isNotEmpty()) InfoRow("Duration", stream.duration)
            }
        }

        // 字幕流信息
        mediaInfo.textStreams.forEachIndexed { index, stream ->
            InfoSection(title = "Subtitle Stream #${index + 1}") {
                if (stream.format.isNotEmpty()) InfoRow("Format", stream.format)
                if (stream.codecId.isNotEmpty()) InfoRow("Codec ID", stream.codecId)
                if (stream.language.isNotEmpty()) InfoRow("Language", stream.language)
                if (stream.title.isNotEmpty()) InfoRow("Title", stream.title)
                if (stream.muxingMode.isNotEmpty()) InfoRow("Muxing Mode", stream.muxingMode)
                if (stream.countOfElements.isNotEmpty()) InfoRow("Elements", stream.countOfElements)
                if (stream.streamSize.isNotEmpty()) InfoRow("Stream Size", stream.streamSize)
                if (stream.duration.isNotEmpty()) InfoRow("Duration", stream.duration)
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}

private suspend fun copyToClipboard(context: Context, text: String, fileName: String) {
    withContext(Dispatchers.Main) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Media Info - $fileName", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun shareMediaInfo(context: Context, text: String, fileName: String) {
    withContext(Dispatchers.Main) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Media info - $fileName")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "Share Media Info"))
        } catch (e: Exception) {
            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
