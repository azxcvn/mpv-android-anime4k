package com.fam4k007.videoplayer.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.presentation.PlayerViewModel
import com.fam4k007.videoplayer.utils.ThumbnailCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AudioPlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val videoTitle by viewModel.videoTitle.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val position by viewModel.precisePosition.collectAsState()
    val paused by viewModel.paused.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val hasNext by viewModel.hasNext.collectAsState()
    val hasPrevious by viewModel.hasPrevious.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val videoList by viewModel.videoList.collectAsState()

    val bgColor = Color(0xFF121212)
    val textColor = Color.White
    val textSecondary = Color.White.copy(alpha = 0.6f)
    val sheetBg = Color(0xFF252525)
    val sheetText = Color.White

    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(currentIndex) {
        val video = videoList.getOrNull(currentIndex) ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val cm = ThumbnailCacheManager.getInstance(context)
                coverBitmap = cm.getThumbnail(context, Uri.parse(video.uri), video.duration)
            } catch (_: Exception) { }
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(if (duration > 0) (position / duration).toFloat() else 0f) }
    val displayFraction = if (isDragging) dragFraction
        else if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f

    var showSpeedSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(bgColor)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically)) {

            Spacer(Modifier.weight(0.3f))

            // 封面 16:9 — 放大、向上
            Box(Modifier.fillMaxWidth(0.85f).aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center) {
                if (coverBitmap != null && !coverBitmap!!.isRecycled) {
                    Image(coverBitmap!!.asImageBitmap(), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                } else {
                    Icon(painterResource(R.drawable.top_speed_24_regular), null,
                        tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(48.dp))
                }
            }

            Spacer(Modifier.height(28.dp))

            // 标题 — 缩小，比缩略图小
            Text(videoTitle.ifBlank { "Untitled Video" }, color = textColor, fontSize = 14.sp,
                fontWeight = FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))

            Spacer(Modifier.height(28.dp))

            // 进度条
            Column(Modifier.fillMaxWidth()) {
                Slider(value = displayFraction,
                    onValueChange = { isDragging = true; dragFraction = it },
                    onValueChangeFinished = {
                        viewModel.seekTo((dragFraction * duration).toInt()); isDragging = false
                    },
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF64B5F6),
                        activeTrackColor = Color(0xFF64B5F6),
                        inactiveTrackColor = textColor.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTimeAudio(if (isDragging) (dragFraction * duration).toLong() else position.toLong()),
                        color = textSecondary, fontSize = 12.sp)
                    Text(formatTimeAudio(duration.toLong()), color = textSecondary, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(32.dp))

            // 五个按钮
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                AudioControlButton(R.drawable.top_speed_24_regular, "${speed}x", iconTint = textColor) { showSpeedSheet = true }
                AudioControlButton(R.drawable.ic_player_previous1, iconTint = textColor, enabled = hasPrevious) { viewModel.previousVideo() }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    IconButton({ viewModel.togglePlayPause() }, Modifier.size(48.dp)) {
                        Icon(painterResource(if (paused == true) R.drawable.ic_player_play1 else R.drawable.ic_player_pause1),
                            null, tint = textColor, modifier = Modifier.size(26.dp))
                    }
                    Box(Modifier.height(16.dp))
                }
                AudioControlButton(R.drawable.ic_player_next1, iconTint = textColor, enabled = hasNext) { viewModel.nextVideo() }
                AudioControlButton(R.drawable.ic_apps_list_20_filled, iconTint = textColor) { showPlaylistSheet = true }
            }

            Spacer(Modifier.weight(0.1f))
        }

        // 顶栏
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.arrow_left_48_regular), "Back",
                    tint = textSecondary, modifier = Modifier.size(28.dp))
            }
            Text("Listen to Video", color = textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(48.dp))
        }
    }

    if (showSpeedSheet) SpeedBottomSheet(speed, sheetBg, sheetText, { viewModel.setSpeed(it.toDouble()); showSpeedSheet = false }) { showSpeedSheet = false }
    if (showPlaylistSheet) PlaylistBottomSheet(videoList, currentIndex, sheetBg, sheetText,
        { idx -> viewModel.playVideoAtIndex(idx); showPlaylistSheet = false }) { showPlaylistSheet = false }
}

@Composable
private fun AudioControlButton(icon: Int, label: String? = null, iconTint: Color = Color.White, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(icon), null, tint = iconTint.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(26.dp))
        }
        Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
            if (label != null && enabled) Text(label, color = iconTint.copy(alpha = 0.5f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun SpeedBottomSheet(currentSpeed: Float, bg: Color, text: Color, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    BottomSheet("Playback Speed", bg, text, onDismiss) {
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f).forEach { s ->
            val cur = s == currentSpeed
            Row(Modifier.fillMaxWidth().clickable { onSelect(s) }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${s}x", color = if (cur) Color(0xFF64B5F6) else text.copy(alpha = 0.8f),
                    fontSize = 16.sp, fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal)
                if (cur) Icon(painterResource(R.drawable.ic_player_pause1), null, tint = Color(0xFF64B5F6), modifier = Modifier.size(20.dp))
            }
            if (s != 3.0f) HorizontalDivider(color = text.copy(alpha = 0.08f))
        }
    }
}

@Composable
private fun PlaylistBottomSheet(videoList: List<com.fam4k007.videoplayer.VideoFileParcelable>, currentIndex: Int, bg: Color, text: Color, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    BottomSheet("Playlist", bg, text, onDismiss) {
        videoList.forEachIndexed { i, v ->
            val cur = i == currentIndex
            Text(v.name.ifBlank { "Video ${i + 1}" },
                color = if (cur) Color(0xFF64B5F6) else text.copy(alpha = 0.8f),
                fontSize = 14.sp, fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { onSelect(i) }.padding(vertical = 10.dp))
            if (i != videoList.lastIndex) HorizontalDivider(color = text.copy(alpha = 0.08f))
        }
    }
}

@Composable
private fun BottomSheet(title: String, bg: Color, text: Color, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }) {
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.5f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = bg) {
            Column(Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Text(title, color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    content()
                }
                HorizontalDivider(color = text.copy(alpha = 0.1f))
                Box(Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center) {
                    Text("Close", color = text.copy(alpha = 0.5f), fontSize = 14.sp)
                }
            }
        }
    }
}

internal fun formatTimeAudio(seconds: Long): String {
    val t = seconds.coerceAtLeast(0); val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
