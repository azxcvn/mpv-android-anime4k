package com.fam4k007.videoplayer.di

import com.fam4k007.videoplayer.presentation.BilibiliViewModel
import com.fam4k007.videoplayer.presentation.BangumiIndexViewModel
import com.fam4k007.videoplayer.presentation.BangumiDetailViewModel
import com.fam4k007.videoplayer.presentation.CacheManagementViewModel
import com.fam4k007.videoplayer.presentation.LibraryViewModel
import com.fam4k007.videoplayer.presentation.LogViewerViewModel
import com.fam4k007.videoplayer.presentation.MediaInfoViewModel
import com.fam4k007.videoplayer.presentation.PlaybackHistoryViewModel
import com.fam4k007.videoplayer.presentation.PlaybackSettingsViewModel
import com.fam4k007.videoplayer.presentation.PlayerViewModel
import com.fam4k007.videoplayer.presentation.SettingsViewModel
import com.fam4k007.videoplayer.presentation.SubtitleSearchViewModel
import com.fam4k007.videoplayer.presentation.TVBrowserViewModel
import com.fam4k007.videoplayer.presentation.WebDavViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Presentation层DI模块
 * 提供所有ViewModel的依赖注入
 */
val presentationModule = module {
    
    // 播放器ViewModel（注入PlayerRepository、Anime4KManager）
    viewModel { 
        PlayerViewModel(
            get<com.fam4k007.videoplayer.repository.PlayerRepository>(), 
            get<com.fam4k007.videoplayer.domain.player.Anime4KManager>()
        ) 
    }
    
    // 视频库ViewModel（注入4个Manager + PreferencesManager）
    viewModel { 
        LibraryViewModel(
            mediaScanManager = get(),
            folderBrowserManager = get(),
            videoBrowserManager = get(),
            treeNavigationManager = get(),
            preferencesManager = get()
        ) 
    }
    
    // B站功能ViewModel
    viewModel { BilibiliViewModel(get()) }
    
    // 设置ViewModel
    viewModel { SettingsViewModel(get()) }
    
    // 播放设置ViewModel
    viewModel { PlaybackSettingsViewModel(get()) }
    
    // TV浏览器ViewModel
    viewModel { TVBrowserViewModel() }
    
    // 番剧索引ViewModel
    viewModel { BangumiIndexViewModel(get()) }
    
    // 番剧详情ViewModel
    viewModel { BangumiDetailViewModel(get(), get()) }

    // WebDAV ViewModel
    viewModel { WebDavViewModel(get(), get()) }
    
    // 字幕搜索ViewModel
    viewModel { SubtitleSearchViewModel(get(), androidContext()) }
    
    // 播放历史ViewModel
    viewModel { PlaybackHistoryViewModel(get()) }
    
    // 日志查看ViewModel
    viewModel { LogViewerViewModel(androidContext()) }
    
    // 缓存管理ViewModel
    viewModel { CacheManagementViewModel(androidContext()) }
    
    // 媒体信息ViewModel
    viewModel { MediaInfoViewModel(get()) }
}
