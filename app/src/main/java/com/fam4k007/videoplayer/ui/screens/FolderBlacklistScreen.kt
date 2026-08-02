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
import com.fam4k007.videoplayer.ui.components.SwitchItem
import com.fam4k007.videoplayer.ui.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

/**
 * 文件夹黑白名单管理页面
 * 支持黑名单模式（屏蔽指定文件夹）和白名单模式（只扫描指定文件夹）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBlacklistScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val preferencesManager: PreferencesManager = koinInject()

    var isWhitelistMode by remember {
        mutableStateOf(preferencesManager.isWhitelistModeEnabled())
    }
    var folders by remember {
        mutableStateOf(
            if (preferencesManager.isWhitelistModeEnabled())
                preferencesManager.getWhitelistedFolders()
            else
                preferencesManager.getBlacklistedFolders()
        )
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var availableFolders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val descriptionText = if (isWhitelistMode)
        "After specifying folders, the app will only scan videos inside them"
    else
        "After blocking folders, the app will no longer scan videos inside them"
    val emptyText = if (isWhitelistMode) "No whitelisted folders yet" else "No blacklisted folders yet"
    val sectionHeader = if (isWhitelistMode)
        "Selected folders (${folders.size})"
    else
        "Blocked folders (${folders.size})"
    val addButtonText = if (isWhitelistMode) "Add folder to whitelist" else "Add folder to blacklist"
    val clearButtonText = if (isWhitelistMode) "Clear all whitelisted folders" else "Clear all blacklisted folders"
    val dialogTitle = if (isWhitelistMode) "Select folder to add" else "Select folder to block"
    val dialogEmptyText = if (isWhitelistMode)
        "No folders to add (all video folders are already whitelisted)"
    else
        "No folders to add (all video folders are already blacklisted)"
    val clearDialogTitle = if (isWhitelistMode) "Clear all whitelisted folders?" else "Clear all blacklisted folders?"
    val clearDialogText = if (isWhitelistMode)
        "This will remove all folder whitelists. You can add them again later."
    else
        "This will remove all folder blacklists. You can add them again later."

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Folder Blacklist/Whitelist",
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
            // 黑白名单切换开关
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                SwitchItem(
                    title = "Whitelist Mode",
                    subtitle = if (isWhitelistMode) "When whitelist mode is enabled, only videos inside are scanned" else "Block selected folders, do not scan videos inside",
                    checked = isWhitelistMode,
                    onCheckedChange = { enabled ->
                        isWhitelistMode = enabled
                        preferencesManager.setWhitelistModeEnabled(enabled)
                        folders = if (enabled)
                            preferencesManager.getWhitelistedFolders()
                        else
                            preferencesManager.getBlacklistedFolders()
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = descriptionText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (folders.isEmpty()) {
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
                            text = emptyText,
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
                        PreferenceSectionHeader(sectionHeader)
                    }

                    items(folders.toList()) { folderPath ->
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
                                    imageVector = if (isWhitelistMode) Icons.Default.Folder else Icons.Default.FolderOff,
                                    contentDescription = null,
                                    tint = if (isWhitelistMode) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
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
                                        val updated = folders.toMutableSet().apply { remove(folderPath) }
                                        folders = updated
                                        if (isWhitelistMode)
                                            preferencesManager.setWhitelistedFolders(updated)
                                        else
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
                                availableFolders = availableFolders.filter { it.path !in folders }
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
                        text = addButtonText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (folders.isNotEmpty()) {
                TextButton(
                    onClick = { showClearAllDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = clearButtonText,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        }
    }

    if (showAddDialog) {
        AddFolderListDialog(
            folders = availableFolders,
            isLoading = isLoading,
            title = dialogTitle,
            emptyText = dialogEmptyText,
            onDismiss = { showAddDialog = false },
            onAddFolders = { folderPaths ->
                val updated = folders.toMutableSet().apply { addAll(folderPaths) }
                folders = updated
                if (isWhitelistMode)
                    preferencesManager.setWhitelistedFolders(updated)
                else
                    preferencesManager.setBlacklistedFolders(updated)
                showAddDialog = false
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(clearDialogTitle) },
            text = { Text(clearDialogText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        folders = emptySet()
                        if (isWhitelistMode)
                            preferencesManager.setWhitelistedFolders(emptySet())
                        else
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
 * 添加文件夹的通用对话框（黑名单/白名单共用）
 */
@Composable
private fun AddFolderListDialog(
    folders: List<FolderItem>,
    isLoading: Boolean,
    title: String,
    emptyText: String,
    onDismiss: () -> Unit,
    onAddFolders: (Set<String>) -> Unit
) {
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
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
                Text(emptyText)
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
