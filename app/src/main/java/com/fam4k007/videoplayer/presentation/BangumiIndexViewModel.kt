package com.fam4k007.videoplayer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.bilibili.model.*
import com.fam4k007.videoplayer.bilibili.repository.BangumiRepository
import com.fam4k007.videoplayer.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 番剧索引页ViewModel
 * 管理筛选条件、索引结果、分页等状态
 */
class BangumiIndexViewModel(
    private val bangumiRepository: BangumiRepository
) : ViewModel() {

    companion object {
        private const val TAG = "BangumiIndexViewModel"
        private const val DEFAULT_PAGE_SIZE = 21
    }

    // ==================== UI State ====================

    private val _uiState = MutableStateFlow<BangumiIndexUiState>(BangumiIndexUiState.Loading)
    val uiState: StateFlow<BangumiIndexUiState> = _uiState.asStateFlow()

    private val _filterState = MutableStateFlow<BangumiFilterState>(BangumiFilterState())
    val filterState: StateFlow<BangumiFilterState> = _filterState.asStateFlow()
    

    private val _indexItems = MutableStateFlow<List<PgcIndexItem>>(emptyList())
    val indexItems: StateFlow<List<PgcIndexItem>> = _indexItems.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasNextPage = MutableStateFlow(false)
    val hasNextPage: StateFlow<Boolean> = _hasNextPage.asStateFlow()

    // 当前页码
    private var currentPage = 1

    // 筛选条件数据
    private var orderList: List<OrderItem> = emptyList()
    private var filterGroups: List<FilterGroup> = emptyList()

    // 当前筛选参数
    private var currentParams = mutableMapOf<String, String>()

    init {
        loadIndexCondition()
    }

    // ==================== 公开方法 ====================

    /**
     * 加载索引筛选条件
     */
    fun loadIndexCondition() {
        viewModelScope.launch {
            _uiState.value = BangumiIndexUiState.Loading
            
            bangumiRepository.getIndexCondition(1).fold(
                onSuccess = { data ->
                    orderList = data.order ?: emptyList()
                    filterGroups = data.filter ?: emptyList()
                    
                    // 打印筛选条件数据用于调试
                    Logger.d(TAG, "Filter groups count: ${filterGroups.size}")
                    filterGroups.forEach { group ->
                        Logger.d(TAG, "Filter group: field=${group.field}, name=${group.name}, values=${group.values?.map { "${it.keyword}:${it.name}" }}")
                    }
                    
                    // 初始化筛选状态：每个筛选条件的第一个选项（全部）默认选中
                    val defaultFilters = mutableMapOf<String, String>()
                    filterGroups.forEach { group ->
                        group.values?.firstOrNull()?.let { defaultFilters[group.field] = it.keyword }
                    }
                    
                    _filterState.value = BangumiFilterState(
                        orderList = orderList,
                        filterGroups = filterGroups,
                        selectedOrder = (orderList.firstOrNull()?.field ?: 0).toString(),
                        selectedFilters = defaultFilters
                    )
                    
                    // 设置默认筛选参数（只传order，其他默认为"全部"不传参数）
                    currentParams.clear()
                    currentParams["order"] = _filterState.value.selectedOrder
                    
                    // 加载第一页数据
                    loadIndexResult(reset = true)
                },
                onFailure = { error ->
                    Logger.e(TAG, "Failed to load index condition", error)
                    _uiState.value = BangumiIndexUiState.Error(error.message ?: "Failed to load filter conditions")
                }
            )
        }
    }

    /**
     * 选择排序方式
     * @param orderField 排序字段
     */
    fun selectOrder(orderField: String) {
        _filterState.value = _filterState.value.copy(selectedOrder = orderField)
        currentParams["order"] = orderField
        loadIndexResult(reset = true)
    }

    /**
     * 选择筛选条件
     * @param field 筛选字段
     * @param keyword 筛选关键词
     */
    fun selectFilter(field: String, keyword: String) {
        Logger.d(TAG, "selectFilter: field=$field, keyword=$keyword")
        
        val updatedFilters = _filterState.value.selectedFilters.toMutableMap()
        
        // 如果已选中相同的值，则取消选择（恢复默认值-1）
        if (updatedFilters[field] == keyword) {
            updatedFilters.remove(field)
            currentParams.remove(field)
            Logger.d(TAG, "Removed filter for field: $field")
        } else {
            updatedFilters[field] = keyword
            currentParams[field] = keyword
            Logger.d(TAG, "Added filter: $field=$keyword")
        }
        
        _filterState.value = _filterState.value.copy(selectedFilters = updatedFilters)
        Logger.d(TAG, "Current params: $currentParams")
        
        loadIndexResult(reset = true)
    }

    /**
     * 加载下一页
     */
    fun loadNextPage() {
        if (_isLoadingMore.value || !_hasNextPage.value) return
        
        currentPage++
        loadIndexResult(reset = false)
    }

    /**
     * 刷新数据
     */
    fun refresh() {
        currentPage = 1
        loadIndexResult(reset = true)
    }

    // ==================== 私有方法 ====================

    /**
     * 加载索引结果
     * @param reset 是否重置列表
     */
    private fun loadIndexResult(reset: Boolean) {
        if (reset) {
            currentPage = 1
            _indexItems.value = emptyList()
        }

        viewModelScope.launch {
            if (reset) {
                _uiState.value = BangumiIndexUiState.Loading
            } else {
                _isLoadingMore.value = true
            }

            bangumiRepository.getIndexResult(
                params = currentParams,
                page = currentPage,
                pageSize = DEFAULT_PAGE_SIZE
            ).fold(
                onSuccess = { data ->
                    val newItems = data.list ?: emptyList()
                    _hasNextPage.value = data.has_next == 1
                    
                    if (reset) {
                        _indexItems.value = newItems
                        _uiState.value = if (newItems.isEmpty()) {
                            BangumiIndexUiState.Empty
                        } else {
                            BangumiIndexUiState.Success
                        }
                    } else {
                        _indexItems.value = _indexItems.value + newItems
                    }
                    
                    Logger.d(TAG, "Loaded ${newItems.size} items, page: $currentPage, hasNext: ${_hasNextPage.value}")
                },
                onFailure = { error ->
                    Logger.e(TAG, "Failed to load index result", error)
                    if (reset) {
                        _uiState.value = BangumiIndexUiState.Error(error.message ?: "Failed to load index results")
                    }
                }
            )

            _isLoadingMore.value = false
        }
    }
}

// ==================== UI State ====================

sealed class BangumiIndexUiState {
    data object Loading : BangumiIndexUiState()
    data object Success : BangumiIndexUiState()
    data object Empty : BangumiIndexUiState()
    data class Error(val message: String) : BangumiIndexUiState()
}

/**
 * 筛选状态
 */
data class BangumiFilterState(
    val orderList: List<OrderItem> = emptyList(),
    val filterGroups: List<FilterGroup> = emptyList(),
    val selectedOrder: String = "0",
    val selectedFilters: Map<String, String> = emptyMap()
)