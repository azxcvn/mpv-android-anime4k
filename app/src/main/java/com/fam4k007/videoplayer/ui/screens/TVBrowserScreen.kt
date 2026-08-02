package com.fam4k007.videoplayer.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fam4k007.videoplayer.presentation.TVBrowserViewModel
import com.fam4k007.videoplayer.sniffer.VideoSnifferManager
import org.koin.androidx.compose.koinViewModel

/**
 * TV浏览器界面
 * 带视频嗅探功能的WebView浏览器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TVBrowserScreen(
    initialUrl: String,
    onWebViewCreated: (WebView) -> Unit,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: TVBrowserViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 初始化URL
    LaunchedEffect(initialUrl) {
        viewModel.initUrl(initialUrl)
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (state.showUrlBar) "Enter URL" else state.currentTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        // 地址栏切换按钮
                        IconButton(onClick = { viewModel.toggleUrlBar() }) {
                            Icon(
                                if (state.showUrlBar) Icons.Default.Close else Icons.Default.Search,
                                if (state.showUrlBar) "Close URL Bar" else "Open URL Bar"
                            )
                        }
                        // 播放按钮 - 智能选择最佳视频
                        Box {
                            IconButton(
                                onClick = { viewModel.playBestVideo(context) },
                                enabled = state.detectedVideos.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.PlayCircle,
                                    "Play Video"
                                )
                            }
                            // 右上角红点提示
                            if (state.detectedVideos.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-8).dp, y = 8.dp)
                                        .background(MaterialTheme.colorScheme.error, CircleShape)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // 地址栏
                AnimatedVisibility(
                    visible = state.showUrlBar,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        OutlinedTextField(
                            value = state.urlInput,
                            onValueChange = { viewModel.updateUrlInput(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Enter URL") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { viewModel.navigateToUrl() }),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (state.urlInput.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.clearUrlInput() }) {
                                            Icon(Icons.Default.Clear, "Clear")
                                        }
                                    }
                                    IconButton(onClick = { viewModel.navigateToUrl() }) {
                                        Icon(
                                            Icons.Default.Send,
                                            "Go",
                                            tint = if (state.urlInput.isNotEmpty())
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // 移除悬浮播放按钮，因为现在自动播放
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            if (state.urlToLoad.isNotEmpty()) {
                // 本地持有WebView引用，用于响应urlToLoad变化
                var localWebView by remember { mutableStateOf<WebView?>(null) }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setupWebView(
                                onUrlChanged = { url -> viewModel.onUrlChanged(url) },
                                onTitleChanged = { title -> viewModel.onTitleChanged(title) },
                                onLoadingChanged = { loading -> viewModel.onLoadingChanged(loading) },
                                onPageUrlChanged = { pageUrl, pageTitle ->
                                    viewModel.onPageUrlChanged(pageUrl, pageTitle)
                                },
                                getCurrentPageUrl = { viewModel.getCurrentPageUrl() },
                                getCurrentPageTitle = { viewModel.getCurrentPageTitle() }
                            )
                            loadUrl(state.urlToLoad)
                            localWebView = this
                            onWebViewCreated(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { _ -> }
                )
                // 只在urlToLoad值实际变化时才重新加载WebView（用户主动输入新地址）
                LaunchedEffect(state.urlToLoad) {
                    val url = state.urlToLoad
                    if (url.isNotEmpty()) {
                        localWebView?.loadUrl(url)
                    }
                }
            } else {
                // 没有URL时显示空白
                Box(modifier = Modifier.fillMaxSize())
            }

            // 加载指示器
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

/**
 * 配置WebView
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.setupWebView(
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onPageUrlChanged: (String, String) -> Unit,
    getCurrentPageUrl: () -> String,
    getCurrentPageTitle: () -> String
) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // 设置User-Agent
        userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            url?.let {
                onUrlChanged(it)
                onLoadingChanged(true)
                Log.d("TVBrowser", "Page started: $it")

                // 页面开始加载，准备检测新页面
                val title = view?.title ?: "TV Browser"
                onPageUrlChanged(it, title)
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.title?.let { onTitleChanged(it) }
            onLoadingChanged(false)
            Log.d("TVBrowser", "Page finished: $url, title: ${view?.title}")
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            request?.let { req ->
                val url = req.url.toString()
                val headers = req.requestHeaders
                // 使用回调获取页面信息，避免在后台线程访问WebView
                val pageUrl = getCurrentPageUrl()
                val pageTitle = getCurrentPageTitle()

                // 处理请求，检测视频
                VideoSnifferManager.processRequest(url, headers, pageUrl, pageTitle)
            }

            return super.shouldInterceptRequest(view, request)
        }
    }
}
