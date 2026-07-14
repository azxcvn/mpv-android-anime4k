package com.fam4k007.videoplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fam4k007.videoplayer.presentation.PlayerViewModel

/**
 * 手势指示器（音量/亮度）— 现代化垂直样式
 */
@Composable
fun GestureIndicators(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val isBrightnessSliderShown by viewModel.isBrightnessSliderShown.collectAsState()
    val isVolumeSliderShown by viewModel.isVolumeSliderShown.collectAsState()
    val currentBrightness by viewModel.currentBrightness.collectAsState()
    val currentVolume by viewModel.currentVolume.collectAsState()
    val volumeBoostEnabled by viewModel.volumeBoostEnabled.collectAsState()
    
    Box(modifier = modifier.fillMaxSize()) {
        // 音量指示器（左侧）
        AnimatedVisibility(
            visible = isVolumeSliderShown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            val maxVol = if (volumeBoostEnabled) 300f else 100f
            ModernGestureIndicator(
                icon = {
                    Crossfade(
                        targetState = volumeIcon(currentVolume, volumeBoostEnabled),
                        animationSpec = tween(300)
                    ) { icon -> 
                        Icon(
                            imageVector = icon,
                            contentDescription = "Volume",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                progress = currentVolume / maxVol,
                text = "$currentVolume%",
                modifier = Modifier.padding(start = 40.dp),
                fillColor = Color(0xFF4FC3F7)  // 蓝色
            )
        }
        
        // 亮度指示器（右侧）
        AnimatedVisibility(
            visible = isBrightnessSliderShown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            ModernGestureIndicator(
                icon = {
                    Crossfade(
                        targetState = brightnessIcon(currentBrightness),
                        animationSpec = tween(300)
                    ) { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = "Brightness",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                progress = currentBrightness,
                text = "${(currentBrightness * 100).toInt()}%",
                modifier = Modifier.padding(end = 40.dp),
                fillColor = Color(0xFFFFB74D)  // 橙色
            )
        }
    }
}

/**
 * 现代化垂直手势指示器
 * 风格：圆角半透明深色背景 + 垂直胶囊进度条
 */
@Composable
private fun ModernGestureIndicator(
    icon: @Composable () -> Unit,
    progress: Float,
    text: String,
    modifier: Modifier = Modifier,
    fillColor: Color = Color.White
) {
    val barWidth = 8.dp
    val barHeight = 90.dp
    val trackColor = Color.White.copy(alpha = 0.2f)
    
    Column(
        modifier = modifier
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标
        icon()
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // 垂直进度条容器
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(barHeight)
                .clip(RoundedCornerShape(barWidth / 2))
                .background(trackColor),
            contentAlignment = Alignment.BottomCenter
        ) {
            // 填充部分（从底部向上）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(fillColor)
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // 百分比文字
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
    }
}

// ==================== 动态图标选择 ====================

/**
 * 根据音量值返回对应的图标，四级递进
 */
private fun volumeIcon(volume: Int, boostEnabled: Boolean): ImageVector {
    if (boostEnabled && volume > 100) return Icons.Default.VolumeUp  // 增强模式 100%+ 用最高级
    return when {
        volume <= 0 -> Icons.Default.VolumeOff
        volume <= 33 -> Icons.Default.VolumeMute
        volume <= 66 -> Icons.Default.VolumeDown
        else -> Icons.Default.VolumeUp
    }
}

/**
 * 根据亮度值返回对应的图标，三级递进
 */
private fun brightnessIcon(brightness: Float): ImageVector {
    return when {
        brightness <= 0.33f -> Icons.Default.Brightness5
        brightness <= 0.66f -> Icons.Default.Brightness6
        else -> Icons.Default.Brightness7
    }
}
