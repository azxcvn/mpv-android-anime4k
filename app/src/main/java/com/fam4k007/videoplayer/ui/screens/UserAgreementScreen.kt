package com.fam4k007.videoplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fam4k007.videoplayer.R
import com.fam4k007.videoplayer.ui.theme.spacing

/**
 * 用户协议界面 - 现代 Material 3 风格
 * 首次启动时全屏展示，用户必须同意才能继续使用
 *
 * @param showActions 是否显示交互元素（复选框、同意/拒绝按钮），预览模式设为 false
 */
@Composable
fun UserAgreementScreen(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    showActions: Boolean = true,
    onBack: (() -> Unit)? = null
) {
    var hasScrolledToBottom by remember { mutableStateOf(false) }
    var isChecked by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // 检测滚动到底部
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        if (scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 50) {
            hasScrolledToBottom = true
        }
    }

    val canAgree = isChecked && hasScrolledToBottom

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MaterialTheme.spacing.medium)
            .padding(top = MaterialTheme.spacing.large)
    ) {
        // 头部
        AgreementHeader(isPreview = !showActions, onBack = onBack)

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

        // 协议内容卡片
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(MaterialTheme.spacing.medium)
            ) {
                AgreementContent(showFullContent = showActions)
            }
        }

        if (showActions) {
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))

            // 滚动提示
            AnimatedVisibility(
                visible = !hasScrolledToBottom,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scroll down to read the full agreement",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))

            // 复选框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { isChecked = it },
                    enabled = hasScrolledToBottom,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buildAnnotatedString {
                        append("I have read and fully ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                            append("understand")
                        }
                        append("all terms above, and agree to abide by this agreement")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasScrolledToBottom) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))

            // 按钮区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 拒绝按钮
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "Decline",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 同意按钮
                Button(
                    onClick = onAgree,
                    enabled = canAgree,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Agree",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
}


@Composable
private fun AgreementHeader(isPreview: Boolean = false, onBack: (() -> Unit)? = null) {
    if (isPreview) {
        // 预览模式：带返回按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "User Agreement",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        // 启动模式：居中带图标
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "User Agreement",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Welcome to Meow Player!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AgreementContent(showFullContent: Boolean = true) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 重要声明
        AgreementSection(
            icon = Icons.Default.Warning,
            title = "I. Important Notice",
            iconTint = MaterialTheme.colorScheme.error
        ) {
            BulletPoint("This app is completely free and open source, licensed under GPL-3.0-or-later")
            BulletPoint("This app is intended for learning and testing purposes only")
            BulletPoint("We strongly oppose and do not condone any form of piracy, illegal redistribution, or other unlawful activities")
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        // 隐私政策
        AgreementSection(
            icon = Icons.Default.Security,
            title = "II. Privacy Policy",
            iconTint = MaterialTheme.colorScheme.primary
        ) {
            SubTitle("[Data Collection]")
            CheckPoint("We do NOT collect any personal information")
            CheckPoint("We do NOT upload any data to any server (we have no servers)")
            CheckPoint("We do NOT share user data with any third party")
            CheckPoint("All features run locally on your device")
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SubTitle("[Permissions]")
            Text(
                text = "This app requires the following permissions:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PermissionItem("Manage All Files", "Scan local video files, save subtitles and danmaku files")
            PermissionItem("Network", "Online playback, danmaku download, WebDAV, etc.")

            Spacer(modifier = Modifier.height(8.dp))

            SubTitle("[WebDAV Storage]")
            BulletPoint("WebDAV credentials and server addresses are stored locally only")
            BulletPoint("They are never synced or uploaded to any third-party server")
            BulletPoint("You can clear saved WebDAV info anytime in Settings")

            Spacer(modifier = Modifier.height(8.dp))

            SubTitle("[Login Security]")
            Text(
                text = "If you choose to use Bilibili login:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BulletPoint("Login credentials are encrypted with AES-256 and stored locally")
            BulletPoint("Encryption keys are protected by Android KeyStore hardware")
            BulletPoint("Login info is only used for Bilibili API calls, never uploaded elsewhere")
            BulletPoint("You can log out anytime in Settings")
            BulletPoint("All login data is permanently destroyed when the app is uninstalled")
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        // 法律风险警告
        AgreementSection(
            icon = Icons.Default.Warning,
            title = "III. Legal Disclaimer",
            iconTint = Color(0xFFFF6B6B)
        ) {
            WarningCard {
                SubTitle("[Video/Bangumi Download]")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠️ Important Warning:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B6B)
                )
                Spacer(modifier = Modifier.height(8.dp))
                BulletPoint("This feature is for personal learning and technical research only")
                BulletPoint("Commercial use is strictly prohibited")
                BulletPoint("Downloaded content is copyright of the original owner")
                BulletPoint("It is recommended to delete downloaded content within 24 hours")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SubTitle("[Disclaimer]")
            BulletPoint("Any consequences arising from the use of this app are the sole responsibility of the user")
            BulletPoint("The developer assumes no responsibility for any losses or damages")
            BulletPoint("Users assume all legal risks associated with commercial use of download features")
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SubTitle("[Copyright]")
            BulletPoint("\"Bilibili\" name, logo and related graphics are registered trademarks of Shanghai Hodei Information Technology Co., Ltd.")
            BulletPoint("This app is an independent third-party tool with no affiliation to Bilibili")
            BulletPoint("Content obtained through this app is copyright of the respective owners")
        }

        if (showFullContent) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // 用户承诺
            AgreementSection(
                icon = Icons.Default.CheckCircle,
                title = "IV. User Agreement",
                iconTint = MaterialTheme.colorScheme.tertiary
            ) {
                Text(
                    text = "By tapping \"Agree & Continue\", you confirm that:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                CheckPoint("You have read and fully understood all terms above")
                CheckPoint("You agree to abide by all terms of this agreement")
                CheckPoint("You will not use this app for any illegal or commercial purposes")
                CheckPoint("You understand and accept that all risks are borne by you")
                CheckPoint("You agree that the developer is not liable for misuse of this app")

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tapping \"Decline\" will prevent you from using this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 开源致谢
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "This app uses several excellent open source components (see Licenses)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 协议更新说明
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "If this agreement is updated, it will be shown again on next launch. Please review it then.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 感谢和更新时间
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Thank you for your understanding and cooperation!",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Last updated: 2026-05-18",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun AgreementSection(
    icon: ImageVector,
    title: String,
    iconTint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CheckPoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "✓",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PermissionItem(permission: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = permission,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun WarningCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF6B6B).copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}
