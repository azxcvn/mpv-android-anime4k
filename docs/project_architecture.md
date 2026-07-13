# FAM4K007 项目架构与技术栈文档

> 最后更新: 2026-07-13  
> 版本: 1.2.9

---

## 目录

1. [项目概述](#1-项目概述)
2. [整体架构](#2-整体架构)
3. [技术栈](#3-技术栈)
4. [分层详解](#4-分层详解)
   - [4.1 UI 层](#41-ui-层)
   - [4.2 Presentation 层](#42-presentation-层)
   - [4.3 Domain 层](#43-domain-层)
   - [4.4 Repository 层](#44-repository-层)
   - [4.5 Data 层](#45-data-层)
   - [4.6 Service 层](#46-service-层)
   - [4.7 DI 层](#47-di-层)
5. [导航系统](#5-导航系统)
6. [状态管理](#6-状态管理)
7. [Manager 模式详解](#7-manager-模式详解)
8. [双 UI 体系](#8-双-ui-体系)
9. [内存管理策略](#9-内存管理策略)
10. [构建与配置](#10-构建与配置)
11. [包结构总览](#11-包结构总览)

---

## 1. 项目概述

FAM4K007 是一款基于 **mpv** 播放引擎的 Android 视频播放器，专注于：

- **高性能播放**：基于 mpv 原生播放器，支持硬件解码
- **Anime4K 实时画质增强**：集成 Anime4K GLSL 着色器
- **弹幕支持**：集成 Bilibili/DanDanPlay 弹幕系统，支持多弹幕服务器管理与并行搜索
- **字幕管理**：支持多种字幕格式（ASS/SRT/VTT）和在线搜索
- **多协议支持**：本地文件、WebDAV、HTTP/HTTPS/RTSP 远程播放
- **B 站生态**：Bilibili API 集成（番剧索引、信息获取、播放地址解析、弹幕下载、二维码登录）
- **下载功能**：Bilibili 视频下载管理器
- **后台播放**：支持一键切到后台只听音频，通过前台 Service 保持进程存活
- **番剧索引**：B 站番剧检索（筛选条件、分页加载、链接输入直达、选集播放）
- **TV 模式**：TV 浏览器，适合大屏设备浏览视频

---

## 2. 整体架构

采用 **基于 Manager 模式的半 MVVM 分层架构**，共 7 层：

```
┌──────────────────────────────────────────────────┐
 │                    UI 层                          │
 │   ┌──────────────────────┬────────────────────┐   │
 │   │  传统 View (XML)      │   Compose Screens   │   │
 │   │  VideoPlayerActivity   │  25 个 Screen      │   │
 │   │  CustomMPVView         │                        │   │
 │   │  CustomMPVView         │  ui/screens/       │   │
 │   │  DanmakuPlayerView     │  ui/components/    │   │
 │   │  7 个扩展文件          │  ui/player/        │   │
 │   └──────────────────────┴────────────────────┘   │
 ├──────────────────────────────────────────────────┤
 │            Manager 层 (Compose 覆盖层)            │
 │   ┌──────────────────────────────────────────┐   │
 │   │  ComposeOverlayManager — 覆盖层管理      │   │
 │   │  9 个抽屉/弹窗组件 (manager/compose/)      │   │
 │   │  ChapterDrawer  SpeedDrawer  Equalizer   │   │
 │   │  DanmakuDialogs SubtitleDialogs          │   │
 │   │  SkipSettings   VideoList   FilePickers  │   │
 │   │  DolbyVisionHintDialog                   │   │
 │   ├──────────────────────────────────────────┤   │
 │   │  ScreenshotManager  SkipIntroOutroManager│   │
 │   │  ThemeManager  VideoThumbnailManager     │   │
 │   └──────────────────────────────────────────┘   │
 ├──────────────────────────────────────────────────┤
 │              Presentation 层                      │
 │   ┌──────────────────────────────────────────┐   │
 │   │  15 个 ViewModel (StateFlow/SharedFlow)   │   │
 │   │  PlayerViewModel  (核心, 监听 MPV 事件)   │   │
 │   │  LibraryViewModel  SettingsViewModel      │   │
 │   │  BangumiIndexViewModel  BangumiDetailVM   │   │
 │   │  BilibiliViewModel  WebDavViewModel       │   │
 │   │  ...                                      │   │
 │   └──────────────────────────────────────────┘   │
 ├──────────────────────────────────────────────────┤
 │               Domain 层                           │
 │   ┌──────────────────────────────────────────┐   │
 │   │  11 个 Manager/Engine                     │   │
 │   │  PlaybackEngine      PlayerControlsManager│   │
 │   │  DanmakuManager      GestureHandler       │   │
 │   │  Anime4KManager      SeriesManager        │   │
 │   │  FilePickerManager   PlayerDialogManager  │   │
 │   │  SkipIntroOutroMgr   ScreenshotManager    │   │
 │   ├──────────────────────────────────────────┤   │
 │   │  辅助 Domain: sniffer/ subtitle/ webdav/  │   │
 │   │  media/ (FolderBrowser/TreeNavigation)    │   │
 │   └──────────────────────────────────────────┘   │
 ├──────────────────────────────────────────────────┤
 │              Service 层                           │
 │   ┌──────────────────────────────────────────┐   │
 │   │  BackgroundPlaybackService               │   │
 │   │  前台 Service，维持后台播放进程存活        │   │
 │   └──────────────────────────────────────────┘   │
 ├──────────────────────────────────────────────────┤
 │              Repository 层                        │
 │   ┌──────────────────────────────────────────┐   │
 │   │  6 个 Repository                          │   │
 │   │  PlayerRepository   VideoRepository       │   │
 │   │  BilibiliRepository                       │   │
 │   │  WebDavRepository   SubtitleRepository    │   │
 │   │  MediaInfoRepository                      │   │
 │   └──────────────────────────────────────────┘   │
 ├──────────────────────────────────────────────────┤
 │                Data 层                            │
 │   ┌──────────┬──────────┬──────────┬──────────┐  │
 │   │  Room DB │ SharedPref│ 网络 API │ 本地 AAR │  │
 │   │  3 表     │ Preferences│ B站/弹幕 │ mpv      │  │
 │   │          │ Manager   │ WebDAV  │ Danmaku   │  │
 │   │          │           │         │ MediaInfo │  │
 │   └──────────┴──────────┴──────────┴──────────┘  │
 ├──────────────────────────────────────────────────┤
 │            DI 层 (Koin)                           │
 │   ┌──────────────────────────────────────────┐   │
 │   │ AppModule  DatabaseModule  NetworkModule  │   │
 │   │ RepositoryModule  DomainModule            │   │
 │   │ PresentationModule (6 模块)               │   │
 │   └──────────────────────────────────────────┘   │
 └──────────────────────────────────────────────────┘
```

### 架构特点

| 特点 | 说明 |
|------|------|
| **Manager 模式** | 不用 UseCase，以 Manager/Engine 封装业务逻辑，职责单一、边界清晰 |
| **Koin DI** | 轻量级依赖注入，5 个模块解耦各层 |
| **StateFlow 驱动 UI** | ViewModel 暴露 StateFlow，UI 层通过 `collectAsState()` 响应 |
| **WeakReference 防泄漏** | Activity 内创建的 Manager 一律用 `WeakReference` 持有 |
| **双 UI 体系** | 播放核心用传统 View（性能稳定），其余页面全量 Compose |
| **Repository 封装** | DAO / 网络 / Preferences 统一走 Repository |
| **后台播放** | 前台 Service 维持进程存活，Activity 生命周期按需跳过暂停/销毁 |

---

## 3. 技术栈

### 开发环境

| 工具 | 版本 |
|------|------|
| Kotlin | 2.3.21 |
| AGP | 8.7.3 |
| KSP | 2.3.7 |
| compileSdk | 36 |
| minSdk | 26 |
| targetSdk | 34 |
| NDK | 25.2.9519653 |

### 核心依赖

| 类别 | 库 | 版本 | 用途 |
|------|-----|------|------|
| **DI** | Koin | 4.0.0 | 依赖注入 |
| **UI (Compose)** | Compose BOM | 2024.09.03 | Compose UI 框架 |
| | Navigation Compose | 2.8.2 | 类型安全导航 |
| | Material3 | BOM 管理 | Material Design 3 |
| **UI (传统)** | AppCompat | 1.7.0 | 传统 View 兼容 |
| | RecyclerView | 1.3.2 | 列表 |
| | Material | 1.12.0 | MDC 组件 |
| **播放器** | mpv-android-lib | 0.1.10 | 播放引擎 (AAR) |
| **弹幕** | DanmakuFlameMaster | - | 弹幕渲染 (AAR) |
| **数据库** | Room | 2.6.1 | 本地持久化 |
| **网络** | OkHttp | 4.12.0 | HTTP 客户端 |
| | Jsoup | 1.18.1 | HTML 解析 |
| **图片** | Coil | 2.7.0 | Compose 图片加载 |
| | Glide | 4.16.0 | View 图片加载 |
| **协程** | Kotlinx Coroutines | 1.9.0 | 异步 |
| **序列化** | Kotlinx Serialization | 1.7.3 | JSON / 导航路由 |
| **媒体信息** | MediaInfoAndroid | 1.0.0-fix | 媒体元数据 (AAR) |
| **串流嗅探** | Seeker | 2.0.1 | 视频流探测 (AAR) |
| **WebDAV** | Sardine | 1.0.2 | WebDAV 客户端 (JAR) |
| **分页** | Paging 3 | 3.3.2 | 大列表分页加载 |
| **WorkManager** | Work Runtime | 2.9.1 | 后台任务 |
| **ZXing** | Core | 3.5.3 | 二维码扫码 |
| **安全** | Security Crypto | 1.1.0-alpha06 | 敏感数据加密存储 |
| **开源许可** | AboutLibraries | 11.2.3 | 开源许可展示 |
| **Gson** | Gson | 2.11.0 | JSON 解析 (兼容) |

---

## 4. 分层详解

### 4.1 UI 层

**位置**: `ui/`, `player/`, `danmaku/`, `mediainfo/` 及顶层 Activity

UI 层分为两个子系统：

#### 传统 View 子系统

| 文件 | 职责 |
|------|------|
| `VideoPlayerActivity` | 播放器主 Activity，集成 mpv View、弹幕 View、控制栏 |
| `VideoPlayerActivity+Setup` | 初始化、Manager 创建、杜比视界检测 |
| `VideoPlayerActivity+Playback` | 播放控制、均衡器恢复 |
| `VideoPlayerActivity+UI` | UI 相关操作 |
| `VideoPlayerActivity+Observers` | MPV 事件观察 |
| `VideoPlayerActivity+Danmaku` | 弹幕操作（多服务器匹配、来源标注选择、网络弹幕加载） |
| `VideoPlayerActivity+Subtitle` | 字幕操作 |
| `VideoPlayerActivity+Orientation` | 横竖屏切换 |
| `CustomMPVView` | mpv 播放器 Surface 封装，配置解码/字幕/音视频参数/GPU Next |
| `DanmakuPlayerView` | 弹幕渲染 View |
| `CustomSeekbar` | 自定义播放进度条 |
| `DoubleTapSeekIndicator` | 双击快进指示器 |

#### Compose 子系统

| 目录 | 职责 |
|------|------|
| `ui/screens/` | 27 个全屏页面（Home、Settings、Library、DanmakuServer 等），含 `dialogs/` 子目录 |
| `ui/components/` | 12 个可复用 Compose 组件（弹窗、卡片、TopAppBar、SliderItem、SortOption 等） |
| `ui/player/` | 8 个播放器控制面板 Compose（PlayerControls、PortraitControls、GestureIndicators 等） |
| `ui/theme/` | 主题（亮/暗/跟随系统）、颜色、排版 |
| `ui/webdav/` | WebDAV 相关页面 |
| `ui/viewmodels/` | UI 层 ViewModel（BiliBiliDanmakuViewModel） |
| `manager/compose/` | 9 个 Compose 抽屉/弹窗（弹幕设置、字幕设置、视频列表、章节、倍速、均衡器、片头片尾、杜比视界提示、文件选择器） |

### 4.2 Presentation 层

**位置**: `presentation/`

| ViewModel | 职责 |
|-----------|------|
| `PlayerViewModel` | 播放器核心状态（播放/暂停、进度、速度、解码、弹幕、字幕等） |
| `LibraryViewModel` | 视频库列表、扫描 |
| `SettingsViewModel` | 应用设置 |
| `PlaybackSettingsViewModel` | 播放相关设置 |
| `BilibiliViewModel` | B 站用户状态、番剧搜索 |
| `BangumiIndexViewModel` | 番剧索引（筛选、分页、搜索） |
| `BangumiDetailViewModel` | 番剧详情、选集播放 |
| `WebDavViewModel` | WebDAV 文件浏览 |
| `SubtitleSearchViewModel` | 字幕在线搜索 |
| `PlaybackHistoryViewModel` | 播放历史 |
| `CacheManagementViewModel` | 缓存管理 |
| `MediaInfoViewModel` | 媒体信息 |
| `LogViewerViewModel` | 日志查看 |
| `TVBrowserViewModel` | TV 模式浏览器 |

**StateFlow 驱动模式**:

```
ViewModel
  │
  ├── val paused: StateFlow<Boolean>
  ├── val progress: StateFlow<Pair<Double, Double>>
  ├── val currentSheet: StateFlow<Sheets>
  ├── val currentPanel: StateFlow<Panels>
  ├── val playerUpdates: SharedFlow<PlayerUpdates>
  │
  └── UI 层 collectAsState()
```

**PlayerUpdates (SharedFlow)** — 一次性事件（速度变化提示、音量变化提示等），用 SharedFlow 避免重放。

### 4.3 Domain 层

**位置**: `domain/`

这是项目的核心业务逻辑层，以 Manager/Engine 模式组织。

| Manager | 职责 | 生命周期 |
|---------|------|----------|
| `PlaybackEngine` | mpv 播放控制、进度轮询、事件回调 | Activity 绑定 |
| `PlayerControlsManager` | 控制栏显隐、按钮事件、系统 UI 管理 | Activity 绑定 |
| `GestureHandler` | 手势识别（滑动/双击/长按/拖动/长按动态调速） | Activity 绑定 |
| `PlayerDialogManager` | 底部弹窗（速度/比例/字幕/音轨等） | Activity 绑定 |
| `DanmakuManager` | 弹幕加载、状态机管理、样式控制 | Activity 绑定 |
| `Anime4KManager` | Anime4K 着色器加载与切换 | 全局单例 |
| `SeriesManager` | 同系列视频识别与导航 | Activity 绑定 |
| `SubtitleManager` | 字幕解析与显示控制 | 无状态 (factory) |
| `FilePickerManager` | 字幕/弹幕文件选择 | Activity 绑定 |
| `VideoSelector` | 串流协议视频选择 | 无状态 |
| `SkipIntroOutroManager` | 片头片尾自动跳过 | Activity 绑定 |

辅助 Domain：
| Manager | 职责 |
|---------|------|
| `FolderBrowserManager` | 文件夹浏览器（过滤、排序） |
| `MediaScanManager` | 媒体文件扫描 |
| `TreeNavigationManager` | 目录树导航 |
| `VideoBrowserManager` | 视频浏览器 |
| `WebDavClient` | WebDAV 协议客户端 |

### 4.4 Repository 层

**位置**: `repository/`

| Repository | 职责 |
|------------|------|
| `PlayerRepository` | 播放历史 CRUD、播放设置读写 |
| `VideoRepository` | 视频文件扫描、缓存管理 |
| `BilibiliRepository` | B 站 API、DanDanPlay 弹幕搜索 |
| `WebDavRepository` | WebDAV 文件列表与操作 |
| `SubtitleRepository` | 字幕在线搜索（对接多个源） |
| `MediaInfoRepository` | 媒体文件元信息 |

注：番剧模块的 Repository 位于 `bilibili/repository/BangumiRepository.kt`。

### 4.5 Data 层

**位置**: `database/`, `preferences/`, `data/`, `danmaku/`, `bilibili/`, `dandanplay/`

#### Room 数据库

```
VideoDatabase (v4)
  ├── video_cache             — 视频文件缓存记录
  │   ├── path, name, size, duration, folderName
  │   ├── folderPath, lastScanned, dateAdded
  │   ├── nameSortKey (自然排序)
  │   └── 索引: folderName, (folderPath, name), lastScanned
  │
  ├── playback_history      — 播放历史
  │   ├── uri, fileName, position, duration
  │   ├── lastPlayed, folderName
  │   ├── danmuPath, danmuVisible, danmuOffsetTime
  │   └── 索引: lastPlayed DESC
  │
  └── playback_state       — 播放状态（快速查询进度）
      ├── uri, position, duration
      └── 索引: uri UNIQUE
```

#### SharedPreferences

`PreferencesManager` 统一管理所有设置项（50+ 配置项）：
- 播放设置（快进时长、倍速、解码方式、章节控制、进度条样式、缩略图、OP/ED 跳过）
- 播放界面动画（控制栏动画、抽屉动画，默认关闭）
- UI 设置（主题模式、显示模式、视频列表进度条）
- 弹幕设置（大小、速度、透明度、行数限制、多服务器列表）
- 字幕设置（延迟、缩放、颜色、描边）
- 黑名单、更新设置、手势配置、灵敏度等
- 功能引导标记（如长按动态调速已使用）

#### 本地库 (libs/)

| 文件 | 用途 |
|------|------|
| `mpv-android-lib-v0.1.10.aar` | mpv 播放器 Android 封装 |
| `mpvlib.aar` | mpv 核心库 |
| `DanmakuFlameMaster.aar` | 弹幕渲染引擎 |
| `mediainfoAndroid-v1.0.0-fix.aar` | 媒体元信息解析 |
| `seeker-2.0.1.aar` | 文件搜索/流探测 |
| `sardine-1.0.2.jar` | WebDAV 客户端 |
| `simple-xml-2.7.1.jar` | XML 序列化 |

#### 网络 API

- **Bilibili API**: 番剧信息、视频流解析、二维码登录
- **DanDanPlay API**: 弹幕搜索与下载（支持自定义服务器）
- **WebDAV**: 远程文件管理

### 4.6 Service 层

**位置**: `service/`

| 组件 | 职责 |
|------|------|
| `BackgroundPlaybackService` | 前台 Service，在"听视频"模式下维持进程存活。使用 `Notification.Builder` 构建通知，`START_STICKY` 重启策略。 |

### 4.7 DI 层

**位置**: `di/`

Koin 5 个模块：

```kotlin
val appModules = listOf(
    databaseModule,      // Room 数据库、DAO 单例
    networkModule,       // BiliBiliAuthManager、DanDanPlayApi (factory 模式，支持多服务器)
    repositoryModule,    // 所有 Repository 单例
    domainModule,        // PreferencesManager、Anime4KManager 等
    presentationModule   // 所有 ViewModel
)
```

需要 Activity 引用的 Manager（如 `PlaybackEngine`）不在 DI 中注册，而是由 `VideoPlayerActivity` 在 `initializeManagers()` 中手动构造。

---

## 5. 导航系统

**位置**: `navigation/`

使用 **Navigation Compose** + **Kotlin Serialization 类型安全路由**：

```kotlin
sealed interface AppScreen {
    @Serializable data object Home : AppScreen
    @Serializable data object Settings : AppScreen
    @Serializable data class WebDavBrowser(val accountId: String) : AppScreen
    @Serializable data class Player(val videoUri: String) : AppScreen
    @Serializable data class BangumiDetail(val seasonId: Int, val isEpId: Boolean = false) : AppScreen
    @Serializable data object BangumiIndex : AppScreen
    @Serializable data object TVBrowser : AppScreen
    // ...
}
```

- `AppNavGraph` — 主导航图，定义所有页面路由和转场动画
- 导航统一走 Compose，`VideoPlayerActivity` 通过 Intent 启动

---

## 6. 状态管理

### 类型选择

| 类型 | 用途 | 示例 |
|------|------|------|
| `StateFlow` | 持续性状态 | 播放/暂停、进度、速度、列表数据 |
| `SharedFlow` | 一次性事件 | 快进提示、音量变化、Toast |
| `mutableStateOf` | Compose 内部状态 | 抽屉展开/折叠、输入框内容 |

### 状态流

```
mpv 属性变化
    ↓ MPVLib.EventObserver
PlayerViewModel (StateFlow)
    ↓ collectAsState()
Compose UI 重组
```

进度更新采用 **Handler + Runnable 500ms 轮询**（非协程方式），从 mpv 读取 position/duration 后写入 StateFlow。

---

## 7. Manager 模式详解

这是项目最核心的设计模式。Manager 替代了传统 MVVM 中的 UseCase 角色。

### 分类

#### A. 全局单例 Manager（Koin single）

| Manager | 初始化 |
|---------|--------|
| `PreferencesManager` | 手动双重检查锁 + Koin 包装 |
| `Anime4KManager` | Koin single |
| `ThumbnailCacheManager` | 手动双重检查锁 + Koin 包装 |
| `ThemeManager` | Koin factory（无状态） |

#### B. Activity 绑定 Manager（手动构造 + WeakReference）

| Manager | 引用方式 | 原因 |
|---------|----------|------|
| `PlaybackEngine` | `WeakReference<Context>` + `mpvView` | 依赖 mpv View |
| `PlayerControlsManager` | `WeakReference<Activity>` | 操作系统 UI |
| `GestureHandler` | `WeakReference<Activity>` | 触摸事件 |
| `PlayerDialogManager` | `WeakReference<Activity>` + 多个 Manager | 对话框管理 |
| `DanmakuManager` | `danmakuView` | 依赖弹幕 View |
| `FilePickerManager` | `WeakReference<Activity>` | 文件选择 |
| `SkipIntroOutroManager` | 直接持有 Context | 仅 SharedPreferences 操作 |

#### C. 无状态 Manager（Koin factory）

| Manager |
|---------|
| `SubtitleManager` |
| `VideoSelector` |
| `SeriesManager` |

### 初始化流程（VideoPlayerActivity）

```
onCreate()
  │
  ├── initMPV()                    — 初始化 mpv 引擎
  ├── initViews()                  — 绑定 View
  ├── 读取用户设置 / 音量增强状态
  ├── 创建后台播放通知渠道
  ├── initializeManagers()
  │   ├── PlaybackEngine           — 播放控制
  │   ├── PlayerControlsManager    — 控制栏
  │   ├── GestureHandler           — 手势
  │   ├── ComposeOverlayManager    — Compose 弹窗
  │   ├── PlayerDialogManager      — 底部弹窗
  │   ├── FilePickerManager        — 文件选择
  │   ├── DanmakuManager           — 弹幕
  │   ├── Anime4KManager           — Anime4K
  │   ├── ScreenshotManager        — 截图
  │   ├── SkipIntroOutroManager    — 片头片尾跳过
  │   └── bindViewsToManagers()    — 绑定回调
  │
  ├── setupComposeControls()       — Compose 控制面板
  └── loadVideo()                  — 加载视频

后台播放流程（点击"听视频"后）：
  onBackgroundPlayback()
    ├── isManualBackgroundPlayback = true
    ├── 暂停弹幕
    ├── startForegroundService()   → BackgroundPlaybackService
    ├── 发送 Home Intent (Activity 切到后台)
    │
    onStop() → isManualBackgroundPlayback=true → 跳过暂停
    onDestroy() → 不销毁 MPV，保持 Service 存活
    
  返回前台：
    onStart() → endBackgroundPlayback() → 停止 Service
```

---

## 8. 双 UI 体系

| 场景 | 方案 | 原因 |
|------|------|------|
| 视频播放器主界面 | 传统 View (XML) | mpv Surface 是原生 View，控制栏图标/弹窗需精确 Layout 控制 |
| 控制面板覆盖层 | Compose | ComposeOverlayManager 动态注入 Compose 弹窗 |
| 设置/列表/浏览器 | 全量 Compose | Compose 开发效率高，Material3 适配好 |
| 弹幕渲染 | 传统 View (SurfaceView) | 弹幕引擎是 AAR 库，必须用 View 集成 |
| TV 模式 | Activity + Compose | TV 焦点导航使用传统方式 |

**ComposeOverlayManager** 是衔接两种 UI 体系的关键：

```kotlin
class ComposeOverlayManager(context, lifecycleOwner, rootView: ViewGroup) {
    fun setContent(content: @Composable () -> Unit) {
        // 在 ViewGroup 上叠加 ComposeView
    }
    fun clearContent() { ... }
}
```

播放器主界面之所以保留传统 View，是因为：
- mpv 播放 Surface 是原生 View，Compose 无法直接替代
- 控制栏按钮、弹幕 View、进度条等需要精确的 Z-order 和 Layout 控制
- Compose 作为覆盖层通过 `ComposeOverlayManager` 叠加在 View 体系之上

---

## 9. 内存管理策略

### WeakReference 模式

所有需要持有 Activity/Context 引用的 Manager 都使用 `WeakReference`：

```kotlin
class PlayerControlsManager(
    private val activityRef: WeakReference<AppCompatActivity>,
    ...
)
class PlaybackEngine(
    private val contextRef: WeakReference<Context>,
    ...
)
```

### 生命周期管理

- `initializeManagers()` — 在 `onCreate()` 中创建所有 Manager
- `releaseManagers()` — 在 `onDestroy()` 中释放
- 所有 `internal lateinit var` — 保证声明周期内必有值

### 数据库连接预热

`AppApplication.onCreate()` 中后台协程提前初始化 Room 数据库连接，减少首次查询延迟。

---

## 10. 构建与配置

### 版本号方案

```
versionCode = baseVersion * 10 + abiCode

abiCode:
  arm64-v8a  → 2
  armeabi-v7a → 1
```

当前: `versionCode = 29`, `versionName = "1.2.9"`

### ABI 拆分

只构建 `arm64-v8a`，不生成 universal APK：

```kotlin
splits {
    abi {
        isEnable = true
        reset()
        include("arm64-v8a")
        isUniversalApk = false
    }
}
```

### ProGuard

Release 构建启用混淆和资源压缩：

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```

### 仓库镜像

使用阿里云镜像加速国内构建：

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/google") }
maven { url = uri("https://maven.aliyun.com/repository/public") }
```

---

## 11. 包结构总览

```
com.fam4k007.videoplayer/  (205 个 .kt 文件, 24 个一级子目录)
│  (17 个顶层 Activity + 核心文件)
│  VideoPlayerActivity.kt          — 播放器主 Activity
│  VideoPlayerActivity+Setup.kt    — 初始化、Manager 创建
│  VideoPlayerActivity+Playback.kt — 播放控制
│  VideoPlayerActivity+UI.kt       — UI 操作
│  VideoPlayerActivity+Observers.kt— MPV 事件观察
│  VideoPlayerActivity+Danmaku.kt  — 弹幕操作
│  VideoPlayerActivity+Subtitle.kt — 字幕操作
│  VideoPlayerActivity+Orientation.kt — 横竖屏
│  MainActivity.kt                 — 主页 Activity
│  AppApplication.kt               — Application 入口
│  AppConstants.kt                 — 常量定义
│  BaseActivity.kt                 — Activity 基类
│  PlaybackHistoryManager.kt       — 播放历史管理器
│  VideoFileParcelable.kt          — 视频文件 Parcelable
│  VideoFolder.kt                  — 视频文件夹模型
│  (About/BiliBiliDanmaku/Download/License/PlaybackHistory/
│   PlaybackSettings/Settings/SubtitleSearch/UserAgreement/
│   VideoBrowser/VideoList)ComposeActivity.kt — 各 Compose 页面 Activity
│
├── di/                            — Koin 依赖注入 (6 模块)
│   ├── AppModule.kt               — 模块入口
│   ├── DatabaseModule.kt          — Room 数据库
│   ├── NetworkModule.kt           — 网络服务
│   ├── RepositoryModule.kt        — 数据仓库
│   ├── DomainModule.kt            — 领域服务
│   └── PresentationModule.kt      — ViewModel
│
├── presentation/                  — 15 个 ViewModel
│   ├── PlayerViewModel.kt         — 播放器核心状态
│   ├── LibraryViewModel.kt        — 视频库
│   ├── SettingsViewModel.kt       — 设置
│   ├── PlaybackSettingsViewModel.kt — 播放设置（含动画开关）
│   ├── BilibiliViewModel.kt       — B站
│   ├── BangumiIndexViewModel.kt   — 番剧索引
│   ├── BangumiDetailViewModel.kt  — 番剧详情
│   ├── WebDavViewModel.kt         — WebDAV
│   ├── PlaybackHistoryViewModel.kt
│   ├── SubtitleSearchViewModel.kt
│   ├── MediaInfoViewModel.kt
│   ├── DeviceInfoViewModel.kt
│   ├── CacheManagementViewModel.kt
│   ├── LogViewerViewModel.kt
│   └── TVBrowserViewModel.kt
│
├── domain/                        — 业务逻辑 (11 Manager + 5 辅助)
│   ├── player/                    — 播放核心 (7)
│   │   ├── PlaybackEngine.kt      — mpv 播放控制
│   │   ├── PlayerControlsManager.kt — 控制栏显隐
│   │   ├── PlayerDialogManager.kt — 底部弹窗管理
│   │   ├── GestureHandler.kt      — 手势识别（View 版）
│   │   ├── Anime4KManager.kt      — Anime4K 着色器
│   │   ├── SeriesManager.kt       — 同系列识别
│   │   └── FilePickerManager.kt   — 文件选择
│   ├── danmaku/                   — 弹幕
│   │   └── DanmakuManager.kt
│   ├── subtitle/                  — 字幕
│   │   └── SubtitleManager.kt
│   ├── sniffer/                   — 串流嗅探
│   │   └── VideoSelector.kt
│   ├── webdav/                    — WebDAV
│   │   └── WebDavClient.kt
│   └── media/                     — 媒体浏览
│       ├── FolderBrowserManager.kt
│       ├── MediaScanManager.kt
│       ├── TreeNavigationManager.kt
│       └── VideoBrowserManager.kt
│
├── repository/                    — 6 个 Repository
│   ├── PlayerRepository.kt
│   ├── VideoRepository.kt
│   ├── BilibiliRepository.kt
│   ├── WebDavRepository.kt
│   ├── SubtitleRepository.kt
│   └── MediaInfoRepository.kt
│
├── database/                      — Room 数据库 (3 表)
│   ├── VideoDatabase.kt
│   ├── VideoCacheDao.kt / Entity.kt
│   ├── PlaybackHistoryDao.kt / Entity.kt
│   └── PlaybackState.kt
│
├── preferences/                   — 设置管理器
│   └── PreferencesManager.kt      — 50+ 配置项统一管理
│
├── manager/                       — 功能管理器 + Compose 覆盖层
│   ├── ScreenshotManager.kt       — 截图
│   ├── SkipIntroOutroManager.kt   — 片头片尾跳过
│   ├── ThemeManager.kt            — 主题
│   ├── VideoThumbnailManager.kt   — 视频缩略图
│   └── compose/                   — 9 个抽屉/弹窗
│       ├── ComposeOverlayManager.kt — 覆盖层管理器（含全局动画开关）
│       ├── ChapterDrawer.kt       — 章节抽屉（冻结标题+横线分隔）
│       ├── SpeedDrawer.kt         — 倍速选择抽屉
│       ├── EqualizerDrawer.kt     — 均衡器抽屉
│       ├── SkipSettingsDrawer.kt  — 片头片尾设置
│       ├── VideoListDrawer.kt     — 视频列表抽屉
│       ├── DanmakuDialogs.kt      — 弹幕设置（冻结标题+横线分隔）
│       ├── DanmakuFilePickerDialog.kt — 弹幕文件选择
│       ├── SubtitleDialogs.kt     — 字幕设置
│       ├── SubtitleFilePickerDialog.kt — 字幕文件选择
│       └── DolbyVisionHintDialog.kt — 杜比视界提示
│
├── navigation/                    — 导航
│   ├── AppNavGraph.kt
│   └── AppScreen.kt
│
├── ui/                            — Compose UI
│   ├── screens/                   — 27 个全屏页面 + dialogs/
│   │   ├── HomeScreen.kt / SettingsScreen.kt / PlaybackSettingsScreen.kt
│   │   ├── MediaSettingsScreen.kt / PlayerScreen.kt / AboutScreen.kt
│   │   ├── BangumiIndexScreen.kt / BangumiDetailScreen.kt
│   │   ├── BiliBiliLoginScreen.kt / BiliBiliDanmakuScreen.kt
│   │   ├── LibraryScreen.kt / FolderBrowserScreen.kt
│   │   ├── VideoListScreen.kt / VideoListScreenPaging.kt
│   │   ├── PlaybackHistoryScreen.kt / SubtitleSearchScreen.kt
│   │   ├── DeviceInfoScreen.kt / CacheManagementScreen.kt
│   │   ├── DanmakuServerScreen.kt / DownloadScreen.kt
│   │   ├── DownloadManagerScreen.kt / FolderBlacklistScreen.kt
│   │   ├── LogViewerScreen.kt / LicenseScreen.kt
│   │   ├── UserAgreementScreen.kt / TVBrowserScreen.kt
│   │   └── dialogs/ (DisplayModeDialog.kt / ThemeDialogs.kt)
│   ├── components/                — 12 个可复用组件
│   │   ├── Dialogs.kt / Cards.kt / SliderItem.kt / PreferenceItems.kt
│   │   ├── ImmersiveTopAppBar.kt / UpdateDialog.kt
│   │   ├── DanDanPlaySearchDialog.kt / ExpandableCard.kt
│   │   ├── FileOperationMenu.kt / MultiSelectActionBar.kt
│   │   ├── CommonStates.kt / SortOption.kt
│   ├── player/                    — 8 个播放器控件
│   │   ├── PlayerControlsCompose.kt  — 横屏控制面板
│   │   ├── PortraitControls.kt       — 竖屏控制面板
│   │   ├── CustomSeekbar.kt          — 自定义进度条
│   │   ├── GestureHandler.kt         — 手势 (Compose 版)
│   │   ├── GestureIndicators.kt      — 音量/亮度指示器（深色背景风格）
│   │   ├── SeekIndicator.kt          — 快进/快退指示器
│   │   ├── SeekbarStyle.kt           — 进度条样式定义
│   │   └── SeekbarThumbnailPreview.kt — 缩略图预览
│   ├── theme/                     — 5 个主题文件
│   ├── webdav/                    — WebDAV 页面
│   └── viewmodels/                — UI 层 ViewModel
│
├── player/                        — mpv 播放器 View (传统)
│   ├── CustomMPVView.kt
│   ├── DoubleTapSeekIndicator.kt
│   ├── SkipSegment.kt
│   └── VideoAspect.kt
│
├── danmaku/                       — 弹幕 View + 下载
│   ├── DanmakuPlayerView.kt
│   ├── DanmakuConfig.kt
│   └── BiliBiliDanmakuDownloadManager.kt
│
├── bilibili/                      — B 站 API
│   ├── api/BiliBangumiApi.kt     — 番剧 API
│   ├── auth/BiliBiliAuthManager.kt — 认证管理
│   ├── model/ (4 个模型文件)     — 数据模型
│   └── repository/BangumiRepository.kt — 番剧数据仓库
│
├── tv/                            — TV 浏览器
│   └── TVBrowserActivity.kt
│
├── dandanplay/                    — DanDanPlay API
│   ├── DanDanPlayApi.kt           — 弹幕 API 客户端
│   ├── DanDanPlayModels.kt        — 数据模型
│   └── DanmakuServer.kt           — 服务器配置
│
├── remote/                        — 远程播放 (5 文件)
├── download/                      — 下载管理 (5 文件)
├── subtitle/                      — 字幕下载
├── sniffer/                       — 视频嗅探 (3 文件)
├── service/                       — 后台 Service
├── utils/                         — 工具类 (16 文件 + media/ 子目录)
├── worker/                        — WorkManager Worker
└── paging/                        — 分页数据源
```
