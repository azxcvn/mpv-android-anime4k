package com.fam4k007.videoplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fam4k007.videoplayer.ui.screens.FolderBrowserScreen
import com.fam4k007.videoplayer.ui.theme.ThemeController
import com.fam4k007.videoplayer.ui.theme.VideoPlayerTheme
import com.fam4k007.videoplayer.utils.NoMediaChecker
import com.fam4k007.videoplayer.utils.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.KoinAndroidContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class VideoBrowserComposeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "VideoBrowserCompose"
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val MAX_SUPPLEMENTARY_FILES = 1000
    }

    private val preferencesManager: com.fam4k007.videoplayer.preferences.PreferencesManager by inject()
    private var themeRevision by mutableIntStateOf(0)
    
    // 用于跳转 Settings 授权后返回时关闭当前 Activity，回到首页重新进入
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从 Settings 返回，直接关闭 Activity 回到首页
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用边到边显示，让内容可以绘制到状态栏区域
        enableEdgeToEdge()

        setupContent()
    }
    
    override fun onResume() {
        super.onResume()
        // 从 Settings 授权页面返回时触发重组，重新检查权限状态
        themeRevision++
    }

    private fun setupContent() {
        val activity = this
        
        setContent {
            val revision = themeRevision
            KoinAndroidContext {
                val themeController = ThemeController.from(activity)
                VideoPlayerTheme(
                    appTheme = themeController.getCurrentTheme(),
                    darkMode = themeController.getDarkMode(),
                    amoledMode = themeController.getAmoledMode()
                ) {
                    FolderBrowserScreen(
                        hasPermission = checkPermissions(),
                        onRequestPermission = { requestStoragePermission() },
                        onNavigateBack = { 
                            finish()
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                        },
                        onOpenFolder = { folder -> openVideoList(folder) }
                    )
                }
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                storagePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storagePermissionLauncher.launch(intent)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            themeRevision++
        }
    }

    private fun scanVideoFiles(callback: (List<VideoFolder>) -> Unit) {
        lifecycleScope.launch {
            try {
                val scannedFolders = withContext(Dispatchers.IO) {
                    val projection = arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.DATA,
                        MediaStore.Video.Media.SIZE,
                        MediaStore.Video.Media.DURATION,
                        MediaStore.Video.Media.DATE_ADDED
                    )

                    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

                    val cursor = contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        sortOrder
                    )

                    val folderMap = mutableMapOf<String, MutableList<VideoFile>>()

                    cursor?.use {
                        val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val pathColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                        val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                        val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                        val dateColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                        while (it.moveToNext()) {
                            val id = it.getLong(idColumn)
                            val name = it.getString(nameColumn)
                            val path = it.getString(pathColumn)
                            val size = it.getLong(sizeColumn)
                            val duration = it.getLong(durationColumn)
                            val dateAdded = it.getLong(dateColumn)

                            if (ScanFilter.shouldSkipFile(this@VideoBrowserComposeActivity, path)) {
                                continue
                            }

                            val file = java.io.File(path)
                            if (!file.exists()) {
                                continue
                            }

                            val uri = Uri.withAppendedPath(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                id.toString()
                            )

                            val folderPath = path.substringBeforeLast("/")

                            val videoFile = VideoFile(
                                uri = uri.toString(),
                                name = name,
                                path = path,
                                size = size,
                                duration = duration,
                                dateAdded = dateAdded
                            )

                            folderMap.getOrPut(folderPath) { mutableListOf() }.add(videoFile)
                        }
                    }

                    // 补充扫描：当 .nomedia 关闭或隐藏文件夹扫描开启时，
                    // MediaStore 会遗漏这些文件，需要用 File API 直接扫描补充
                    val prefs = com.fam4k007.videoplayer.preferences.PreferencesManager.getInstance(this@VideoBrowserComposeActivity)
                    val needSupplementaryScan = prefs.getIncludeNoMediaFolders() || prefs.isScanHiddenFoldersEnabled()
                    if (needSupplementaryScan) {
                        val knownPaths = folderMap.values.flatten().map { it.path }.toMutableSet()
                        // 收集待扫描的目录
                        val dirsToScan = mutableSetOf<String>()
                        val parentDirs = folderMap.keys.map { java.io.File(it).parentFile?.absolutePath }.distinct().filterNotNull()
                        for (parentPath in parentDirs) {
                            val parentFile = java.io.File(parentPath)
                            if (parentFile.exists() && parentFile.isDirectory) {
                                parentFile.listFiles()?.forEach { subDir ->
                                    if (subDir.isDirectory && subDir.canRead()) {
                                        dirsToScan.add(subDir.absolutePath)
                                    }
                                }
                            }
                        }
                        // 如果 MediaStore 没有返回任何结果（所有视频都在 .nomedia 文件夹中），
                        // 则从外部存储根目录开始扫描
                        if (dirsToScan.isEmpty()) {
                            val storageRoot = android.os.Environment.getExternalStorageDirectory()
                            if (storageRoot.exists() && storageRoot.isDirectory) {
                                storageRoot.listFiles()?.forEach { subDir ->
                                    if (subDir.isDirectory && subDir.canRead() && !subDir.name.startsWith(".")) {
                                        dirsToScan.add(subDir.absolutePath)
                                    }
                                }
                            }
                            Log.d(TAG, "MediaStore 无结果，从存储根目录补充扫描")
                        }
                        for (dirPath in dirsToScan) {
                            val subDir = java.io.File(dirPath)
                            if (!subDir.exists() || !subDir.isDirectory || !subDir.canRead()) continue
                            if (prefs.isScanHiddenFoldersEnabled() && subDir.name.startsWith(".")) {
                                scanSingleFolder(subDir, this@VideoBrowserComposeActivity, knownPaths, folderMap)
                            }
                            if (prefs.getIncludeNoMediaFolders()) {
                                scanSingleFolder(subDir, this@VideoBrowserComposeActivity, knownPaths, folderMap)
                            }
                        }
                        Log.d(TAG, "补充扫描完成，共 ${folderMap.size} 个文件夹")
                    }

                    val folders = mutableListOf<VideoFolder>()
                    folderMap.forEach { (path, videos) ->
                        val folderName = path.substringAfterLast("/")
                        folders.add(
                            VideoFolder(
                                folderPath = path,
                                folderName = folderName.ifEmpty { "Root" },
                                videoCount = videos.size,
                                videos = videos,
                                totalSize = videos.sumOf { it.size },
                                dateModified = videos.maxOfOrNull { it.dateAdded } ?: 0L
                            )
                        )
                    }

                    folders.sortByDescending { it.videoCount }

                    Log.d(TAG, "扫描完成，找到 ${folders.size} 个文件夹")
                    
                    folders
                }
                
                callback(scannedFolders)
                
            } catch (e: Exception) {
                Log.e(TAG, "扫描视频文件失败", e)
                callback(emptyList())
            }
        }
    }

    /**
     * 直接扫描目录（用于补充 MediaStore 遗漏的文件）
     */
    private fun scanSingleFolder(
        dir: java.io.File,
        context: android.content.Context,
        knownPaths: MutableSet<String>,
        folderMap: MutableMap<String, MutableList<VideoFile>>
    ) {
        try {
            if (knownPaths.size >= MAX_SUPPLEMENTARY_FILES) return
            if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return
            if (com.fam4k007.videoplayer.utils.ScanFilter.shouldSkipFolder(context, dir.absolutePath)) return

            val files = dir.listFiles() ?: return
            for (file in files) {
                if (!file.isFile || !file.exists()) continue
                val path = file.absolutePath
                if (path in knownPaths) continue
                if (knownPaths.size >= MAX_SUPPLEMENTARY_FILES) return
                if (com.fam4k007.videoplayer.utils.ScanFilter.shouldSkipFile(context, path)) continue

                val extension = file.extension.lowercase()
                if (extension in com.fam4k007.videoplayer.AppConstants.Files.SUPPORTED_VIDEO_EXTENSIONS) {
                    val folderPath = path.substringBeforeLast("/")
                    val msDuration = com.fam4k007.videoplayer.utils.ScanFilter.queryDuration(context, path)
                    val videoFile = VideoFile(
                        uri = Uri.fromFile(file).toString(),
                        name = file.name,
                        path = path,
                        size = file.length(),
                        duration = msDuration,
                        dateAdded = file.lastModified() / 1000
                    )
                    folderMap.getOrPut(folderPath) { mutableListOf() }.add(videoFile)
                    knownPaths.add(path)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "扫描文件夹失败: ${dir.absolutePath}", e)
        }
    }

    private fun openVideoList(folder: VideoFolder) {
        val intent = Intent(this, VideoListComposeActivity::class.java)
        intent.putExtra("folder_name", folder.folderName)
        intent.putExtra("folder_path", folder.folderPath)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}
