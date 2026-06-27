package com.fam4k007.videoplayer.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fam4k007.videoplayer.bilibili.auth.BiliBiliAuthManager
import com.fam4k007.videoplayer.bilibili.model.PgcIndexItem
import com.fam4k007.videoplayer.bilibili.model.SearchBangumiItem
import com.fam4k007.videoplayer.presentation.BangumiIndexUiState
import com.fam4k007.videoplayer.presentation.BangumiIndexViewModel
import com.fam4k007.videoplayer.presentation.BangumiFilterState
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel

/**
 * 链接图标（SVG内嵌）
 */
private val LinkIcon: ImageVector
    get() {
        if (_linkIcon == null) {
            _linkIcon = ImageVector.Builder(
                name = "Link",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(11f, 17f)
                    horizontalLineTo(7f)
                    quadTo(4.925f, 17f, 3.463f, 15.538f)
                    quadTo(2f, 14.075f, 2f, 12f)
                    quadTo(2f, 9.925f, 3.463f, 8.462f)
                    quadTo(4.925f, 7f, 7f, 7f)
                    horizontalLineTo(11f)
                    verticalLineTo(9f)
                    horizontalLineTo(7f)
                    quadTo(5.75f, 9f, 4.875f, 9.875f)
                    quadTo(4f, 10.75f, 4f, 12f)
                    quadTo(4f, 13.25f, 4.875f, 14.125f)
                    quadTo(5.75f, 15f, 7f, 15f)
                    horizontalLineTo(11f)
                    close()
                    moveTo(8f, 13f)
                    verticalLineTo(11f)
                    horizontalLineTo(16f)
                    verticalLineTo(13f)
                    close()
                    moveTo(13f, 17f)
                    verticalLineTo(15f)
                    horizontalLineTo(17f)
                    quadTo(18.25f, 15f, 19.125f, 14.125f)
                    quadTo(20f, 13.25f, 20f, 12f)
                    quadTo(20f, 10.75f, 19.125f, 9.875f)
                    quadTo(18.25f, 9f, 17f, 9f)
                    horizontalLineTo(13f)
                    verticalLineTo(7f)
                    horizontalLineTo(17f)
                    quadTo(19.075f, 7f, 20.538f, 8.463f)
                    quadTo(22f, 9.925f, 22f, 12f)
                    quadTo(22f, 14.075f, 20.538f, 15.538f)
                    quadTo(19.075f, 17f, 17f, 17f)
                    close()
                }
            }.build()
        }
        return _linkIcon!!
    }

private var _linkIcon: ImageVector? = null

/**
 * 番剧索引页
 * 分行筛选条件（收起5行/展开10行），番剧网格列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BangumiIndexScreen(
    onBack: () -> Unit,
    onBangumiClick: (seasonId: Int, isEpId: Boolean) -> Unit,
    viewModel: BangumiIndexViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val indexItems by viewModel.indexItems.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasNextPage by viewModel.hasNextPage.collectAsState()

    val gridState = rememberLazyGridState()
    var isFilterExpanded by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    
    // 登录状态检查
    val authManager: BiliBiliAuthManager = koinInject()
    val isLoggedIn = remember { authManager.isLoggedIn() }
    val context = LocalContext.current

    // 监听滚动到底部，加载更多
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                if (visibleItems.isNotEmpty()) {
                    val lastVisibleItem = visibleItems.last()
                    val totalItems = gridState.layoutInfo.totalItemsCount
                    if (lastVisibleItem.index >= totalItems - 3 && hasNextPage && !isLoadingMore) {
                        viewModel.loadNextPage()
                    }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "番剧索引",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isLoggedIn) {
                            showLinkDialog = true
                        } else {
                            Toast.makeText(context, "请在首页左上角登录账号后使用", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            imageVector = LinkIcon,
                            contentDescription = "输入番剧链接",
                            tint = MaterialTheme.colorScheme.onSurface
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
        // 未登录提示
        if (!isLoggedIn) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "此功能需要登录后使用\n请在首页左上角登录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 分行筛选条件区域
            FilterSection(
                filterState = filterState,
                isExpanded = isFilterExpanded,
                onExpandToggle = { isFilterExpanded = !isFilterExpanded },
                onOrderSelected = { viewModel.selectOrder(it) },
                onFilterSelected = { field, keyword -> viewModel.selectFilter(field, keyword) }
            )

            // 内容区域
            when (val state = uiState) {
                is BangumiIndexUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is BangumiIndexUiState.Success, is BangumiIndexUiState.Empty -> {
                    if (indexItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "没有找到番剧",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = gridState,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(indexItems) { index, item ->
                                BangumiCard(
                                    item = item,
                                    onClick = { onBangumiClick(item.season_id, false) }
                                )
                            }

                            // 加载更多指示器
                            if (isLoadingMore) {
                                item(span = { GridItemSpan(3) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is BangumiIndexUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.refresh() }) {
                                Text("重试")
                            }
                        }
                    }
                }
            }
        }
        } // end else

        // 链接输入弹窗
        if (showLinkDialog) {
            LinkInputDialog(
                onDismiss = { showLinkDialog = false },
                onConfirm = { seasonId, isEpId ->
                    showLinkDialog = false
                    onBangumiClick(seasonId, isEpId)
                }
            )
        }
    }
}

/**
 * 链接输入弹窗 - 用户粘贴B站番剧链接后解析跳转
 */
@Composable
private fun LinkInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit
) {
    var linkText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    
    LaunchedEffect(Unit) {
        clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.let { clipText ->
            if (clipText.contains("bilibili.com") && (clipText.contains("ss") || clipText.contains("ep"))) {
                linkText = clipText
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入番剧链接", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "从B站App分享番剧链接后粘贴到此处",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it; errorMsg = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://www.bilibili.com/bangumi/play/ss...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                errorMsg?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val ssRegex = """ss(\d+)""".toRegex()
                val epRegex = """ep(\d+)""".toRegex()
                
                ssRegex.find(linkText)?.let { onConfirm(it.groupValues[1].toInt(), false); return@Button }
                epRegex.find(linkText)?.let { onConfirm(it.groupValues[1].toInt(), true); return@Button }
                
                errorMsg = "无效的番剧链接\n支持格式: .../ss12345"
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/**
 * 分行筛选条件区域
 * 默认显示前5行，展开后显示全部
 */
@Composable
private fun FilterSection(
    filterState: BangumiFilterState,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onOrderSelected: (String) -> Unit,
    onFilterSelected: (String, String) -> Unit
) {
    // 构建所有筛选行
    val filterRows = mutableListOf<FilterRow>()
    
    // 1. 排序条件
    if (filterState.orderList.isNotEmpty()) {
        filterRows.add(
            FilterRow(
                items = filterState.orderList.map { 
                    FilterItem(
                        key = "order_${it.field}",
                        name = it.name,
                        isSelected = filterState.selectedOrder == it.field.toString()
                    )
                },
                type = FilterRowType.ORDER
            )
        )
    }
    
    // 2-10. 其他筛选条件
    filterState.filterGroups.forEach { group ->
        filterRows.add(
            FilterRow(
                items = group.values?.map { value ->
                    FilterItem(
                        key = value.keyword,
                        name = value.name,
                        isSelected = filterState.selectedFilters[group.field] == value.keyword
                    )
                } ?: emptyList(),
                type = FilterRowType.FILTER,
                field = group.field
            )
        )
    }

    if (filterRows.isEmpty()) return

    // 计算显示的行数
    val collapsedCount = 5
    val displayRows = if (isExpanded) filterRows else filterRows.take(collapsedCount)
    val hasMore = filterRows.size > collapsedCount

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .animateContentSize()
    ) {
        // 显示筛选行
        displayRows.forEach { row ->
            FilterRowItem(
                row = row,
                onOrderSelected = onOrderSelected,
                onFilterSelected = onFilterSelected
            )
        }

        // 展开/收起按钮
        if (hasMore) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle() }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpanded) "收起" else "展开全部",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 筛选行类型
 */
private enum class FilterRowType {
    ORDER,  // 排序
    FILTER  // 其他筛选
}

/**
 * 筛选行数据
 */
private data class FilterRow(
    val items: List<FilterItem>,
    val type: FilterRowType,
    val field: String = ""
)

/**
 * 筛选项数据
 */
private data class FilterItem(
    val key: String,
    val name: String,
    val isSelected: Boolean
)

/**
 * 单行筛选条件 - 带圆角背景的选中样式
 */
@Composable
private fun FilterRowItem(
    row: FilterRow,
    onOrderSelected: (String) -> Unit,
    onFilterSelected: (String, String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(row.items.size) { index ->
            val item = row.items[index]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (item.isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .clickable {
                        when (row.type) {
                            FilterRowType.ORDER -> {
                                val field = item.key.removePrefix("order_")
                                onOrderSelected(field)
                            }
                            FilterRowType.FILTER -> {
                                onFilterSelected(row.field, item.key)
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = item.name,
                    fontSize = 11.sp,
                    color = if (item.isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (item.isSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * 番剧卡片 - 参考项目样式：主标题加粗加大，副标题做小标签
 */
@Composable
private fun BangumiCard(
    item: PgcIndexItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
    ) {
        // 封面图区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
        ) {
            // 封面图
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.cover)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // 角标（左上角，如"会员"）
            if (!item.badge.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = item.badge,
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 副标题小标签（封面下方靠左）
            if (!item.index_show.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = item.index_show,
                        color = Color.White,
                        fontSize = 8.sp
                    )
                }
            }
        }

        // 信息区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // 主标题（加粗加大）
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp
            )
        }
    }
}
