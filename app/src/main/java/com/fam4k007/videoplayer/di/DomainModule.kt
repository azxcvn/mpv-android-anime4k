package com.fam4k007.videoplayer.di

import com.fam4k007.videoplayer.domain.media.FolderBrowserManager
import com.fam4k007.videoplayer.domain.media.MediaScanManager
import com.fam4k007.videoplayer.domain.media.TreeNavigationManager
import com.fam4k007.videoplayer.domain.media.VideoBrowserManager
import com.fam4k007.videoplayer.domain.player.Anime4KManager
import com.fam4k007.videoplayer.domain.subtitle.SubtitleManager
import com.fam4k007.videoplayer.domain.webdav.WebDavClient
import com.fam4k007.videoplayer.domain.webdav.WebDavConfig
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.manager.ThemeManager
import com.fam4k007.videoplayer.utils.ThumbnailCacheManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * 领域模块
 * 提供业务逻辑层的依赖注入（核心算法、管理器等）
 */
val domainModule = module {
    
    // PreferencesManager单例
    single { 
        PreferencesManager.getInstance(androidContext())
    }
    
    // Anime4KManager单例
    single { 
        Anime4KManager(androidContext())
    }
    
    // SubtitleManager - 字幕管理器（无状态，使用factory）
    factory { 
        SubtitleManager()
    }
    
    // WebDavClient - WebDAV客户端（按需创建，使用factory）
    factory { (config: WebDavConfig) -> 
        WebDavClient(config)
    }
    
    // ThumbnailCacheManager单例
    single { 
        ThumbnailCacheManager.getInstance(androidContext())
    }
    
    // ThemeManager（工具类，无状态）
    factory { 
        ThemeManager 
    }

    // ==================== 媒体库 Managers ====================

    // FolderBrowserManager — 文件夹排序、黑名单过滤
    factory {
        FolderBrowserManager(preferencesManager = get())
    }

    // VideoBrowserManager — 视频排序、搜索过滤
    factory {
        VideoBrowserManager(preferencesManager = get())
    }

    // TreeNavigationManager — 树状视图导航栈管理
    factory {
        TreeNavigationManager()
    }

    // MediaScanManager — 媒体扫描协调
    factory {
        MediaScanManager(videoRepository = get())
    }
}
