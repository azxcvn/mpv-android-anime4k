package com.fam4k007.videoplayer.domain.player

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fam4k007.videoplayer.domain.player.Anime4KManager
import com.fam4k007.videoplayer.domain.player.PlaybackEngine
import com.fam4k007.videoplayer.player.VideoAspect
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.danmaku.DanmakuConfig
import com.fam4k007.videoplayer.domain.danmaku.DanmakuManager
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.utils.DialogUtils
import com.fam4k007.videoplayer.presentation.PlayerViewModel
import `is`.xyz.mpv.MPVLib
import java.io.File
import java.lang.ref.WeakReference

/**
 * 播放器对话框管理器
 * 负责所有对话框的显示和交互
 */
class PlayerDialogManager(
    private val activityRef: WeakReference<AppCompatActivity>,
    private val playbackEngine: PlaybackEngine,
    private val danmakuManager: DanmakuManager,
    private val anime4KManager: Anime4KManager,
    private val preferencesManager: PreferencesManager,
    private val composeOverlayManager: com.fam4k007.videoplayer.manager.compose.ComposeOverlayManager,
    private val controlsManagerRef: WeakReference<PlayerControlsManager>,
    private val viewModelRef: WeakReference<PlayerViewModel>? = null  // 新增：用于ViewModel数据访问
) {
    companion object {
        private const val TAG = "PlayerDialogManager"
    }

    private data class PopupAnchor(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height
        val centerX: Int get() = x + width / 2
    }

    private data class PopupPosition(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )

    enum class PopupHorizontalAlignment {
        START,
        CENTER,
        END
    }

    private val context: Context?
        get() = activityRef.get()

    // 最后一次触发对话框的锚点位置（用于 Compose 按钮触发时定位）
    private var lastAnchorX = 0
    private var lastAnchorY = 0
    private var lastAnchorW = 100
    private var lastAnchorH = 50

    /** 由 VideoPlayerActivity 在 Compose 按钮点击时调用，设置锚点位置 */
    fun setLastAnchor(x: Int, y: Int, w: Int, h: Int) {
        lastAnchorX = x; lastAnchorY = y; lastAnchorW = w; lastAnchorH = h
        Log.d(TAG, "setLastAnchor: x=$x, y=$y, w=$w, h=$h")
    }

    private fun resolvePopupAnchor(anchorView: View? = null): PopupAnchor {
        if (anchorView != null && anchorView.isShown) {
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val anchor = PopupAnchor(
                x = location[0],
                y = location[1],
                width = anchorView.width.coerceAtLeast(1),
                height = anchorView.height.coerceAtLeast(1)
            )
            lastAnchorX = anchor.x
            lastAnchorY = anchor.y
            lastAnchorW = anchor.width
            lastAnchorH = anchor.height
            return anchor
        }

        // 如果lastAnchor没有设置（还是初始值0），使用屏幕右上角作为默认位置
        if (lastAnchorX == 0 && lastAnchorY == 0) {
            val activity = activityRef.get()
            if (activity != null) {
                val screenWidth = activity.resources.displayMetrics.widthPixels
                // 假设"更多"按钮在右上角，估算其位置
                // 这是一个回退方案，实际位置应该通过setLastAnchor设置
                val estimatedX = screenWidth - 80  // 假设按钮在右边，距离右边缘约80px
                val estimatedY = 60   // 假设按钮在顶部状态栏下方
                Log.w(TAG, "resolvePopupAnchor: lastAnchor not set, using estimated position ($estimatedX, $estimatedY)")
                return PopupAnchor(
                    x = estimatedX,
                    y = estimatedY,
                    width = 50,
                    height = 50
                )
            }
        }

        return PopupAnchor(
            x = lastAnchorX,
            y = lastAnchorY,
            width = lastAnchorW.coerceAtLeast(1),
            height = lastAnchorH.coerceAtLeast(1)
        )
    }

    private fun calculatePopupPosition(
        anchor: PopupAnchor,
        popupWidth: Int,
        popupHeight: Int,
        showAbove: Boolean,
        horizontalAlignment: PopupHorizontalAlignment = PopupHorizontalAlignment.CENTER,
        clampToScreen: Boolean = true
    ): PopupPosition? {
        val activity = activityRef.get() ?: return null
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val screenHeight = activity.resources.displayMetrics.heightPixels
        val margin = 8.dpToPx()
        val popupGap = 1.dpToPx()
        val dialogWidth = popupWidth.coerceAtLeast(anchor.width)
        val dialogHeight = popupHeight

        Log.d(TAG, "calculatePopupPosition: anchor=(${ anchor.x}, ${anchor.y}, ${anchor.width}, ${anchor.height}), alignment=$horizontalAlignment, screenWidth=$screenWidth")

        val minX = margin
        val maxX = (screenWidth - dialogWidth - margin).coerceAtLeast(minX)
        
        // 计算初始X位置
        val initialX = when (horizontalAlignment) {
            PopupHorizontalAlignment.START -> anchor.x
            PopupHorizontalAlignment.CENTER -> anchor.x + (anchor.width - dialogWidth) / 2
            PopupHorizontalAlignment.END -> {
                // 对于END对齐，检查锚点是否超出屏幕
                val anchorRight = anchor.x + anchor.width
                if (anchorRight > screenWidth) {
                    // 锚点位置异常，直接右对齐到屏幕边缘
                    Log.w(TAG, "calculatePopupPosition: anchor right ($anchorRight) exceeds screen width ($screenWidth), aligning to screen edge")
                    screenWidth - dialogWidth - margin
                } else {
                    // 正常情况：菜单右边缘对齐到锚点右边缘
                    anchor.x + anchor.width - dialogWidth
                }
            }
        }
        Log.d(TAG, "calculatePopupPosition: initialX=$initialX, minX=$minX, maxX=$maxX, dialogWidth=$dialogWidth")
        // 将位置限制在屏幕可见范围内
        val popupX = if (clampToScreen) initialX.coerceIn(minX, maxX) else initialX

        val minY = margin
        val maxY = (screenHeight - dialogHeight - margin).coerceAtLeast(minY)
        val belowY = anchor.bottom + popupGap
        val aboveY = anchor.y - dialogHeight - popupGap
        val preferredY = if (showAbove) aboveY else belowY
        val fallbackY = if (showAbove) belowY else aboveY
        val popupY = when {
            preferredY in minY..maxY -> preferredY
            fallbackY in minY..maxY -> fallbackY
            else -> preferredY.coerceIn(minY, maxY)
        }

        return PopupPosition(
            x = popupX,
            y = popupY,
            width = dialogWidth,
            height = dialogHeight
        )
    }

    private fun applyPopupPosition(
        popupWindow: PopupWindow,
        contentView: View,
        anchor: PopupAnchor,
        showAbove: Boolean,
        horizontalAlignment: PopupHorizontalAlignment = PopupHorizontalAlignment.CENTER,
        clampToScreen: Boolean = true,
        widthOverride: Int? = null,
        heightOverride: Int? = null,
        rootView: View
    ) {
        val measuredWidth: Int
        val measuredHeight: Int

        if (widthOverride != null && heightOverride != null) {
            measuredWidth = widthOverride
            measuredHeight = heightOverride
        } else {
            contentView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            measuredWidth = contentView.measuredWidth
            measuredHeight = contentView.measuredHeight
        }

        val popupPosition = calculatePopupPosition(
            anchor = anchor,
            popupWidth = measuredWidth,
            popupHeight = measuredHeight,
            showAbove = showAbove,
            horizontalAlignment = horizontalAlignment,
            clampToScreen = clampToScreen
        ) ?: return

        if (popupWindow.isShowing) {
            popupWindow.update(
                popupPosition.x,
                popupPosition.y,
                popupPosition.width,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        } else {
            popupWindow.width = popupPosition.width
            popupWindow.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            popupWindow.showAtLocation(
                rootView,
                android.view.Gravity.NO_GRAVITY,
                popupPosition.x,
                popupPosition.y
            )
        }

        Log.d(
            TAG,
            "applyPopupPosition: anchor=(${anchor.x},${anchor.y},${anchor.width},${anchor.height}), dialogWidth=${popupPosition.width}, dialogHeight=${popupPosition.height}, popupX=${popupPosition.x}, popupY=${popupPosition.y}"
        )
    }

    /**
     * 为Compose按钮显示对话框（直接使用lastAnchor坐标）
     */
    private fun showPopupDialogAtLastAnchor(
        items: List<String>,
        selectedPosition: Int = -1,
        title: String = "",
        showAbove: Boolean = false,
        useFixedHeight: Boolean = false,
        showScrollHint: Boolean = false,
        horizontalAlignment: PopupHorizontalAlignment = PopupHorizontalAlignment.CENTER,
        clampToScreen: Boolean = true,
        onItemClick: (Int) -> Unit
    ) {
        val activity = activityRef.get() ?: return

        val layoutRes = if (useFixedHeight) R.layout.dialog_popup_menu_fixed else R.layout.dialog_popup_menu
        val contentView = activity.layoutInflater.inflate(layoutRes, null)
        val recyclerView = contentView.findViewById<RecyclerView>(R.id.recyclerViewPopup)
        val rootView = activity.findViewById<View>(android.R.id.content)
        var popupWindow: PopupWindow? = null

        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.isVerticalScrollBarEnabled = false
        
        val adapter = PopupMenuAdapter(items, selectedPosition) { position ->
            onItemClick(position)
            popupWindow?.dismiss()
        }
        recyclerView.adapter = adapter

        // 控制滑动提示文字的显示
        val scrollHint = contentView.findViewById<android.widget.TextView>(R.id.tvScrollHint)
        if (scrollHint != null) {
            scrollHint.visibility = if (showScrollHint) View.VISIBLE else View.GONE
        }

        val anchor = resolvePopupAnchor()

        popupWindow = PopupWindow(
            contentView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            animationStyle = R.style.PopupAnimation
        }

        applyPopupPosition(
            popupWindow = popupWindow,
            contentView = contentView,
            anchor = anchor,
            showAbove = showAbove,
            horizontalAlignment = horizontalAlignment,
            clampToScreen = clampToScreen,
            rootView = rootView
        )
        
        controlsManagerRef.get()?.setPopupVisible(true)

        contentView.post {
            val actualWidth = contentView.width
            val actualHeight = contentView.height
            if (actualWidth > 0 && actualHeight > 0) {
                applyPopupPosition(
                    popupWindow = popupWindow,
                    contentView = contentView,
                    anchor = anchor,
                    showAbove = showAbove,
                    horizontalAlignment = horizontalAlignment,
                    clampToScreen = clampToScreen,
                    widthOverride = actualWidth,
                    heightOverride = actualHeight,
                    rootView = rootView
                )
            }
        }
        
        activePopupWindows.add(popupWindow)
        popupWindow.setOnDismissListener {
            activePopupWindows.remove(popupWindow)
            controlsManagerRef.get()?.setPopupVisible(false)
        }
        
        if (selectedPosition >= 0 && useFixedHeight) {
            val nestedScrollView = contentView.findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollViewPopup)
            nestedScrollView?.post {
                val itemHeight = 40.dpToPx()
                val targetScroll = selectedPosition * itemHeight - itemHeight
                nestedScrollView.scrollTo(0, targetScroll.coerceAtLeast(0))
            }
        }
    }
    
    // 追踪所有活动的 PopupWindow，防止内存泄漏
    private val activePopupWindows = mutableListOf<PopupWindow>()

    // 回调接口
    interface DialogCallback {
        fun onSpeedChanged(speed: Double)
        fun onAnime4KChanged(enabled: Boolean, mode: Anime4KManager.Mode, quality: Anime4KManager.Quality)
    }

    private var dialogCallback: DialogCallback? = null

    fun setCallback(callback: DialogCallback) {
        this.dialogCallback = callback
    }

    /**
     * 显示音频选项对话框（音频轨道 / 添加音频）
     * 点击顶部音频按钮时触发
     */
    fun showAudioOptionsDialog() {
        val activity = activityRef.get() ?: return

        val items = listOf("Audio Track", "Add Audio")

        val configuration = activity.resources.configuration
        val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        val horizontalAlignment = if (isPortrait) PopupHorizontalAlignment.END else PopupHorizontalAlignment.CENTER

        showPopupDialogAtLastAnchor(
            items,
            selectedPosition = -1,
            title = "Audio",
            showAbove = false,
            useFixedHeight = false,
            showScrollHint = false,
            horizontalAlignment = horizontalAlignment,
            clampToScreen = false
        ) { position ->
            when (position) {
                0 -> showAudioTrackDialog()  // 音频轨道选择
                1 -> (activity as? AudioOptionsCallback)?.onAddAudioTrack()  // 添加外部音频
            }
        }
    }

    /**
     * 显示音频轨道选择对话框
     * 使用ViewModel的audioTracks StateFlow数据
     */
    fun showAudioTrackDialog() {
        val activity = activityRef.get() ?: return
        val viewModel = viewModelRef?.get()

        try {
            // 优先使用ViewModel数据，降级到PlaybackEngine
            val audioTracks = if (viewModel != null) {
                // 【方案A改进】使用ViewModel的audioTracks StateFlow
                val vmTracks = viewModel.audioTracks.value
                if (vmTracks.isNotEmpty()) {
                    // 转换为对话框所需格式: Triple(id, displayName, isSelected)
                    vmTracks.map { track ->
                        val displayName = buildString {
                            append("Track ${track.id}")
                            track.lang?.let { append(" ($it)") }
                            track.title?.let { append(" - $it") }
                            track.codec?.let { append(" [$it]") }
                        }
                        Triple(track.id, displayName, track.selected)
                    }
                } else {
                    // ViewModel数据未就绪，降级
                    playbackEngine.getAudioTracks()
                }
            } else {
                // 无ViewModel，使用原方法
                playbackEngine.getAudioTracks()
            }

            if (audioTracks.isEmpty()) {
                DialogUtils.showToastShort(activity, "No audio tracks available")
                return
            }

            // 获取轨道名称列表
            val items = audioTracks.map { it.second }
            // 获取当前选中的轨道索引
            val currentTrackIndex = audioTracks.indexOfFirst { it.third }

            // 根据屏幕方向决定对齐方式：竖屏靠右对齐，横屏居中
            val configuration = activity.resources.configuration
            val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            val horizontalAlignment = if (isPortrait) PopupHorizontalAlignment.END else PopupHorizontalAlignment.CENTER

            showPopupDialogAtLastAnchor(
                items,
                currentTrackIndex,
                title = "Audio Track",
                showAbove = false,
                useFixedHeight = false,
                showScrollHint = false,
                horizontalAlignment = horizontalAlignment,
                clampToScreen = false
            ) { position ->
                val trackId = audioTracks[position].first
                playbackEngine.selectAudioTrack(trackId)
                DialogUtils.showToastShort(activity, "Switched to: ${items[position]}")
                Log.d(TAG, "Audio track changed to: $trackId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show audio track dialog", e)
            DialogUtils.showToastShort(activity, "Failed to get audio tracks")
        }
    }

    /**
     * 显示解码器选择对话框
     */
    fun showDecoderDialog() {
        val activity = activityRef.get() ?: return

        val items = listOf("Hardware Decode", "Software Decode")
        val currentDecoder = preferencesManager.getHardwareDecoder()
        val currentSelection = if (currentDecoder) 0 else 1

        // 根据屏幕方向决定对齐方式：竖屏靠右对齐，横屏居中
        val configuration = activity.resources.configuration
        val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        val horizontalAlignment = if (isPortrait) PopupHorizontalAlignment.END else PopupHorizontalAlignment.CENTER

        showPopupDialogAtLastAnchor(
            items,
            currentSelection,
            title = "Decoder",
            showAbove = false,
            useFixedHeight = false,
            showScrollHint = false,
            horizontalAlignment = horizontalAlignment,
            clampToScreen = false
        ) { position ->
            val newDecoder = (position == 0)
            preferencesManager.setHardwareDecoder(newDecoder)
            playbackEngine.setHardwareDecoding(newDecoder)
            DialogUtils.showToastShort(activity, "Switched to ${items[position]}")
            Log.d(TAG, "Decoder changed to: ${if (newDecoder) "hardware" else "software"}")
        }
    }

    /**
     * 显示画面比例选择对话框
     */
    fun showAspectRatioDialog(currentAspect: VideoAspect) {
        val activity = activityRef.get() ?: return

        val items = listOf("自动", "拉伸", "裁剪", "等宽", "等高", "原始", "4:3", "16:9")
        val aspects = listOf(
            VideoAspect.FIT, VideoAspect.STRETCH, VideoAspect.CROP,
            VideoAspect.EQUAL_WIDTH, VideoAspect.EQUAL_HEIGHT, VideoAspect.ORIGINAL,
            VideoAspect.RATIO_4_3, VideoAspect.RATIO_16_9
        )
        val currentSelection = aspects.indexOf(currentAspect).coerceAtLeast(0)

        showPopupDialogAtLastAnchor(
            items,
            currentSelection,
            title = "Aspect Ratio",
            showAbove = false,
            useFixedHeight = true,
            showScrollHint = true
        ) { position ->
            val newAspect = aspects.getOrElse(position) { VideoAspect.FIT }
            playbackEngine.changeVideoAspect(newAspect)
            (activity as? VideoAspectCallback)?.onVideoAspectChanged(newAspect)
            DialogUtils.showToastShort(activity, "Aspect ratio: ${items[position]}")
            Log.d(TAG, "Video aspect changed to: ${newAspect.displayName}")
        }
    }

    /**
     * 显示通用弹出对话框
     */
    fun showPopupDialog(
        anchorView: View,
        items: List<String>,
        selectedPosition: Int = -1,
        title: String = "",
        showAbove: Boolean = false,
        useFixedHeight: Boolean = false,
        showScrollHint: Boolean = false,
        horizontalAlignment: PopupHorizontalAlignment = PopupHorizontalAlignment.CENTER,
        onItemClick: (Int) -> Unit
    ) {
        val activity = activityRef.get() ?: return

        // 根据是否需要固定高度选择不同的布局文件
        val layoutRes = if (useFixedHeight) R.layout.dialog_popup_menu_fixed else R.layout.dialog_popup_menu
        val contentView = activity.layoutInflater.inflate(layoutRes, null)
        val recyclerView = contentView.findViewById<RecyclerView>(R.id.recyclerViewPopup)
        val rootView = activity.findViewById<View>(android.R.id.content)
        var popupWindow: PopupWindow? = null

        // 标题已从布局中移除，不再设置标题

        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.isVerticalScrollBarEnabled = false
        
        val adapter = PopupMenuAdapter(items, selectedPosition) { position ->
            onItemClick(position)
            popupWindow?.dismiss()
        }
        recyclerView.adapter = adapter

        // 控制滑动提示文字的显示
        val scrollHint = contentView.findViewById<android.widget.TextView>(R.id.tvScrollHint)
        if (scrollHint != null) {
            scrollHint.visibility = if (showScrollHint) View.VISIBLE else View.GONE
        }

        val anchor = resolvePopupAnchor(anchorView)

        popupWindow = PopupWindow(
            contentView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            animationStyle = R.style.PopupAnimation
        }

        applyPopupPosition(
            popupWindow = popupWindow,
            contentView = contentView,
            anchor = anchor,
            showAbove = showAbove,
            horizontalAlignment = horizontalAlignment,
            rootView = rootView
        )
        
        // 通知控制组件有弹窗显示
        controlsManagerRef.get()?.setPopupVisible(true)

        contentView.post {
            val actualWidth = contentView.width
            val actualHeight = contentView.height
            if (actualWidth > 0 && actualHeight > 0) {
                applyPopupPosition(
                    popupWindow = popupWindow,
                    contentView = contentView,
                    anchor = anchor,
                    showAbove = showAbove,
                    horizontalAlignment = horizontalAlignment,
                    widthOverride = actualWidth,
                    heightOverride = actualHeight,
                    rootView = rootView
                )
            }
        }
        
        activePopupWindows.add(popupWindow)
        popupWindow.setOnDismissListener {
            activePopupWindows.remove(popupWindow)
            // 通知控制组件弹窗关闭
            controlsManagerRef.get()?.setPopupVisible(false)
        }
        
        // 如果有选中项且使用固定高度，自动滚动到选中位置
        if (selectedPosition >= 0 && useFixedHeight) {
            val nestedScrollView = contentView.findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollViewPopup)
            nestedScrollView?.post {
                val itemHeight = 40.dpToPx()
                val scrollViewHeight = nestedScrollView.height
                val targetY = selectedPosition * itemHeight - (scrollViewHeight / 2) + (itemHeight / 2)
                nestedScrollView.smoothScrollTo(0, targetY.coerceAtLeast(0))
            }
        }
    }
    
    private fun Int.dpToPx(): Int {
        val activity = activityRef.get() ?: return this
        return (this * activity.resources.displayMetrics.density).toInt()
    }

    /**
     * 显示字幕菜单对话框
     */
    fun showSubtitleDialog() {
        val activity = activityRef.get() ?: return

        val menuItems = listOf("Subtitle Track", "External Subtitle", "More Settings")

        showPopupDialogAtLastAnchor(
            menuItems,
            selectedPosition = -1,
            title = "Subtitle",
            showAbove = false,
            useFixedHeight = false,
            showScrollHint = false
        ) { position ->
            when (position) {
                0 -> showSubtitleTrackDialog()
                1 -> {
                    // 导入外部字幕的逻辑由Activity处理
                    (activity as? SubtitleDialogCallback)?.onImportSubtitle()
                }
                2 -> showSubtitleSettingsDrawer()
            }
        }
    }

    /**
     * 显示字幕轨道切换对话框
     * 使用ViewModel的subtitleTracks StateFlow数据
     */
    private fun showSubtitleTrackDialog() {
        val activity = activityRef.get() ?: return
        val viewModel = viewModelRef?.get()

        try {
            // 优先使用ViewModel数据，降级到PlaybackEngine
            val tracks = if (viewModel != null) {
                // 【方案A改进】使用ViewModel的subtitleTracks StateFlow
                val vmTracks = viewModel.subtitleTracks.value
                if (vmTracks.isNotEmpty()) {
                    // 转换为对话框所需格式: Triple(id, displayName, isSelected)
                    val trackList = mutableListOf<Triple<Int, String, Boolean>>()
                    
                    // 添加"关闭字幕"选项
                    val currentId = MPVLib.getPropertyInt("sid") ?: -1
                    trackList.add(Triple(-1, "Disable Subtitles", currentId == -1))
                    
                    // 添加ViewModel提供的轨道
                    vmTracks.forEach { track ->
                        val displayName = buildString {
                            track.lang?.let { append("$it") }
                            track.title?.let { append(" $it") }
                            if (track.external) append(" [External]")
                        }
                        trackList.add(Triple(track.id, displayName, track.selected))
                    }
                    
                    trackList
                } else {
                    // ViewModel数据未就绪，降级
                    playbackEngine.getSubtitleTracks()
                }
            } else {
                // 无ViewModel，使用原方法
                playbackEngine.getSubtitleTracks()
            }
            
            val trackNames = tracks.map { it.second }
            val currentSelection = tracks.indexOfFirst { it.third }

            showPopupDialogAtLastAnchor(
                trackNames,
                currentSelection,
                title = "Subtitle Track",
                showAbove = false,
                useFixedHeight = false,
                showScrollHint = false
            ) { position ->
                val trackId = tracks[position].first
                playbackEngine.setSubtitleTrack(trackId)
                
                // 保存字幕轨道选择
                val videoUri = (activity as? VideoUriProvider)?.getVideoUri()
                videoUri?.let { uri ->
                    preferencesManager.setSubtitleTrackId(uri.toString(), trackId)
                    Log.d(TAG, "Saved subtitle track: $trackId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show subtitle track dialog", e)
            DialogUtils.showToastShort(activity, "Failed to get subtitle tracks")
        }
    }

    /**
     * 显示字幕设置抽屉（合并延迟、样式、杂项）
     */
    fun showSubtitleSettingsDrawer() {
        val activity = activityRef.get() ?: return
        val videoUri = (activity as? VideoUriProvider)?.getVideoUri() ?: return
        val uriString = videoUri.toString()

        val currentDelay = preferencesManager.getSubtitleDelay(uriString)
        val currentScale = preferencesManager.getSubtitleScale(uriString).toFloat()
        val currentPosition = preferencesManager.getSubtitlePosition(uriString)
        val currentBorderSize = preferencesManager.getSubtitleBorderSize(uriString)
        val currentTextColor = preferencesManager.getSubtitleTextColor(uriString)
        val currentBorderColor = preferencesManager.getSubtitleBorderColor(uriString)
        val currentBackColor = preferencesManager.getSubtitleBackColor(uriString)
        val currentBorderStyle = preferencesManager.getSubtitleBorderStyle(uriString)

        composeOverlayManager.showSubtitleSettingsDrawer(
            currentDelay = currentDelay,
            currentScale = currentScale,
            currentPosition = currentPosition,
            currentBorderSize = currentBorderSize,
            currentTextColor = currentTextColor,
            currentBorderColor = currentBorderColor,
            currentBackColor = currentBackColor,
            currentBorderStyle = currentBorderStyle,
            onDelayChange = { newDelay ->
                playbackEngine.setSubtitleDelay(newDelay)
                preferencesManager.setSubtitleDelay(uriString, newDelay)
            },
            onScaleChange = { newScale ->
                playbackEngine.setSubtitleScale(newScale.toDouble())
                preferencesManager.setSubtitleScale(uriString, newScale.toDouble())
            },
            onPositionChange = { newPos ->
                playbackEngine.setSubtitlePosition(newPos)
                preferencesManager.setSubtitlePosition(uriString, newPos)
            },
            onBorderSizeChange = { newSize ->
                playbackEngine.setSubtitleBorderSize(newSize)
                preferencesManager.setSubtitleBorderSize(uriString, newSize)
            },
            onTextColorChange = { newColor ->
                playbackEngine.setSubtitleTextColor(newColor)
                preferencesManager.setSubtitleTextColor(uriString, newColor)
            },
            onBorderColorChange = { newColor ->
                playbackEngine.setSubtitleBorderColor(newColor)
                preferencesManager.setSubtitleBorderColor(uriString, newColor)
            },
            onBackColorChange = { newColor ->
                playbackEngine.setSubtitleBackColor(newColor)
                preferencesManager.setSubtitleBackColor(uriString, newColor)
            },
            onBorderStyleChange = { newStyle ->
                playbackEngine.setSubtitleBorderStyle(newStyle)
                preferencesManager.setSubtitleBorderStyle(uriString, newStyle)
            }
        )
    }

    /**
     * 显示字幕延迟调整对话框（已废弃）
     */
    @Deprecated("使用 showSubtitleSettingsDrawer 替代")
    fun showSubtitleDelayDialog() {
        val activity = activityRef.get() ?: return
        val videoUri = (activity as? VideoUriProvider)?.getVideoUri() ?: return
        val uriString = videoUri.toString()

        val currentDelay = preferencesManager.getSubtitleDelay(uriString)

        composeOverlayManager.showSubtitleDelayDialog(
            currentDelay = currentDelay,
            onDelayChange = { newDelay ->
                playbackEngine.setSubtitleDelay(newDelay)
                preferencesManager.setSubtitleDelay(uriString, newDelay)
            }
        )
    }

    /**
     * 显示字幕杂项设置对话框（已废弃）
     */
    @Deprecated("使用 showSubtitleSettingsDrawer 替代")
    fun showSubtitleMiscDialog() {
        val activity = activityRef.get() ?: return
        val videoUri = (activity as? VideoUriProvider)?.getVideoUri() ?: return
        val uriString = videoUri.toString()

        val currentScale = preferencesManager.getSubtitleScale(uriString).toFloat()
        val currentPos = preferencesManager.getSubtitlePosition(uriString)

        composeOverlayManager.showSubtitleMiscDialog(
            currentScale = currentScale,
            currentPosition = currentPos,
            onScaleChange = { newScale ->
                playbackEngine.setSubtitleScale(newScale.toDouble())
                preferencesManager.setSubtitleScale(uriString, newScale.toDouble())
            },
            onPositionChange = { newPos ->
                playbackEngine.setSubtitlePosition(newPos)
                preferencesManager.setSubtitlePosition(uriString, newPos)
            }
        )
    }

    /**
     * 显示字幕样式设置对话框（已废弃）
     */
    @Deprecated("使用 showSubtitleSettingsDrawer 替代")
    fun showSubtitleStyleDialog() {
        val activity = activityRef.get() ?: return
        val videoUri = (activity as? VideoUriProvider)?.getVideoUri() ?: return
        val uriString = videoUri.toString()

        val currentBorderSize = preferencesManager.getSubtitleBorderSize(uriString)

        composeOverlayManager.showSubtitleStyleDialog(
            currentBorderSize = currentBorderSize,
            onBorderSizeChange = { newSize ->
                playbackEngine.setSubtitleBorderSize(newSize)
                preferencesManager.setSubtitleBorderSize(uriString, newSize)
            }
        )
    }

    /**
     * 显示播放速度选择对话框
     */
    fun showSpeedDialog(currentSpeed: Double) {
        val activity = activityRef.get() ?: return

        val speedStrings = preferencesManager.getCustomSpeedPresets()
        val speedValues = speedStrings.mapNotNull { it.toDoubleOrNull() }.sorted()
        val speeds = speedValues.map { "${it}x" }
        val currentSelection = speedValues.indexOf(currentSpeed)

        showPopupDialogAtLastAnchor(
            speeds,
            currentSelection,
            title = "Playback Speed",
            showAbove = true,
            useFixedHeight = true,
            showScrollHint = true,
            horizontalAlignment = PopupHorizontalAlignment.CENTER
        ) { position ->
            val newSpeed = speedValues[position]
            dialogCallback?.onSpeedChanged(newSpeed)
            DialogUtils.showToastShort(activity, "Speed: ${speeds[position]}")
        }
    }

    /**
     * 显示Anime4K模式选择对话框
     */
    fun showAnime4KModeDialog(currentMode: Anime4KManager.Mode) {
        val activity = activityRef.get() ?: return

        val modes = listOf(
            Anime4KManager.Mode.OFF,
            Anime4KManager.Mode.A,
            Anime4KManager.Mode.B,
            Anime4KManager.Mode.C,
            Anime4KManager.Mode.A_PLUS,
            Anime4KManager.Mode.B_PLUS,
            Anime4KManager.Mode.C_PLUS
        )
        
        val modeNames = listOf(
            "Off - Original",
            "A - Sharp Reconstruct",
            "B - Soft Reconstruct",
            "C - Denoise",
            "A+ - Dual Enhancement",
            "B+ - Dual Soft",
            "C+ - Dual Denoise"
        )
        
        val currentSelection = modes.indexOf(currentMode)
        
        // 使用专门为Compose按钮设计的对话框显示方法
        showPopupDialogAtLastAnchor(
            modeNames,
            currentSelection,
            title = "Anime4K Mode",
            showAbove = true,  // 横屏和竖屏都显示在上方
            useFixedHeight = true,
            showScrollHint = true
        ) { position ->
            val selectedMode = modes[position]
            val enabled = selectedMode != Anime4KManager.Mode.OFF
            dialogCallback?.onAnime4KChanged(enabled, selectedMode, Anime4KManager.Quality.BALANCED)
            DialogUtils.showToastShort(activity, "Upscale mode: ${modeNames[position]}")
        }
    }

    /**
     * 显示更多选项对话框
     */
    fun showMoreOptionsDialog() {
        val activity = activityRef.get() ?: return

        // 检查是否有章节信息
        val chapterCount = MPVLib.getPropertyInt("chapter-list/count") ?: 0
        val hasChapters = chapterCount > 0
        
        val autoRotateEnabled =
            (activity as? MoreOptionsCallback)?.isAutoRotateEnabled() == true
        val autoRotateText = if (autoRotateEnabled) "Auto Rotate: On" else "Auto Rotate: Off"
        
        // 根据是否有章节动态构建菜单项
        val items = mutableListOf<String>()
        if (hasChapters) {
            items.add("Chapters")
        }
        items.addAll(listOf("Decoder", "Listen to Video", "Skip Intro/Outro", "Audio Equalizer", autoRotateText))
        
        // 根据屏幕方向决定对齐方式：竖屏靠右对齐，横屏居中
        val configuration = activity.resources.configuration
        val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        val horizontalAlignment = if (isPortrait) PopupHorizontalAlignment.END else PopupHorizontalAlignment.CENTER

        // 使用专门为Compose按钮设计的对话框显示方法
        showPopupDialogAtLastAnchor(
            items,
            selectedPosition = -1,
            title = "More Options",
            showAbove = false,
            useFixedHeight = true,
            showScrollHint = true,
            horizontalAlignment = horizontalAlignment,
            clampToScreen = false
        ) { position ->
            // 根据是否有章节项调整索引映射
            // 有章节时：0=章节, 1=解码, 2=听视频, 3=片头片尾, 4=均衡器, 5=自动旋转
            // 无章节时：0=解码, 1=听视频, 2=片头片尾, 3=均衡器, 4=自动旋转
            val actualAction = if (hasChapters) {
                position
            } else {
                position + 1  // 跳过章节索引
            }
            
            when (actualAction) {
                0 -> showChapterDialog()
                1 -> showDecoderDialog()  // 解码方式
                2 -> (activity as? MoreOptionsCallback)?.onBackgroundPlayback()  // 听视频
                3 -> (activity as? MoreOptionsCallback)?.onShowSkipSettings()  // 片头片尾设置
                4 -> (activity as? MoreOptionsCallback)?.onShowEqualizer()  // 音频均衡器
                5 -> (activity as? MoreOptionsCallback)?.onToggleAutoRotate()
            }
        }
    }

    /**
     * 显示章节列表面板（右侧抽屉式）
     */
    fun showChapterDialog() {
        val activity = activityRef.get() ?: return

        try {
            val chapterCount = MPVLib.getPropertyInt("chapters") ?: 0
            if (chapterCount <= 0) {
                DialogUtils.showToastShort(activity, "No chapter info for this video")
                return
            }

            val chapters = mutableListOf<Pair<String, Double>>()
            for (i in 0 until chapterCount) {
                val title = MPVLib.getPropertyString("chapter-list/$i/title") ?: "Chapter ${i + 1}"
                val time = MPVLib.getPropertyDouble("chapter-list/$i/time") ?: 0.0
                chapters.add(Pair(title, time))
            }

            val currentChapter = MPVLib.getPropertyInt("chapter") ?: 0

            composeOverlayManager.showChapterDrawer(
                chapters = chapters,
                currentChapter = currentChapter,
                onChapterClick = { position ->
                    MPVLib.setPropertyInt("chapter", position)

                    // 同步弹幕位置
                    try {
                        val chapterTime = MPVLib.getPropertyDouble("chapter-list/$position/time") ?: 0.0
                        danmakuManager.seekTo((chapterTime * 1000).toLong())
                        Log.d(TAG, "Chapter jump: synced danmaku to ${chapterTime}s")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync danmaku on chapter jump", e)
                    }

                    DialogUtils.showToastShort(activity, "Jumped to: ${chapters[position].first}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show chapter drawer", e)
        }
    }

    /**
     * 显示弹幕菜单对话框
     */
    fun showDanmakuDialog() {
        val activity = activityRef.get() ?: return

        // 简化的菜单项：移除了显示/隐藏选项，合并弹幕来源
        val menuItems = listOf(
            "Select Danmaku",
            "Match Danmaku",
            "Danmaku Track",
            "Danmaku Settings"
        )

        showPopupDialogAtLastAnchor(
            menuItems,
            selectedPosition = -1,
            title = "Danmaku",
            showAbove = false,
            useFixedHeight = false,
            showScrollHint = false
        ) { position ->
            when (position) {
                0 -> showDanmakuSourceDialog()  // 弹幕来源选择（三级菜单）
                1 -> (activity as? DanmakuDialogCallback)?.onMatchDanmaku()  // 匹配弹幕
                2 -> showDanmakuTrackDialog()  // 弹幕轨道
                3 -> showDanmakuSettingsDialog()  // 弹幕设置
            }
        }
    }
    
    /**
     * 显示弹幕来源选择对话框（三级菜单）
     */
    private fun showDanmakuSourceDialog() {
        val activity = activityRef.get() ?: return

        val sourceItems = listOf(
            "Local Danmaku",
            "Online Danmaku"
        )

        showPopupDialogAtLastAnchor(
            sourceItems,
            selectedPosition = -1,
            title = "Danmaku Source",
            showAbove = false,
            useFixedHeight = false,
            showScrollHint = false
        ) { position ->
            when (position) {
                0 -> (activity as? DanmakuDialogCallback)?.onImportDanmaku()  // 本地弹幕
                1 -> (activity as? DanmakuDialogCallback)?.onSearchNetworkDanmaku()  // 网络弹幕
            }
        }
    }
    
    /**
     * 显示弹幕轨道对话框
     */
    private fun showDanmakuTrackDialog() {
        val activity = activityRef.get() ?: return
        
        val currentPath = danmakuManager.getCurrentDanmakuPath()
        if (currentPath == null) {
            DialogUtils.showToastShort(activity, "No danmaku file loaded")
            return
        }
        
        // 获取文件名
        val fileName = File(currentPath).name
        
        val menuItems = listOf(
            "✓ $fileName",
            "Disable Danmaku Track"
        )
        
        // 根据trackSelected状态确定选中项：true=0（弹幕轨道），false=1（取消弹幕轨道）
        val selectedIndex = if (danmakuManager.getTrackSelected()) 0 else 1
        
        showPopupDialogAtLastAnchor(
            menuItems,
            selectedPosition = selectedIndex,
            title = "Danmaku Track",
            showAbove = false,
            useFixedHeight = false,
            showScrollHint = false
        ) { position ->
            when (position) {
                0 -> {
                    // 保持当前轨道,不做任何操作
                    DialogUtils.showToastShort(activity, "Danmaku track selected")
                }
                1 -> {
                    // 取消弹幕轨道(类似 DanDanPlay 的 removeTrack)
                    danmakuManager.setTrackSelected(false)
                    danmakuManager.setVisibility(false)
                    com.fam4k007.videoplayer.danmaku.DanmakuConfig.setEnabled(false)
                    (activity as? DanmakuDialogCallback)?.onDanmakuVisibilityChanged(false)
                    DialogUtils.showToastShort(activity, "Danmaku track deselected")
                }
            }
        }
    }

    /**
     * 显示弹幕设置对话框（Compose版本）
     */
    fun showDanmakuSettingsDialog() {
        activityRef.get() ?: return
        
        // 获取当前弹幕文件路径
        val danmakuPath = danmakuManager.getCurrentDanmakuPath()
        val hasDanmaku = danmakuPath != null
        
        // 从 DanmakuConfig 读取当前值
        val currentSize = com.fam4k007.videoplayer.danmaku.DanmakuConfig.size
        val currentSpeed = com.fam4k007.videoplayer.danmaku.DanmakuConfig.speed
        val currentAlpha = com.fam4k007.videoplayer.danmaku.DanmakuConfig.alpha
        val currentStroke = com.fam4k007.videoplayer.danmaku.DanmakuConfig.stroke
        val currentShowScroll = com.fam4k007.videoplayer.danmaku.DanmakuConfig.showScrollDanmaku
        val currentShowTop = com.fam4k007.videoplayer.danmaku.DanmakuConfig.showTopDanmaku
        val currentShowBottom = com.fam4k007.videoplayer.danmaku.DanmakuConfig.showBottomDanmaku
        val currentDisplayArea = com.fam4k007.videoplayer.danmaku.DanmakuConfig.displayAreaPercent
        val currentMaxScreenNum = com.fam4k007.videoplayer.danmaku.DanmakuConfig.maxScreenNum
        
        composeOverlayManager.showDanmakuSettingsDrawer(
            hasDanmakuLoaded = hasDanmaku,
            currentSize = currentSize,
            currentSpeed = currentSpeed,
            currentAlpha = currentAlpha,
            currentStroke = currentStroke,
            currentShowScroll = currentShowScroll,
            currentShowTop = currentShowTop,
            currentShowBottom = currentShowBottom,
            currentDisplayArea = currentDisplayArea,
            currentMaxScreenNum = currentMaxScreenNum,
            onSizeChange = { size ->
                com.fam4k007.videoplayer.danmaku.DanmakuConfig.setSize(size)
                danmakuManager.updateSize()
            },
            onSpeedChange = { speed ->
                com.fam4k007.videoplayer.danmaku.DanmakuConfig.setSpeed(speed)
                danmakuManager.updateSpeed()
            },
            onAlphaChange = { alpha ->
                com.fam4k007.videoplayer.danmaku.DanmakuConfig.setAlpha(alpha)
                danmakuManager.updateAlpha()
            },
            onStrokeChange = { stroke ->
                com.fam4k007.videoplayer.danmaku.DanmakuConfig.setStroke(stroke)
                danmakuManager.updateStroke()
            },
            onShowScrollChange = { show ->
                com.fam4k007.videoplayer.danmaku.DanmakuConfig.setShowScrollDanmaku(show)
                danmakuManager.updateScrollDanmaku()
            },
            onShowTopChange = { show ->
                com.fam4k007.videoplayer.danmaku.DanmakuConfig.setShowTopDanmaku(show)
                danmakuManager.updateTopDanmaku()
            },
            onShowBottomChange = { show ->
                com.fam4k007.videoplayer.danmaku.DanmakuConfig.setShowBottomDanmaku(show)
                danmakuManager.updateBottomDanmaku()
            },
            onDisplayAreaChange = { percent ->
                com.fam4k007.videoplayer.danmaku.DanmakuConfig.setDisplayAreaPercent(percent)
                danmakuManager.updateMaxLine()
            },
            onMaxScreenNumChange = { num ->
                com.fam4k007.videoplayer.danmaku.DanmakuConfig.setMaxScreenNum(num)
                danmakuManager.updateMaxScreenNum()
            }
        )
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        // 释放所有活动的 PopupWindow
        activePopupWindows.forEach { popupWindow ->
            try {
                if (popupWindow.isShowing) {
                    popupWindow.dismiss()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing popup window", e)
            }
        }
        activePopupWindows.clear()
        
        // 清理回调
        dialogCallback = null
    }

    // 内部Adapter类
    private inner class PopupMenuAdapter(
        private val items: List<String>,
        private var selectedPosition: Int,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<PopupMenuAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val itemText: TextView = view.findViewById(R.id.itemText)
            val innerLayout: LinearLayout = view.findViewById(R.id.innerLayout)

            fun bind(position: Int) {
                val activity = activityRef.get() ?: return
                
                itemText.text = items[position]

                val isSelected = selectedPosition >= 0 && position == selectedPosition
                if (isSelected) {
                    val typedValue = android.util.TypedValue()
                    activity.theme.resolveAttribute(
                        com.google.android.material.R.attr.colorPrimary,
                        typedValue,
                        true
                    )
                    itemText.setTextColor(typedValue.data)
                    itemText.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    val typedValue = android.util.TypedValue()
                    activity.theme.resolveAttribute(
                        R.attr.colorDialogText,
                        typedValue,
                        true
                    )
                    itemText.setTextColor(typedValue.data)
                    itemText.setTypeface(null, android.graphics.Typeface.NORMAL)
                }

                innerLayout.setOnClickListener {
                    onItemClick(position)
                }
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val activity = activityRef.get() ?: throw IllegalStateException("Activity is null")
            val view = activity.layoutInflater.inflate(R.layout.dialog_selection_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount() = items.size
    }
}

// 回调接口
interface SubtitleDialogCallback {
    fun onImportSubtitle()
}

interface DanmakuDialogCallback {
    fun onImportDanmaku()
    fun onDanmakuVisibilityChanged(visible: Boolean)
    fun onSearchNetworkDanmaku()
    fun onMatchDanmaku()
}

interface MoreOptionsCallback {
    fun onScreenshot()
    fun onShowSkipSettings()
    fun onShowEqualizer()
    fun onTogglePortraitUi()
    fun onToggleAutoRotate()
    fun isAutoRotateEnabled(): Boolean
    fun onBackgroundPlayback()
}

interface AudioOptionsCallback {
    fun onAddAudioTrack()
}

interface VideoAspectCallback {
    fun onVideoAspectChanged(aspect: VideoAspect)
}

interface VideoUriProvider {
    fun getVideoUri(): android.net.Uri?
}
