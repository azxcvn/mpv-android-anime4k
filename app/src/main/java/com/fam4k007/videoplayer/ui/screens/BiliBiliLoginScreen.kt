package com.fam4k007.videoplayer.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fam4k007.videoplayer.bilibili.auth.BiliBiliAuthManager
import com.fam4k007.videoplayer.bilibili.model.LoginResult
import com.fam4k007.videoplayer.bilibili.model.QRCodeInfo
import com.fam4k007.videoplayer.bilibili.model.UserInfo
import com.fam4k007.videoplayer.ui.theme.spacing
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * B站扫码登录界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliBiliLoginScreen(
    onNavigateBack: () -> Unit,
    authManager: BiliBiliAuthManager = koinInject(),
    viewModel: LoginViewModel = viewModel { LoginViewModel(authManager) }
) {
    val uiState by viewModel.uiState.collectAsState()

    // 如果已登录，直接显示已登录页面
    LaunchedEffect(Unit) {
        if (authManager.isLoggedIn() && uiState !is LoginUiState.LoggedIn) {
            authManager.getUserInfo()?.let {
                viewModel.setLoggedIn(it)
            }
        }
    }

    val state = uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Login to Bilibili",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        when (state) {
            is LoginUiState.LoggedIn -> {
                LoggedInContent(
                    userInfo = state.userInfo,
                    onLogout = { viewModel.logout() },
                    onNavigateBack = onNavigateBack,
                    modifier = Modifier.padding(padding)
                )
            }
            is LoginUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingContent()
                }
            }
            else -> {
                QRCodeLoginTab(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

// ==================== 扫码登录 ====================

@Composable
private fun QRCodeLoginTab(
    state: LoginUiState,
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val leftTime by viewModel.qrCodeLeftTime.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

        Text(
            text = "Scan QR code with Bilibili official app",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

        if (state is LoginUiState.ShowQRCode) {
            Text(
                text = "Time remaining: $leftTime s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.smaller))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewModel.refreshQRCode() }) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Refresh QR", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { viewModel.saveQRCodeToGallery(context) }) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save to Gallery", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = {
                if (state is LoginUiState.ShowQRCode) {
                    openWithBilibiliApp(context, state.qrCodeInfo.url)
                }
            }) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open Bilibili", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.smaller))

        when (state) {
            is LoginUiState.ShowQRCode -> {
                Card(
                    modifier = Modifier.size(260.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val qrBitmap = remember(state.qrCodeInfo.url) {
                            generateQRCodeBitmap(state.qrCodeInfo.url, 220)
                        }
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

                AnimatedContent(
                    targetState = state.status,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "status"
                ) { currentStatus ->
                    Text(
                        text = currentStatus,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = when (currentStatus) {
                            "Waiting for scan..." -> MaterialTheme.colorScheme.onSurfaceVariant
                            "Scanned, waiting for confirmation..." -> MaterialTheme.colorScheme.primary
                            "QR code expired" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center
                    )
                }

                if (state.status == "QR code expired") {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                    Button(onClick = { viewModel.refreshQRCode() }) {
                        Text("Refresh QR")
                    }
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                Text(
                    text = "Please download and install the app from official channels.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large)
                )

                LaunchedEffect(state.qrCodeInfo.qrcodeKey) {
                    viewModel.startPolling(state.qrCodeInfo.qrcodeKey)
                }
            }
            is LoginUiState.Loading -> LoadingContent()
            is LoginUiState.Error -> ErrorContent(
                message = state.message,
                onRetry = { viewModel.refreshQRCode() }
            )
            else -> {}
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
    }
}

// ==================== 加载中 ====================

@Composable
private fun LoadingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        Text("Generating QR code...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ==================== 登录成功界面 ====================

@Composable
private fun LoggedInContent(
    userInfo: UserInfo,
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; onLogout() }) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.spacing.medium,
                    vertical = MaterialTheme.spacing.smaller
                ),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：头像
                if (userInfo.face.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(userInfo.face)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))

                // 中间：名称 + UID + VIP
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userInfo.uname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "UID: ${userInfo.mid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (userInfo.vipStatus == 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Diamond,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (userInfo.vipType >= 2) "Annual VIP" else "VIP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

        // 退出登录按钮，紧接在卡片下方
        OutlinedButton(
            onClick = { showLogoutConfirm = true },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = MaterialTheme.spacing.medium),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("退出登录")
        }
    }
}

// ==================== 错误界面 ====================

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

// ==================== 工具函数 ====================

private fun generateQRCodeBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) { null }
}

/**
 * 打开B站App扫码
 * 通过 bilibili://  scheme 唤起系统上已安装的哔哩哔哩 App。
 * 如有多个 B站 App，系统会自动弹出选择器。
 */
private fun openWithBilibiliApp(context: Context, qrUrl: String) {
    try {
        val encodedUrl = Uri.encode(qrUrl)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("bilibili://browser?url=$encodedUrl")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // 用系统Chooser打开，如果有多个App能处理会弹出选择器
        val chooser = Intent.createChooser(intent, "Choose an app")
        context.startActivity(chooser)
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "Bilibili app not detected", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to open: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ==================== UI状态定义 ====================

sealed class LoginUiState {
    object Loading : LoginUiState()
    data class ShowQRCode(val qrCodeInfo: QRCodeInfo, val status: String) : LoginUiState()
    data class LoggedIn(val userInfo: UserInfo) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

// ==================== ViewModel ====================

class LoginViewModel(private val authManager: BiliBiliAuthManager) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val uiState: StateFlow<LoginUiState> = _uiState

    private val _qrCodeLeftTime = MutableStateFlow(180)
    val qrCodeLeftTime: StateFlow<Int> = _qrCodeLeftTime

    init {
        if (authManager.isLoggedIn()) {
            authManager.getUserInfo()?.let { _uiState.value = LoginUiState.LoggedIn(it) }
        } else {
            generateQRCode()
        }
    }

    fun setLoggedIn(userInfo: UserInfo) { _uiState.value = LoginUiState.LoggedIn(userInfo) }
    fun generateQRCode() {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            _qrCodeLeftTime.value = 180
            authManager.generateQRCode().onSuccess { qrCodeInfo ->
                _uiState.value = LoginUiState.ShowQRCode(qrCodeInfo, "Waiting for scan...")
            }.onFailure { e ->
                _uiState.value = LoginUiState.Error(e.message ?: "Failed to generate QR code")
            }
        }
    }

    fun refreshQRCode() { qrCodeTimerJob?.cancel(); generateQRCode() }

    private var qrCodeTimerJob: kotlinx.coroutines.Job? = null
    private var pollingJob: kotlinx.coroutines.Job? = null

    fun startPolling(qrcodeKey: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var elapsed = 0
            while (elapsed < 180) { delay(1000); elapsed++; _qrCodeLeftTime.value = 180 - elapsed }
        }
        qrCodeTimerJob = viewModelScope.launch {
            repeat(60) {
                delay(3000)
                when (val result = authManager.pollQRCodeStatus(qrcodeKey)) {
                    is LoginResult.Success -> {
                        delay(500)
                        val userInfo = authManager.getUserInfo() ?: UserInfo(mid = 0, uname = "Bilibili User", face = "", vipStatus = 0, vipType = 0)
                        _uiState.value = LoginUiState.LoggedIn(userInfo)
                        pollingJob?.cancel(); return@launch
                    }
                    is LoginResult.WaitingScan -> updateStatus("Waiting for scan...")
                    is LoginResult.WaitingConfirm -> updateStatus("Scanned, waiting for confirmation...")
                    is LoginResult.Expired -> { updateStatus("QR code expired"); pollingJob?.cancel(); return@launch }
                    is LoginResult.Failed -> { _uiState.value = LoginUiState.Error(result.message); pollingJob?.cancel(); return@launch }
                }
            }
            updateStatus("二维码已过期"); pollingJob?.cancel()
        }
    }

    fun saveQRCodeToGallery(context: Context) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state !is LoginUiState.ShowQRCode) return@launch
                val bitmap = generateQRCodeBitmap(state.qrCodeInfo.url, 440) ?: run { Toast.makeText(context, "Failed to generate QR code image", Toast.LENGTH_SHORT).show(); return@launch }
                val fileName = "BiliLogin_QR_${System.currentTimeMillis()}.png"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear(); contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    Toast.makeText(context, "二维码已保存到相册", Toast.LENGTH_SHORT).show()
                } else {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        val saved = MediaStore.Images.Media.insertImage(resolver, bitmap, fileName, "Bilibili Login QR Code")
                        if (saved != null) Toast.makeText(context, "二维码已保存到相册", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                com.fam4k007.videoplayer.utils.Logger.e("BiliLogin", "Save QR error", e)
                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStatus(status: String) {
        val currentState = _uiState.value
        if (currentState is LoginUiState.ShowQRCode) _uiState.value = currentState.copy(status = status)
    }

    fun logout() {
        authManager.logout()
        _uiState.value = LoginUiState.Loading
        generateQRCode()
    }

    override fun onCleared() { super.onCleared(); pollingJob?.cancel(); qrCodeTimerJob?.cancel() }
}
