package com.fam4k007.videoplayer.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.repository.SubtitleRepository
import com.fam4k007.videoplayer.subtitle.SearchOptions
import com.fam4k007.videoplayer.subtitle.SubtitleInfo
import com.fam4k007.videoplayer.subtitle.TmdbMediaResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 字幕搜索ViewModel
 */
class SubtitleSearchViewModel(
    private val subtitleRepository: SubtitleRepository,
    private val context: Context
) : ViewModel() {

    companion object {
        private const val PREFS_NAME = "subtitle_search"
        private const val KEY_SAVE_DIR_URI = "save_directory_uri"
        private const val KEY_LANGUAGES = "languages"
        private const val KEY_SOURCES = "sources"
        private const val KEY_FORMATS = "formats"
    }

    // UI状态
    data class UiState(
        val savedFolderUri: Uri? = null,
        val mediaResults: List<TmdbMediaResult> = emptyList(),
        val searchResults: List<SubtitleInfo> = emptyList(),
        val isSearchingMedia: Boolean = false,
        val isSearching: Boolean = false,
        val searchOptions: SearchOptions = SearchOptions(),
        val selectedMedia: TmdbMediaResult? = null,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSavedPreferences()
    }

    /**
     * 加载已保存的偏好设置
     */
    private fun loadSavedPreferences() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // 加载保存目录
        val savedUriString = prefs.getString(KEY_SAVE_DIR_URI, null)
        val savedUri = savedUriString?.let { Uri.parse(it) }
        
        // 加载搜索选项
        val languages = prefs.getStringSet(KEY_LANGUAGES, setOf("zh", "en")) ?: setOf("zh", "en")
        val sources = prefs.getStringSet(KEY_SOURCES, setOf("all")) ?: setOf("all")
        val formats = prefs.getStringSet(KEY_FORMATS, setOf("srt", "ass")) ?: setOf("srt", "ass")
        
        val searchOptions = SearchOptions(
            languages = languages,
            sources = sources,
            formats = formats
        )

        _uiState.value = _uiState.value.copy(
            savedFolderUri = savedUri,
            searchOptions = searchOptions
        )
    }

    /**
     * 保存偏好设置
     */
    private fun savePreferences() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val state = _uiState.value
        prefs.edit().apply {
            state.savedFolderUri?.let { putString(KEY_SAVE_DIR_URI, it.toString()) }
            putStringSet(KEY_LANGUAGES, state.searchOptions.languages)
            putStringSet(KEY_SOURCES, state.searchOptions.sources)
            putStringSet(KEY_FORMATS, state.searchOptions.formats)
            apply()
        }
    }

    /**
     * 设置保存文件夹
     */
    fun setFolderUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            savedFolderUri = uri,
            message = "Save folder setting successful"
        )
        savePreferences()
    }

    /**
     * 更新搜索选项
     */
    fun updateSearchOptions(options: SearchOptions) {
        _uiState.value = _uiState.value.copy(
            searchOptions = options,
            message = "Search options updated"
        )
        savePreferences()
        
        // 如果已选择媒体，使用新选项重新搜索
        _uiState.value.selectedMedia?.let { media ->
            searchSubtitles(media.id)
        }
    }

    /**
     * 搜索媒体
     */
    fun searchMedia(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "Please enter a search keyword")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearchingMedia = true,
                mediaResults = emptyList(),
                searchResults = emptyList(),
                selectedMedia = null,
                message = null
            )

            subtitleRepository.searchMedia(query)
                .onSuccess { media ->
                    _uiState.value = _uiState.value.copy(
                        isSearchingMedia = false,
                        mediaResults = media,
                        message = when {
                            media.isEmpty() -> "No related media found"
                            media.size == 1 -> null // 自动选择，不显示消息
                            else -> null
                        }
                    )
                    
                    // 只有一个结果，直接选中并搜索字幕
                    if (media.size == 1) {
                        selectMedia(media[0])
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSearchingMedia = false,
                        message = "Search failed: ${error.message}"
                    )
                }
        }
    }

    /**
     * 选择媒体
     */
    fun selectMedia(media: TmdbMediaResult) {
        _uiState.value = _uiState.value.copy(
            selectedMedia = media,
            mediaResults = emptyList() // 清空媒体列表
        )
        
        // 开始搜索字幕
        searchSubtitles(media.id)
    }

    /**
     * 搜索字幕
     */
    private fun searchSubtitles(mediaId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                searchResults = emptyList(),
                message = null
            )

            subtitleRepository.searchSubtitlesByMediaId(mediaId, _uiState.value.searchOptions)
                .onSuccess { subtitles ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        searchResults = subtitles,
                        message = when {
                            subtitles.isEmpty() -> "No subtitles found"
                            else -> "Found ${subtitles.size} subtitles"
                        }
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        message = "Search failed: ${error.message}"
                    )
                }
        }
    }

    /**
     * 清除选择
     */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedMedia = null,
            searchResults = emptyList()
        )
    }

    /**
     * 下载字幕
     */
    fun downloadSubtitle(subtitle: SubtitleInfo) {
        val state = _uiState.value
        
        if (state.savedFolderUri == null) {
            _uiState.value = _uiState.value.copy(message = "Please set the save folder first")
            return
        }

        val videoFileName = state.selectedMedia?.title ?: "subtitle"

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(message = "Starting download...")

            subtitleRepository.downloadSubtitle(
                subtitle,
                state.savedFolderUri,
                videoFileName
            )
                .onSuccess { filePath ->
                    _uiState.value = _uiState.value.copy(
                        message = "Download successful: $filePath"
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        message = "Download failed: ${error.message}"
                    )
                }
        }
    }

    /**
     * 清除消息
     */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
