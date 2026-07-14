package com.fam4k007.videoplayer.manager.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 杜比视界提示对话框
 * 当检测到视频为杜比视界编码且未开启 GPU Next 时弹出，
 * 提示用户启用 GPU Next 渲染 + 软解以解决画面发绿问题。
 */
@Composable
fun DolbyVisionHintDialog(
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Dolby Vision Notice",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                "If the screen appears green, enable GPU Next Rendering and switch to software decoding in Settings - Playback Settings. If the issue persists, your device may not support Dolby Vision playback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDontShowAgain) {
                Text("Don't show again", color = MaterialTheme.colorScheme.error)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
