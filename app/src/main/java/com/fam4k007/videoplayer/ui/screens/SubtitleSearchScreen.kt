package com.fam4k007.videoplayer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fam4k007.videoplayer.subtitle.SubtitleInfo
import com.fam4k007.videoplayer.subtitle.SubtitleLanguages
import com.fam4k007.videoplayer.subtitle.SubtitleSources
import com.fam4k007.videoplayer.subtitle.SubtitleFormats
import com.fam4k007.videoplayer.subtitle.SearchOptions
import com.fam4k007.videoplayer.subtitle.TmdbMediaResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSearchScreen(
    savedFolderUri: Uri?,
    mediaResults: List<TmdbMediaResult>,
    searchResults: List<SubtitleInfo>,
    isSearchingMedia: Boolean,
    isSearching: Boolean,
    searchOptions: SearchOptions,
    selectedMedia: TmdbMediaResult?,
    onBack: () -> Unit,
    onFolderSelected: (Uri) -> Unit,
    onSearchOptionsChanged: (SearchOptions) -> Unit,
    onSearchMedia: (String) -> Unit,
    onSelectMedia: (TmdbMediaResult) -> Unit,
    onDownload: (SubtitleInfo) -> Unit,
    onClearSelection: () -> Unit
) {
    var currentFolderUri by remember { mutableStateOf(savedFolderUri) }
    var searchQuery by remember { mutableStateOf("") }
    var showOptionsDialog by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            onFolderSelected(it)
            currentFolderUri = it
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subtitle Search", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // 搜索输入框卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    placeholder = { 
                        Text(
                            if (currentFolderUri != null) "Enter movie name to search subtitles..." else "Please set a save folder first",
                            fontSize = 14.sp
                        ) 
                    },
                    enabled = currentFolderUri != null,
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = if (currentFolderUri != null) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        Row {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    if (searchQuery.isNotBlank()) {
                                        onSearchMedia(searchQuery.trim())
                                    }
                                },
                                enabled = currentFolderUri != null && searchQuery.isNotBlank()
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Search",
                                    tint = if (currentFolderUri != null && searchQuery.isNotBlank()) 
                                        primaryColor 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 功能按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 设置文件夹按钮
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { folderPickerLauncher.launch(currentFolderUri) },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = primaryColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val displayPath = currentFolderUri?.let { uri ->
                            val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                            docFile?.name ?: "Path Set"
                        }
                        Text(
                            text = displayPath ?: "Set Folder",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 搜索选项按钮
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = currentFolderUri != null) { showOptionsDialog = true },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentFolderUri != null) 
                            MaterialTheme.colorScheme.surfaceContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (currentFolderUri != null) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Search Options",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (currentFolderUri != null) 
                                MaterialTheme.colorScheme.onSurface 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 内容区域
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    // 媒体搜索中
                    isSearchingMedia -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = primaryColor)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Searching movies...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // 显示媒体选择列表
                    mediaResults.isNotEmpty() && selectedMedia == null -> {
                        MediaResultList(
                            mediaList = mediaResults,
                            onSelectMedia = onSelectMedia
                        )
                    }
                    // 已选择媒体，显示字幕搜索结果
                    selectedMedia != null -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 已选择的媒体信息卡片
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Selected",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = selectedMedia.displayTitle,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryColor
                                        )
                                    }
                                    IconButton(onClick = onClearSelection) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Deselect",
                                            tint = primaryColor
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 字幕搜索结果
                            if (isSearching) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = primaryColor)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Searching subtitles...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else if (searchResults.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "No subtitles found",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 16.sp
                                    )
                                }
                            } else {
                                SubtitleResultList(
                                    subtitles = searchResults,
                                    onDownload = onDownload
                                )
                            }
                        }
                    }
                    // 空状态 - 不显示任何内容
                    else -> {}
                }
            }
        }
    }

    // 搜索选项对话框
    if (showOptionsDialog) {
        SearchOptionsDialog(
            currentOptions = searchOptions,
            onDismiss = { showOptionsDialog = false },
            onConfirm = { newOptions ->
                showOptionsDialog = false
                onSearchOptionsChanged(newOptions)
            }
        )
    }
}

@Composable
private fun MediaResultList(
    mediaList: List<TmdbMediaResult>,
    onSelectMedia: (TmdbMediaResult) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Text(
                text = "Select Movie (${mediaList.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        items(mediaList) { media ->
            MediaItem(
                media = media,
                onClick = { onSelectMedia(media) }
            )
        }
    }
}

@Composable
private fun MediaItem(
    media: TmdbMediaResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 类型图标
            Icon(
                imageVector = if (media.mediaType == "tv") Icons.Default.Tv else Icons.Default.Movie,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.displayTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                media.overview?.let { overview ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = overview,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubtitleResultList(
    subtitles: List<SubtitleInfo>,
    onDownload: (SubtitleInfo) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Text(
                text = "Subtitle Results (${subtitles.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        items(subtitles) { subtitle ->
            SubtitleItem(
                subtitle = subtitle,
                onDownload = { onDownload(subtitle) }
            )
        }
    }
}

@Composable
private fun SubtitleItem(
    subtitle: SubtitleInfo,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDownload),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subtitle.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { 
                            Text(
                                subtitle.displayLanguage, 
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            ) 
                        },
                        modifier = Modifier
                            .height(28.dp)
                            .weight(1f, fill = false)
                    )
                    subtitle.format?.let {
                        AssistChip(
                            onClick = {},
                            label = { 
                                Text(
                                    it.uppercase(), 
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                ) 
                            },
                            modifier = Modifier
                                .height(28.dp)
                                .weight(1f, fill = false)
                        )
                    }
                    subtitle.source?.let {
                        AssistChip(
                            onClick = {},
                            label = { 
                                Text(
                                    it, 
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                ) 
                            },
                            modifier = Modifier
                                .height(28.dp)
                                .weight(1f, fill = false)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SearchOptionsDialog(
    currentOptions: SearchOptions,
    onDismiss: () -> Unit,
    onConfirm: (SearchOptions) -> Unit
) {
    var selectedLanguages by remember { mutableStateOf(currentOptions.languages) }
    var selectedSources by remember { mutableStateOf(currentOptions.sources) }
    var selectedFormats by remember { mutableStateOf(currentOptions.formats) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Search Options",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // 语言选项
                Text(
                    "Language",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                SubtitleLanguages.ALL.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedLanguages = if (code == "all") {
                                    setOf("all")
                                } else {
                                    val newSet = selectedLanguages.toMutableSet()
                                    newSet.remove("all")
                                    if (code in newSet) newSet.remove(code) else newSet.add(code)
                                    if (newSet.isEmpty()) setOf("all") else newSet
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = code in selectedLanguages,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            name,
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 来源选项
                Divider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
                Text(
                    "Source",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                SubtitleSources.ALL.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSources = if (code == "all") {
                                    setOf("all")
                                } else {
                                    val newSet = selectedSources.toMutableSet()
                                    newSet.remove("all")
                                    if (code in newSet) newSet.remove(code) else newSet.add(code)
                                    if (newSet.isEmpty()) setOf("all") else newSet
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = code in selectedSources,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            name,
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 格式选项
                Divider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
                Text(
                    "Format",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                SubtitleFormats.ALL.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSet = selectedFormats.toMutableSet()
                                if (code in newSet) newSet.remove(code) else newSet.add(code)
                                if (newSet.isNotEmpty()) {
                                    selectedFormats = newSet
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = code in selectedFormats,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            name,
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        SearchOptions(
                            languages = selectedLanguages,
                            sources = selectedSources,
                            formats = selectedFormats
                        )
                    )
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
