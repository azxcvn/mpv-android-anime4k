package com.fam4k007.videoplayer.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.VideoBrowserComposeActivity
import com.fam4k007.videoplayer.ui.components.ClickableItem
import com.fam4k007.videoplayer.ui.components.PreferenceCard
import com.fam4k007.videoplayer.ui.components.PreferenceSectionHeader
import com.fam4k007.videoplayer.ui.theme.spacing

/**
 * 视频库页面
 * 显示本地视频文件夹和最近播放
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Video Library",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 本地视频
            item {
                PreferenceSectionHeader(title = "Local Videos")
            }
            
            item {
                PreferenceCard {
                    ClickableItem(
                        title = "Browse Folders",
                        subtitle = "Browse video folders on device",
                        icon = Icons.Default.Folder,
                        onClick = {
                            context.startActivity(Intent(context, VideoBrowserComposeActivity::class.java))
                            (context as? android.app.Activity)?.overridePendingTransition(
                                R.anim.slide_in_right,
                                R.anim.slide_out_left
                            )
                        }
                    )
                    
                    ClickableItem(
                        title = "All Videos",
                        subtitle = "View all local videos",
                        icon = Icons.Default.VideoLibrary,
                        onClick = {
                            // TODO: 实现所有视频列表
                            android.widget.Toast.makeText(
                                context,
                                "This feature is under development",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
            
            // 底部留白
            item {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
            }
        }
    }
}
