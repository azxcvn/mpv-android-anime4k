package com.fam4k007.videoplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fam4k007.videoplayer.presentation.SubtitleSearchViewModel
import com.fam4k007.videoplayer.ui.screens.SubtitleSearchScreen
import com.fam4k007.videoplayer.ui.theme.ThemeController
import com.fam4k007.videoplayer.ui.theme.VideoPlayerTheme
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.androidx.compose.koinViewModel

/**
 * 字幕搜索下载Activity（重构版）
 * 提供字幕搜索和下载功能
 */
class SubtitleSearchActivity : BaseActivity() {

    private var themeRevision by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用边到边显示
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val revision = themeRevision
            KoinAndroidContext {
                val viewModel: SubtitleSearchViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                
                // 显示Toast消息
                LaunchedEffect(uiState.message) {
                    uiState.message?.let { message ->
                        Toast.makeText(this@SubtitleSearchActivity, message, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessage()
                    }
                }
                
                val themeController = ThemeController.from(this@SubtitleSearchActivity)
                VideoPlayerTheme(
                    appTheme = themeController.getCurrentTheme(),
                    darkMode = themeController.getDarkMode(),
                    amoledMode = themeController.getAmoledMode()
                ) {
                    SubtitleSearchScreen(
                        savedFolderUri = uiState.savedFolderUri,
                        mediaResults = uiState.mediaResults,
                        searchResults = uiState.searchResults,
                        isSearchingMedia = uiState.isSearchingMedia,
                        isSearching = uiState.isSearching,
                        searchOptions = uiState.searchOptions,
                        selectedMedia = uiState.selectedMedia,
                        onBack = {
                            finish()
                        },
                        onFolderSelected = { uri -> handleFolderSelected(uri, viewModel) },
                        onSearchOptionsChanged = { options -> viewModel.updateSearchOptions(options) },
                        onSearchMedia = { query -> viewModel.searchMedia(query) },
                        onSelectMedia = { media -> viewModel.selectMedia(media) },
                        onDownload = { subtitle -> viewModel.downloadSubtitle(subtitle) },
                        onClearSelection = { viewModel.clearSelection() }
                    )
                }
            }
        }
    }

    /**
     * 处理文件夹选择
     */
    private fun handleFolderSelected(uri: Uri, viewModel: SubtitleSearchViewModel) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            viewModel.setFolderUri(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        themeRevision++
    }
}
