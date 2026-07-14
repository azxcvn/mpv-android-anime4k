package com.fam4k007.videoplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fam4k007.videoplayer.presentation.PlayerViewModel

/**
 * Seek指示器（快进/快退提示）
 * 按钮点击时显示在顶部，双击手势时显示在左侧/右侧
 */
@Composable
fun SeekIndicator(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val seekIndicatorShown by viewModel.seekIndicatorShown.collectAsState()
    val seekOffset by viewModel.seekOffset.collectAsState(initial = 0)
    val seekIndicatorAtTop by viewModel.seekIndicatorAtTop.collectAsState()
    
    if (seekIndicatorShown) {
        val alignment = if (seekIndicatorAtTop) {
            Alignment.TopCenter
        } else {
            // 双击手势：正数（快进）显示在右侧，负数（快退）显示在左侧
            if (seekOffset > 0) Alignment.CenterEnd else Alignment.CenterStart
        }
        
        val topPadding = if (seekIndicatorAtTop) 60.dp else 0.dp
        val sidePadding = if (!seekIndicatorAtTop) 48.dp else 0.dp
        
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = alignment
        ) {
            if (seekIndicatorAtTop) {
                // 顶部指示器（按钮点击）：水平排列，整体高度调矮
                Row(
                    modifier = Modifier
                        .padding(top = topPadding)
                        .widthIn(min = 100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (seekOffset.compareTo(0) > 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = if (seekOffset.compareTo(0) > 0) "Fast Forward" else "Rewind",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (seekOffset.compareTo(0) > 0) "+${seekOffset}s" else "${seekOffset}s",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // 侧边指示器（双击手势）：上下排列，文字在上图标在下
                Column(
                    modifier = Modifier
                        .padding(start = sidePadding, end = sidePadding)
                        .widthIn(min = 80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (seekOffset.compareTo(0) > 0) "+${seekOffset}s" else "${seekOffset}s",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Icon(
                        imageVector = if (seekOffset.compareTo(0) > 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = if (seekOffset.compareTo(0) > 0) "Fast Forward" else "Rewind",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
