package com.fam4k007.videoplayer.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 进度条拖动时的缩略图预览气泡
 * 跟随进度位置水平移动，支持章节标题、加载状态
 *
 * @param bitmap 缩略图位图
 * @param timeSec 当前时间位置（秒）
 * @param fraction 进度比例 [0,1]，用于水平定位
 * @param show 是否显示
 * @param isLoading 是否加载中
 * @param chapterTitle 章节标题（可选）
 */
@Composable
fun SeekbarThumbnailPreview(
    bitmap: Bitmap?,
    timeSec: Long,
    fraction: Float,
    show: Boolean,
    isLoading: Boolean = false,
    chapterTitle: String? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val previewWidth = 160.dp
            val progress = fraction.coerceIn(0f, 1f)
            val xOffset = (maxWidth - previewWidth).coerceAtLeast(0.dp) * progress
            val previewShape = RoundedCornerShape(10.dp)

            Column(
                modifier = Modifier.offset(x = xOffset).width(previewWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                chapterTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    Box(
                        modifier = Modifier.padding(bottom = 5.dp)
                            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text(title, color = Color.White, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .clip(previewShape).border(1.dp, Color.White.copy(alpha = 0.2f), previewShape)
                        .background(Color.Black.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null && !bitmap.isRecycled) {
                        Image(bitmap.asImageBitmap(), null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    if (isLoading) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        }
                    }
                }

                Box(
                    modifier = Modifier.padding(top = 5.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(formatThumbnailTime(timeSec), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun formatThumbnailTime(seconds: Long): String {
    val t = seconds.toInt()
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
