package com.fam4k007.videoplayer.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.fam4k007.videoplayer.PlaybackHistoryManager
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.VideoPlayerActivity
import com.fam4k007.videoplayer.domain.webdav.WebDavClient
import com.fam4k007.videoplayer.domain.webdav.WebDavConfig
import com.fam4k007.videoplayer.presentation.PlaybackHistoryViewModel
import com.fam4k007.videoplayer.presentation.PlaybackSettingsViewModel
import com.fam4k007.videoplayer.presentation.SubtitleSearchViewModel
import com.fam4k007.videoplayer.repository.WebDavRepository
import com.fam4k007.videoplayer.ui.screens.AboutScreen
import com.fam4k007.videoplayer.ui.screens.BiliBiliDanmakuScreen
import com.fam4k007.videoplayer.ui.screens.CacheManagementScreen
import com.fam4k007.videoplayer.ui.screens.DownloadManagerScreen
import com.fam4k007.videoplayer.ui.screens.DownloadScreen
import com.fam4k007.videoplayer.ui.screens.FolderBrowserScreen
import com.fam4k007.videoplayer.ui.screens.TVBrowserScreen
import com.fam4k007.videoplayer.ui.screens.VideoListScreen
import com.fam4k007.videoplayer.ui.screens.BiliBiliLoginScreen
import com.fam4k007.videoplayer.ui.screens.HomeScreen
import com.fam4k007.videoplayer.ui.screens.LicenseScreen
import com.fam4k007.videoplayer.ui.screens.UserAgreementScreen
import com.fam4k007.videoplayer.ui.screens.LogViewerScreen
import com.fam4k007.videoplayer.ui.screens.UserAgreementScreen
import com.fam4k007.videoplayer.ui.screens.PlaybackHistoryScreen
import com.fam4k007.videoplayer.ui.screens.PlaybackSettingsScreen
import com.fam4k007.videoplayer.ui.screens.FolderBlacklistScreen
import com.fam4k007.videoplayer.ui.screens.SettingsScreen
import com.fam4k007.videoplayer.ui.screens.MediaSettingsScreen
import com.fam4k007.videoplayer.ui.screens.DanmakuServerScreen
import com.fam4k007.videoplayer.ui.screens.DeviceInfoScreen
import com.fam4k007.videoplayer.ui.screens.SubtitleSearchScreen
import com.fam4k007.videoplayer.ui.screens.BangumiIndexScreen
import com.fam4k007.videoplayer.ui.screens.BangumiDetailScreen
import com.fam4k007.videoplayer.ui.webdav.WebDavAccountListScreen
import com.fam4k007.videoplayer.ui.webdav.WebDavBrowserScreen
import com.fam4k007.videoplayer.ui.viewmodels.BiliBiliDanmakuViewModel
import com.fam4k007.videoplayer.mediainfo.MediaInfoScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * 应用主导航图
 * 管理 Home、Settings、PlaybackSettings、PlaybackHistory、About 之间的导航
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    historyManager: PlaybackHistoryManager,
) {
    val transitionDuration = 300

    NavHost(
        navController = navController,
        startDestination = AppScreen.Home,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(transitionDuration)
            ) + fadeIn(animationSpec = tween(transitionDuration))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(transitionDuration)
            ) + fadeOut(animationSpec = tween(transitionDuration))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(transitionDuration)
            ) + fadeIn(animationSpec = tween(transitionDuration))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(transitionDuration)
            ) + fadeOut(animationSpec = tween(transitionDuration))
        }
    ) {
        composable<AppScreen.Home> {
            HomeScreen(
                historyManager = historyManager,
                onNavigateToSettings = {
                    navController.navigate(AppScreen.Settings)
                },
                onNavigateToWebDav = {
                    navController.navigate(AppScreen.WebDavAccounts)
                },
                onNavigateToVideoBrowser = {
                    navController.navigate(AppScreen.VideoBrowser)
                },
                onNavigateToBiliBiliPlay = {
                    navController.navigate(AppScreen.BangumiIndex)
                },
                onNavigateToTVBrowser = {
                    navController.navigate(AppScreen.TVBrowser(initialUrl = ""))
                },
                onNavigateToBiliBiliLogin = {
                    navController.navigate(AppScreen.BiliBiliLogin)
                }
            )
        }

        composable<AppScreen.Settings> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlaybackSettings = {
                    navController.navigate(AppScreen.PlaybackSettings)
                },
                onNavigateToPlaybackHistory = {
                    navController.navigate(AppScreen.PlaybackHistory)
                },
                onNavigateToAbout = {
                    navController.navigate(AppScreen.About)
                },
                onNavigateToBiliBiliDanmaku = {
                    navController.navigate(AppScreen.BiliBiliDanmaku)
                },
                onNavigateToDownload = {
                    navController.navigate(AppScreen.Download)
                },
                onNavigateToSubtitleSearch = {
                    navController.navigate(AppScreen.SubtitleSearch)
                },
                onNavigateToFolderBlacklist = {
                    navController.navigate(AppScreen.FolderBlacklist)
                },
                onNavigateToMediaSettings = {
                    navController.navigate(AppScreen.MediaSettings)
                },
                onNavigateToDeviceInfo = {
                    navController.navigate(AppScreen.DeviceInfo)
                },
                onNavigateToDanmakuServer = {
                    navController.navigate(AppScreen.DanmakuServerManagement)
                }
            )
        }

        composable<AppScreen.DanmakuServerManagement> {
            DanmakuServerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<AppScreen.DeviceInfo> {
            DeviceInfoScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<AppScreen.MediaSettings> {
            MediaSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<AppScreen.PlaybackSettings> {
            val viewModel = koinViewModel<PlaybackSettingsViewModel>()
            PlaybackSettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<AppScreen.PlaybackHistory> {
            val context = LocalContext.current
            val viewModel = koinViewModel<PlaybackHistoryViewModel>()
            PlaybackHistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPlayVideo = { uri, startPosition ->
                    val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                        data = uri
                        putExtra("lastPosition", startPosition)
                    }
                    context.startActivity(intent)
                    (context as? android.app.Activity)?.overridePendingTransition(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                    )
                }
            )
        }

        composable<AppScreen.WebDavAccounts> {
            WebDavAccountListScreen(
                onNavigateBack = { navController.popBackStack() },
                onAccountSelected = { account ->
                    navController.navigate(AppScreen.WebDavBrowser(accountId = account.id))
                }
            )
        }

        composable<AppScreen.WebDavBrowser> { backStackEntry ->
            val context = LocalContext.current
            val repository: WebDavRepository = koinInject()
            val args = backStackEntry.toRoute<AppScreen.WebDavBrowser>()
            val account = repository.getAccountById(args.accountId)

            if (account == null) {
                // 账户不存在，返回
                navController.popBackStack()
            } else {
                WebDavBrowserScreen(
                    account = account,
                    onNavigateBack = { navController.popBackStack() },
                    onPlayVideo = { file, client ->
                        val config = client.config
                        // 通过本地 HTTP 代理播放，解决直接嵌入凭据 URL 的兼容性问题
                        val proxy = com.fam4k007.videoplayer.domain.webdav.WebDavStreamingProxy.getInstance()
                        val streamId = "${account.id}_${System.currentTimeMillis()}"
                        val mimeType = when {
                            file.name.endsWith(".mkv", true) -> "video/x-matroska"
                            file.name.endsWith(".webm", true) -> "video/webm"
                            file.name.endsWith(".ts", true) -> "video/mp2t"
                            else -> "video/mp4"
                        }
                        val proxyUrl = proxy.registerStream(
                            streamId = streamId,
                            config = config,
                            filePath = file.path,
                            fileSize = file.size,
                            mimeType = mimeType
                        )

                        val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                            data = Uri.parse(proxyUrl)
                            putExtra("video_title", file.name)
                            putExtra("is_webdav", true)
                            putExtra("webdav_account_id", account.id)
                            putExtra("webdav_file_path", file.path)
                            putExtra("webdav_stream_id", streamId)
                        }
                        context.startActivity(intent)
                        (context as? android.app.Activity)?.overridePendingTransition(
                            R.anim.slide_in_right,
                            R.anim.slide_out_left
                        )
                    }
                )
            }
        }

        composable<AppScreen.About> {
            val context = LocalContext.current
            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
                } catch (e: Exception) {
                    "Unknown"
                }
            }
            AboutScreen(
                versionName = versionName,
                onBack = { navController.popBackStack() },
                onNavigateToLicense = {
                    navController.navigate(AppScreen.License)
                },
                onNavigateToLogs = {
                    navController.navigate(AppScreen.LogViewer)
                },
                onNavigateToCache = {
                    navController.navigate(AppScreen.CacheManagement)
                },
                onNavigateToUserAgreement = {
                    navController.navigate(AppScreen.UserAgreement)
                },
                onSendEmail = {
                    try {
                        val email = "2297065843@qq.com"
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:$email")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                            putExtra(Intent.EXTRA_SUBJECT, "Meow Player Feedback")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            context,
                            "No email app found",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
        
        composable<AppScreen.LogViewer> {
            val viewModel = koinViewModel<com.fam4k007.videoplayer.presentation.LogViewerViewModel>()
            LogViewerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable<AppScreen.CacheManagement> {
            val viewModel = koinViewModel<com.fam4k007.videoplayer.presentation.CacheManagementViewModel>()
            CacheManagementScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable<AppScreen.UserAgreement> {
            UserAgreementScreen(
                showActions = false,
                onAgree = { navController.popBackStack() },
                onDecline = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable<AppScreen.License> {
            LicenseScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppScreen.UserAgreement> {
            UserAgreementScreen(
                showActions = false,
                onAgree = { navController.popBackStack() },
                onDecline = { navController.popBackStack() }
            )
        }

        composable<AppScreen.BiliBiliDanmaku> {
            val context = LocalContext.current
            val viewModel: BiliBiliDanmakuViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val savedFolderUri by viewModel.savedFolderUri.collectAsStateWithLifecycle()
            val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
            val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
            val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
            val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()
            val downloadWholeSeason by viewModel.downloadWholeSeason.collectAsStateWithLifecycle()

            // 处理错误消息
            LaunchedEffect(errorMessage) {
                errorMessage?.let {
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                    viewModel.clearErrorMessage()
                }
            }

            // 处理成功消息
            LaunchedEffect(successMessage) {
                successMessage?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearSuccessMessage()
                }
            }

            BiliBiliDanmakuScreen(
                savedFolderUri = savedFolderUri,
                downloadProgress = downloadProgress,
                isDownloading = isDownloading,
                downloadWholeSeason = downloadWholeSeason,
                onBack = { navController.popBackStack() },
                onFolderSelected = { uri ->
                    viewModel.setFolderUri(uri, context.contentResolver)
                },
                onDownloadDanmaku = { url, wholeSeason ->
                    viewModel.startDownload(url, wholeSeason)
                },
                onModeChanged = { wholeSeason ->
                    viewModel.setDownloadMode(wholeSeason)
                }
            )
        }

        composable<AppScreen.VideoBrowser> {
            val context = LocalContext.current

            // 权限刷新触发器：每次从 Settings 返回时递增，强制重组重新检查权限
            var permissionRefreshTrigger by remember { mutableIntStateOf(0) }

            // 检查存储权限（每次重组时检查）
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }

            // 权限请求启动器
            // Android 11+：通过 StartActivityForResult 跳转 Settings，返回时触发重组检查权限
            // Android 10-：通过 RequestPermission 请求
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ ->
                permissionRefreshTrigger++
            }
            
            val manageFilesLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { _ ->
                // 从 Settings 授权返回后，直接返回首页
                // 下次再进入 VideoBrowser 时会重新检查权限，看到已授权的结果
                permissionRefreshTrigger++
                navController.popBackStack()
            }

            FolderBrowserScreen(
                hasPermission = hasPermission,
                onRequestPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${context.packageName}")
                            manageFilesLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageFilesLauncher.launch(intent)
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                },
                onNavigateBack = { navController.popBackStack() },
                onOpenFolder = { folder ->
                    navController.navigate(
                        AppScreen.VideoList(
                            folderName = folder.folderName,
                            folderPath = folder.folderPath
                        )
                    )
                }
            )
        }

        composable<AppScreen.Download> {
            DownloadScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDownloadManager = {
                    navController.navigate(AppScreen.DownloadManager)
                }
            )
        }

        composable<AppScreen.DownloadManager> {
            DownloadManagerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppScreen.SubtitleSearch> {
            val context = LocalContext.current
            val viewModel: SubtitleSearchViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // 显示Toast消息
            LaunchedEffect(uiState.message) {
                uiState.message?.let { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    viewModel.clearMessage()
                }
            }

            SubtitleSearchScreen(
                savedFolderUri = uiState.savedFolderUri,
                mediaResults = uiState.mediaResults,
                searchResults = uiState.searchResults,
                isSearchingMedia = uiState.isSearchingMedia,
                isSearching = uiState.isSearching,
                searchOptions = uiState.searchOptions,
                selectedMedia = uiState.selectedMedia,
                onBack = { navController.popBackStack() },
                onFolderSelected = { uri ->
                    try {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, flags)
                        viewModel.setFolderUri(uri)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Settings failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                onSearchOptionsChanged = { options -> viewModel.updateSearchOptions(options) },
                onSearchMedia = { query -> viewModel.searchMedia(query) },
                onSelectMedia = { media -> viewModel.selectMedia(media) },
                onDownload = { subtitle -> viewModel.downloadSubtitle(subtitle) },
                onClearSelection = { viewModel.clearSelection() }
            )
        }

        composable<AppScreen.FolderBlacklist> {
            FolderBlacklistScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<AppScreen.BiliBiliLogin> {
            BiliBiliLoginScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<AppScreen.BangumiIndex> {
            BangumiIndexScreen(
                onBack = { navController.popBackStack() },
                onBangumiClick = { seasonId, isEpId ->
                    navController.navigate(AppScreen.BangumiDetail(seasonId, isEpId))
                }
            )
        }

        composable<AppScreen.BangumiDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<AppScreen.BangumiDetail>()
            BangumiDetailScreen(
                seasonId = args.seasonId,
                isEpId = args.isEpId,
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppScreen.TVBrowser> { backStackEntry ->
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val args = backStackEntry.toRoute<AppScreen.TVBrowser>()
            var webView by remember { mutableStateOf<android.webkit.WebView?>(null) }

            // 处理 WebView 生命周期
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> webView?.onResume()
                        Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                        Lifecycle.Event.ON_DESTROY -> {
                            webView?.destroy()
                            webView = null
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            TVBrowserScreen(
                initialUrl = args.initialUrl,
                onWebViewCreated = { webView = it },
                onBackPressed = {
                    if (webView?.canGoBack() == true) {
                        webView?.goBack()
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable<AppScreen.MediaInfo> { backStackEntry ->
            val context = LocalContext.current
            val args = backStackEntry.toRoute<AppScreen.MediaInfo>()

            MediaInfoScreen(
                videoUri = args.videoUri,
                videoName = args.videoName,
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppScreen.VideoList> { backStackEntry ->
            val context = LocalContext.current
            val args = backStackEntry.toRoute<AppScreen.VideoList>()

            VideoListScreen(
                folderName = args.folderName,
                folderPath = args.folderPath,
                onNavigateBack = { navController.popBackStack() },
                onOpenVideo = { video, index, allVideos ->
                    val intent = android.content.Intent(context, VideoPlayerActivity::class.java).apply {
                        data = Uri.parse(video.uri)
                        putExtra("video_name", video.name)
                        putExtra("current_index", index)
                        putExtra("folderName", args.folderName)
                        putParcelableArrayListExtra("video_list", ArrayList(allVideos))
                    }
                    context.startActivity(intent)
                    (context as? android.app.Activity)?.overridePendingTransition(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                    )
                },
                onOpenMediaInfo = { video ->
                    navController.navigate(
                        AppScreen.MediaInfo(
                            videoUri = video.uri,
                            videoName = video.name
                        )
                    )
                }
            )
        }
    }
}
