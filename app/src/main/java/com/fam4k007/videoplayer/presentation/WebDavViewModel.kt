package com.fam4k007.videoplayer.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.data.model.WebDavAccount
import com.fam4k007.videoplayer.domain.webdav.WebDavClient
import com.fam4k007.videoplayer.domain.webdav.WebDavConfig
import com.fam4k007.videoplayer.repository.WebDavRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

/**
 * WebDAV 统一 ViewModel
 * Presentation Layer - 管理账户列表和文件浏览两个界面的状态
 */
class WebDavViewModel(
    application: Application,
    private val repository: WebDavRepository
) : AndroidViewModel(application), KoinComponent {

    // ==================== 账户列表状态 ====================

    data class AccountListState(
        val accounts: List<WebDavAccount> = emptyList(),
        val showAddDialog: Boolean = false,
        val editingAccountId: String? = null,  // 正在编辑的账户ID，null表示新增模式
        val accountToDelete: WebDavAccount? = null
    )

    private val _accountListState = MutableStateFlow(AccountListState())
    val accountListState: StateFlow<AccountListState> = _accountListState.asStateFlow()

    init {
        refreshAccounts()
    }

    fun refreshAccounts() {
        _accountListState.value = _accountListState.value.copy(
            accounts = repository.getAllAccounts()
        )
    }

    fun showAddDialog() {
        resetAddAccountState()
        _accountListState.value = _accountListState.value.copy(showAddDialog = true, editingAccountId = null)
    }

    fun showEditDialog(account: WebDavAccount) {
        val url = account.serverUrl.orEmpty()
        val useHttps = url.startsWith("https://")
        val (host, port, path) = AddAccountState.parseServerUrl(url)
        _addAccountState.value = AddAccountState(
            displayName = account.displayName.orEmpty(),
            serverUrl = account.serverUrl.orEmpty(),
            account = account.account.orEmpty(),
            password = account.password.orEmpty(),
            isAnonymous = account.isAnonymous,
            useHttps = useHttps,
            host = host,
            port = port,
            path = path
        )
        _accountListState.value = _accountListState.value.copy(showAddDialog = true, editingAccountId = account.id)
    }

    fun dismissAddDialog() {
        _accountListState.value = _accountListState.value.copy(showAddDialog = false, editingAccountId = null)
        resetAddAccountState()
    }

    fun onAccountAdded() {
        refreshAccounts()
        dismissAddDialog()
    }

    fun requestDeleteAccount(account: WebDavAccount) {
        _accountListState.value = _accountListState.value.copy(accountToDelete = account)
    }

    fun cancelDeleteAccount() {
        _accountListState.value = _accountListState.value.copy(accountToDelete = null)
    }

    fun confirmDeleteAccount() {
        val account = _accountListState.value.accountToDelete ?: return
        repository.deleteAccount(account.id)
        refreshAccounts()
        cancelDeleteAccount()
    }


    // ==================== 添加账户对话框状态 ====================

    data class AddAccountState(
        val displayName: String = "",
        val serverUrl: String = "",
        val account: String = "",
        val password: String = "",
        val isAnonymous: Boolean = false,
        val passwordVisible: Boolean = false,
        val isTesting: Boolean = false,
        val testResult: String? = null,
        val testResultIsSuccess: Boolean? = null,  // null=无结果, true=成功, false=失败
        val saveError: String? = null,
        // 拆分后的服务器地址字段（UI 层面使用）
        val useHttps: Boolean = false,
        val host: String = "",
        val port: String = "",
        val path: String = ""
    ) {
        /**
         * 根据拆分字段自动构建完整 serverUrl
         */
        fun buildServerUrl(): String {
            if (host.isEmpty()) return ""
            val protocol = if (useHttps) "https" else "http"
            val portPart = if (port.isNotEmpty()) ":$port" else ""
            val pathPart = if (path.isNotEmpty()) {
                val p = if (path.startsWith("/")) path else "/$path"
                if (p.endsWith("/")) p else "$p/"
            } else {
                "/"
            }
            return "$protocol://$host$portPart$pathPart"
        }
        
        companion object {
            /**
             * 将完整 serverUrl 解析为拆分字段
             */
            fun parseServerUrl(url: String): Triple<String, String, String> {
                if (url.isBlank()) return Triple("", "", "")
                try {
                    val withoutProtocol = url.removePrefix("https://").removePrefix("http://")
                    val hostPart = withoutProtocol.substringBefore("/")
                    val pathPart = withoutProtocol.substringAfter("/", "")
                    
                    val host = hostPart.substringBefore(":")
                    val port = hostPart.substringAfter(":", "")
                    
                    val path = if (pathPart.isNotEmpty()) {
                        val trimmed = pathPart.trimEnd('/')
                        "/$trimmed"
                    } else ""
                    
                    return Triple(host, port, path)
                } catch (e: Exception) {
                    return Triple("", "", "")
                }
            }
        }
    }

    private val _addAccountState = MutableStateFlow(AddAccountState())
    val addAccountState: StateFlow<AddAccountState> = _addAccountState.asStateFlow()

    fun updateDisplayName(value: String) {
        _addAccountState.value = _addAccountState.value.copy(displayName = value, saveError = null)
    }

    fun updateServerUrl(value: String) {
        _addAccountState.value = _addAccountState.value.copy(serverUrl = value, testResult = null, saveError = null)
    }
    
    /**
     * 通过拆分字段自动构建 serverUrl
     */
    private fun updateServerUrlFromParts() {
        val state = _addAccountState.value
        val newUrl = state.buildServerUrl()
        _addAccountState.value = state.copy(serverUrl = newUrl, testResult = null, saveError = null)
    }

    fun setUseHttps(value: Boolean) {
        _addAccountState.value = _addAccountState.value.copy(useHttps = value)
        updateServerUrlFromParts()
    }

    fun updateHost(value: String) {
        _addAccountState.value = _addAccountState.value.copy(host = value)
        updateServerUrlFromParts()
    }

    fun updatePort(value: String) {
        // 只允许数字
        val filtered = value.filter { it.isDigit() }
        _addAccountState.value = _addAccountState.value.copy(port = filtered)
        updateServerUrlFromParts()
    }

    fun updatePath(value: String) {
        _addAccountState.value = _addAccountState.value.copy(path = value)
        updateServerUrlFromParts()
    }

    fun updateAccount(value: String) {
        _addAccountState.value = _addAccountState.value.copy(account = value)
    }

    fun updatePassword(value: String) {
        _addAccountState.value = _addAccountState.value.copy(password = value)
    }

    fun setAnonymous(value: Boolean) {
        _addAccountState.value = _addAccountState.value.copy(isAnonymous = value)
    }

    fun togglePasswordVisible() {
        _addAccountState.value = _addAccountState.value.copy(
            passwordVisible = !_addAccountState.value.passwordVisible
        )
    }

    fun testConnection() {
        val state = _addAccountState.value
        val serverUrl = state.serverUrl

        when {
            state.host.isEmpty() -> {
                _addAccountState.value = state.copy(
                    testResult = "Please enter the host address",
                    testResultIsSuccess = false
                )
                return
            }
            serverUrl.isEmpty() -> {
                _addAccountState.value = state.copy(
                    testResult = "Please enter the server address",
                    testResultIsSuccess = false
                )
                return
            }
            !serverUrl.startsWith("http://") && !serverUrl.startsWith("https://") -> {
                _addAccountState.value = state.copy(
                    testResult = "Server address must start with http:// or https://",
                    testResultIsSuccess = false
                )
                return
            }
            !state.isAnonymous && (state.account.isEmpty() || state.password.isEmpty()) -> {
                _addAccountState.value = state.copy(
                    testResult = "Please enter username and password",
                    testResultIsSuccess = false
                )
                return
            }
        }

        _addAccountState.value = state.copy(isTesting = true, testResult = null, testResultIsSuccess = null)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val config = WebDavConfig(
                        serverUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/",
                        account = state.account,
                        password = state.password,
                        isAnonymous = state.isAnonymous
                    )
                    // 使用 Koin 创建 WebDavClient
                    val client: WebDavClient by inject { parametersOf(config) }
                    client.testConnection()
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            _addAccountState.value = _addAccountState.value.copy(
                isTesting = false,
                testResult = if (result) "Connection successful" else "Connection failed, please check the configuration",
                testResultIsSuccess = result
            )
        }
    }

    fun saveAccount() {
        val state = _addAccountState.value
        val editingId = _accountListState.value.editingAccountId

        when {
            state.displayName.isEmpty() -> {
                _addAccountState.value = state.copy(saveError = "Please enter a display name")
                return
            }
            state.host.isEmpty() -> {
                _addAccountState.value = state.copy(saveError = "Please enter the host address")
                return
            }
            state.serverUrl.isEmpty() -> {
                _addAccountState.value = state.copy(saveError = "Please enter the server address")
                return
            }
            !state.serverUrl.startsWith("http://") && !state.serverUrl.startsWith("https://") -> {
                _addAccountState.value = state.copy(saveError = "Server address must start with http:// or https://")
                return
            }
            !state.isAnonymous && (state.account.isEmpty() || state.password.isEmpty()) -> {
                _addAccountState.value = state.copy(saveError = "Please enter username and password")
                return
            }
        }

        val serverUrl = if (state.serverUrl.endsWith("/")) state.serverUrl else "${state.serverUrl}/"

        if (editingId != null) {
            // 编辑模式：更新已有账户
            val updatedAccount = WebDavAccount(
                id = editingId,
                displayName = state.displayName,
                serverUrl = serverUrl,
                account = state.account,
                password = state.password,
                isAnonymous = state.isAnonymous
            )
            if (repository.updateAccount(updatedAccount)) {
                onAccountAdded()
                resetAddAccountState()
            } else {
                _addAccountState.value = state.copy(saveError = "Update failed, account does not exist")
            }
        } else {
            // 新增模式
            val newAccount = WebDavAccount(
                displayName = state.displayName,
                serverUrl = serverUrl,
                account = state.account,
                password = state.password,
                isAnonymous = state.isAnonymous
            )
            if (repository.addAccount(newAccount)) {
                onAccountAdded()
                resetAddAccountState()
            } else {
                _addAccountState.value = state.copy(saveError = "This account already exists")
            }
        }
    }

    fun resetAddAccountState() {
        _addAccountState.value = AddAccountState()
    }


    // ==================== 文件浏览状态 ====================

    data class BrowserState(
        val files: List<WebDavClient.WebDavFile> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val currentPath: String = "",
        val sortType: Int = 0,
        val showSortDialog: Boolean = false,
        val client: WebDavClient? = null,
        private val pathStack: List<String> = emptyList()
    ) {
        val pathDepth: Int get() = pathStack.size
        val canGoBack: Boolean get() = pathStack.isNotEmpty()

        fun pushPath(path: String): BrowserState = copy(
            pathStack = pathStack + path
        )

        fun popPath(): Pair<BrowserState, String?> {
            if (pathStack.isEmpty()) return copy() to null
            // 栈顶是父级路径，pop 后应返回它作为目标路径
            val parentPath = pathStack.last()
            return copy(pathStack = pathStack.dropLast(1)) to parentPath
        }
    }

    private val _browserState = MutableStateFlow(BrowserState())
    val browserState: StateFlow<BrowserState> = _browserState.asStateFlow()

    /** 当前正在进行的文件加载协程，用于在新的导航操作时取消旧请求 */
    private var loadJob: Job? = null

    fun initBrowser(account: WebDavAccount) {
        val client = WebDavClient(
            WebDavConfig(
                serverUrl = account.serverUrl.orEmpty(),
                account = account.account.orEmpty(),
                password = account.password.orEmpty(),
                isAnonymous = account.isAnonymous
            )
        )
        // 取消旧的加载协程，完全重置浏览器状态
        loadJob?.cancel()
        _browserState.value = BrowserState(client = client)
        loadFiles("")
    }

    /**
     * 加载指定路径的文件列表
     *
     * 核心设计：每次调用前取消旧的 loadJob，保证只有最新的导航请求会生效。
     * path 在 launch 时通过闭包捕获，完成时用 update 原子写入，
     * 只更新 files/currentPath/error，不触碰 pathStack，避免覆盖导航操作。
     */
    fun loadFiles(path: String) {
        val client = _browserState.value.client ?: return

        // 取消旧的加载请求，避免旧请求完成时覆盖新状态
        loadJob?.cancel()

        _browserState.update { it.copy(isLoading = true, error = null) }

        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    client.listFiles(path)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    e.printStackTrace()
                    null
                }
            }

            if (result == null) {
                _browserState.update { current ->
                    val (newState, fallbackPath) = current.popPath()
                    newState.copy(
                        isLoading = false,
                        error = "Load failed",
                        currentPath = fallbackPath ?: ""
                    )
                }
            } else {
                val sortType = _browserState.value.sortType
                val sorted = sortFiles(result, sortType)
                _browserState.update { current ->
                    current.copy(
                        files = sorted,
                        isLoading = false,
                        currentPath = path
                    )
                }
            }
        }
    }

    fun navigateToFolder(path: String) {
        // 加载中禁止操作，避免快速点击导致路径栈和 currentPath 错乱
        if (_browserState.value.isLoading) return
        val state = _browserState.value
        _browserState.value = state.pushPath(state.currentPath)
        loadFiles(path)
    }

    fun navigateBack(): Boolean {
        // 加载中禁止操作
        if (_browserState.value.isLoading) return false
        val (newState, previousPath) = _browserState.value.popPath()
        return if (previousPath != null) {
            _browserState.value = newState
            loadFiles(previousPath)
            true
        } else {
            false
        }
    }

    fun setSortType(type: Int) {
        val state = _browserState.value
        val sorted = sortFiles(state.files, type)
        _browserState.value = state.copy(sortType = type, files = sorted, showSortDialog = false)
    }

    fun showSortDialog() {
        _browserState.value = _browserState.value.copy(showSortDialog = true)
    }

    fun dismissSortDialog() {
        _browserState.value = _browserState.value.copy(showSortDialog = false)
    }

    private fun sortFiles(
        fileList: List<WebDavClient.WebDavFile>,
        sortType: Int
    ): List<WebDavClient.WebDavFile> {
        val folders = fileList.filter { it.isDirectory }
        val files = fileList.filter { !it.isDirectory }

        val sortedFolders = when (sortType) {
            0 -> folders.sortedWith(naturalComparator { it.name })
            1 -> folders.sortedWith(naturalComparator<WebDavClient.WebDavFile> { it.name }.reversed())
            2 -> folders.sortedByDescending { it.modifiedTime }
            3 -> folders.sortedBy { it.modifiedTime }
            else -> folders.sortedWith(naturalComparator { it.name })
        }

        val sortedFiles = when (sortType) {
            0 -> files.sortedWith(naturalComparator { it.name })
            1 -> files.sortedWith(naturalComparator<WebDavClient.WebDavFile> { it.name }.reversed())
            2 -> files.sortedByDescending { it.modifiedTime }
            3 -> files.sortedBy { it.modifiedTime }
            else -> files.sortedWith(naturalComparator { it.name })
        }

        return sortedFolders + sortedFiles
    }

    private fun <T> naturalComparator(selector: (T) -> String): Comparator<T> {
        return Comparator { a, b -> compareNatural(selector(a).orEmpty(), selector(b).orEmpty()) }
    }

    private fun compareNatural(str1: String, str2: String): Int {
        val s1 = str1.lowercase()
        val s2 = str2.lowercase()
        var i1 = 0
        var i2 = 0

        while (i1 < s1.length && i2 < s2.length) {
            val c1 = s1[i1]
            val c2 = s2[i2]
            if (c1.isDigit() && c2.isDigit()) {
                var num1 = 0
                while (i1 < s1.length && s1[i1].isDigit()) {
                    num1 = num1 * 10 + (s1[i1] - '0')
                    i1++
                }
                var num2 = 0
                while (i2 < s2.length && s2[i2].isDigit()) {
                    num2 = num2 * 10 + (s2[i2] - '0')
                    i2++
                }
                if (num1 != num2) return num1 - num2
            } else {
                if (c1 != c2) return c1 - c2
                i1++
                i2++
            }
        }
        return s1.length - s2.length
    }

    companion object {
        fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
                size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
                else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
            }
        }
    }
}
