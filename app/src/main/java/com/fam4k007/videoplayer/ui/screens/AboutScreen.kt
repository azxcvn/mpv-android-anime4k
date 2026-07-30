package com.fam4k007.videoplayer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.ui.components.ClickableItem
import com.fam4k007.videoplayer.ui.components.PreferenceDivider
import com.fam4k007.videoplayer.ui.components.SwitchItem
import com.fam4k007.videoplayer.ui.theme.spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    versionName: String,
    onBack: () -> Unit,
    onNavigateToLicense: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    onNavigateToCache: () -> Unit = {},
    onNavigateToUserAgreement: () -> Unit = {},
    onSendEmail: () -> Unit
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.fam4k007.videoplayer.utils.UpdateManager.UpdateInfo?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App 信息卡片 - 紧凑版
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
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App 图标
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_icon),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.width(20.dp))

                    // 中间文字信息
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "小喵Player",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Version $versionName",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 右侧图标竖向排列
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 邮件图标
                        IconButton(
                            onClick = onSendEmail,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_email),
                                contentDescription = "发送邮件",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // GitHub 图标
                        IconButton(
                            onClick = {
                                openUrl(context, "https://github.com/azxcvn/mpv-android-anime4k")
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_github),
                                contentDescription = "GitHub",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // ===== 信息 =====
            Text(
                text = "信息",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                ClickableItem(
                    title = "许可证书",
                    subtitle = "查看开源许可",
                    icon = Icons.Default.Description,
                    onClick = onNavigateToLicense
                )

                PreferenceDivider()

                ClickableItem(
                    title = "用户协议",
                    subtitle = "预览用户服务协议与隐私政策",
                    icon = Icons.Default.Lock,
                    onClick = onNavigateToUserAgreement
                )
            }

            // ===== 工具 =====
            Text(
                text = "工具",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                ClickableItem(
                    title = "错误日志",
                    subtitle = "查看和导出错误日志",
                    icon = Icons.Default.BugReport,
                    onClick = onNavigateToLogs
                )

                PreferenceDivider()

                ClickableItem(
                    title = "缓存管理",
                    subtitle = "查看和清除应用缓存",
                    icon = Icons.Default.Storage,
                    onClick = onNavigateToCache
                )
            }

            // ===== 更新 =====
            Text(
                text = "更新",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                ClickableItem(
                    title = "检查更新",
                    subtitle = "当前版本: $versionName",
                    icon = Icons.Default.Update,
                    onClick = {
                        scope.launch {
                            try {
                                val result = com.fam4k007.videoplayer.utils.UpdateManager.checkForUpdate(context)
                                if (result != null) {
                                    updateInfo = result
                                    showUpdateDialog = true
                                } else {
                                    android.widget.Toast.makeText(
                                        context, "已是最新版本", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context, "检查更新失败: ${e.message}", android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )

                PreferenceDivider()

                val prefs = remember { com.fam4k007.videoplayer.preferences.PreferencesManager.getInstance(context) }
                var autoCheck by remember { mutableStateOf(prefs.isAutoCheckUpdateEnabled()) }

                SwitchItem(
                    title = "自动检查更新",
                    subtitle = if (autoCheck) "启动后5秒自动检查" else "关闭后不再自动检查",
                    checked = autoCheck,
                    onCheckedChange = { enabled ->
                        autoCheck = enabled
                        prefs.setAutoCheckUpdateEnabled(enabled)
                    }
                )
            }

        }
    }

    // 更新弹窗
    if (showUpdateDialog && updateInfo != null) {
        val prefs = remember { com.fam4k007.videoplayer.preferences.PreferencesManager.getInstance(context) }
        com.fam4k007.videoplayer.ui.components.UpdateDialog(
            updateInfo = updateInfo!!,
            onDismiss = { showUpdateDialog = false },
            onDownload = { url ->
                com.fam4k007.videoplayer.utils.UpdateManager.openDownloadPage(context, url)
                showUpdateDialog = false
            },
            onSecondaryDownload = { url ->
                if (url.isNotBlank()) {
                    com.fam4k007.videoplayer.utils.UpdateManager.openDownloadPage(context, url)
                }
                showUpdateDialog = false
            },
            onIgnore = {
                prefs.setIgnoredUpdateVersion(updateInfo!!.versionName)
                showUpdateDialog = false
            }
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        // Handle error
    }
}
