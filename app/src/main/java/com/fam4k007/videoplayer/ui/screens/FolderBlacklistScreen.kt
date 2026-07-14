package com.fam4k007.videoplayer.ui.screens

import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.ui.components.PreferenceSectionHeader
import com.fam4k007.videoplayer.ui.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

/**
 * 文件夹黑名单管理页面
 * 管理被屏蔽的文件夹列表，被屏蔽的文件夹不会被扫描到视频
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBlacklistScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val preferencesManager: PreferencesManager = koinInject()

    var blacklistedFolders by remember { mutableStateOf(preferencesManager.getBlacklistedFolders()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var availableFolders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Folder Blacklist",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = MaterialTheme.spacing.medium)
        ) {
            Text(
                text = "Blocked folders will not be scanned for videos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (blacklistedFolders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOff,
                            contentDescription = null,
                            modifier = Modifier.padding(bottom = 8.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No blacklisted folders",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        PreferenceSectionHeader("Blocked Folders (${blacklistedFolders.size})")
                    }

                    items(blacklistedFolders.toList()) { folderPath ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = File(folderPath).name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = folderPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updated = blacklistedFolders.toMutableSet().apply { remove(folderPath) }
                                        blacklistedFolders = updated
                                        preferencesManager.setBlacklistedFolders(updated)
                                    }
                                ) {
                                    Text(
                                        text = "✕",
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        showAddDialog = true
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                availableFolders = scanVideoFolders(context)
                                // 过滤掉已在黑名单中的
                                availableFolders = availableFolders.filter { it.path !in blacklistedFolders }
                            } catch (_: Exception) {
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text(
                        text = "Add folder to blacklist",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (blacklistedFolders.isNotEmpty()) {
                TextButton(
                    onClick = { showClearAllDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "Clear all blacklisted folders",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        }
    }

    if (showAddDialog) {
        AddFolderBlacklistDialog(
            folders = availableFolders,
            isLoading = isLoading,
            onDismiss = { showAddDialog = false },
            onAddFolders = { folderPaths ->
                val updated = blacklistedFolders.toMutableSet().apply { addAll(folderPaths) }
                blacklistedFolders = updated
                preferencesManager.setBlacklistedFolders(updated)
                showAddDialog = false
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear all blacklisted folders?") },
            text = { Text("This will remove all blacklisted folders. You can re-add them later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        blacklistedFolders = emptySet()
                        preferencesManager.setBlacklistedFolders(emptySet())
                        showClearAllDialog = false
                    }
                ) {
                    Text("Confirm", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * 可供选择的文件夹项
 */
private data class FolderItem(
    val name: String,
    val path: String
)

/**
 * 扫描设备上所有包含视频的文件夹
 */
private suspend fun scanVideoFolders(context: android.content.Context): List<FolderItem> = withContext(Dispatchers.IO) {
    val folderSet = mutableSetOf<String>()

    try {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn)
                val file = File(path)
                if (file.exists()) {
                    val folderPath = file.parent
                    if (folderPath != null) {
                        folderSet.add(folderPath)
                    }
                }
            }
        }
    } catch (_: Exception) {
    }

    folderSet.map { path ->
        FolderItem(name = File(path).name, path = path)
    }.sortedBy { it.name.lowercase() }
}

/**
 * 添加文件夹到黑名单的对话框
 */
@Composable
private fun AddFolderBlacklistDialog(
    folders: List<FolderItem>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAddFolders: (Set<String>) -> Unit
) {
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select folders to block")
        },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Scanning folders...")
                }
            } else if (folders.isEmpty()) {
                Text("No folders to add (all video folders are already blacklisted)")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(folders) { folder ->
                        val isSelected = folder.path in selectedPaths
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPaths = if (isSelected) {
                                        selectedPaths - folder.path
                                    } else {
                                        selectedPaths + folder.path
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle
                                    else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = folder.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAddFolders(selectedPaths)
                },
                enabled = selectedPaths.isNotEmpty() && !isLoading
            ) {
                Text("Add (${selectedPaths.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
