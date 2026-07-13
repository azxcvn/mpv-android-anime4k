# ProGuard rules for release build

# ============================================
# 日志优化：Release版本移除DEBUG/INFO日志
# ============================================

# 移除Android Log的调试日志（保留Warning和Error）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# 移除自定义Logger的调试日志
-assumenosideeffects class com.fam4k007.videoplayer.utils.Logger {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** sensitive(...);
}

# ============================================
# 核心规则：保护 MPV 和 Native 调用
# ============================================

# 1. 保护 MPV 库（最重要！）
# 注意：使用的是 is.xyz.mpv，不是 dev.jdtech.mpv
-keep class is.xyz.mpv.** { *; }
-keep interface is.xyz.mpv.** { *; }
-keepclassmembers class is.xyz.mpv.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# 特别保护 MPVLib 和 BaseMPVView
-keep class is.xyz.mpv.MPVLib { *; }
-keep class is.xyz.mpv.BaseMPVView { *; }
-keep class is.xyz.mpv.MPVLib$** { *; }

# 2. 保护所有 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 3. 保护 Parcelable（用于 Intent 传递数据）
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# 4. 保护 Gson 序列化模型（DanDanPlay API）
# DanDanPlayApi 使用 Gson 反射反序列化 JSON，混淆后字段名不匹配导致解析失败
-keep class com.fam4k007.videoplayer.dandanplay.** { *; }

# 5. 保护自定义 View（防止 CustomMPVView 被混淆）
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保护所有 Activity（确保 findViewById 和 Intent 正常工作）
-keep public class * extends androidx.appcompat.app.AppCompatActivity {
    public <methods>;
}
-keep public class * extends android.app.Activity {
    public <methods>;
}

# 5. 保护项目中的关键类
-keep class com.fam4k007.videoplayer.player.CustomMPVView { *; }
-keep class com.fam4k007.videoplayer.player.PlaybackEngine { *; }
-keep class com.fam4k007.videoplayer.player.PlayerControlsManager { *; }
-keep class com.fam4k007.videoplayer.player.GestureHandler { *; }

# 6. 保护设置管理和常量类（防止 SharedPreferences 键名被混淆）
-keep class com.fam4k007.videoplayer.AppConstants { *; }
-keep class com.fam4k007.videoplayer.AppConstants$** { *; }
-keep class com.fam4k007.videoplayer.preferences.PreferencesManager { *; }

# 7. 保护数据类
-keep class com.fam4k007.videoplayer.VideoFolder { *; }
-keep class com.fam4k007.videoplayer.VideoFile { *; }
-keep class com.fam4k007.videoplayer.VideoFileParcelable { *; }

# ============================================
# 其他库规则
# ============================================

# Glide 图片加载库
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class com.bumptech.glide.** { *; }
-keepclassmembers class com.bumptech.glide.** { *; }

# Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}

# ============================================
# WebDAV 相关规则
# ============================================

# Sardine WebDAV 客户端
-keep class com.xyoye.sardine.** { *; }
-keepclassmembers class com.xyoye.sardine.** { *; }
-dontwarn com.xyoye.sardine.**

# Simple XML - XML 解析库
-keep class org.simpleframework.xml.** { *; }
-keepclassmembers class org.simpleframework.xml.** { *; }
-dontwarn org.simpleframework.xml.**

# 忽略 javax.xml.stream (Android 不包含这些类，但 Simple XML 可以用其他方式解析)
-dontwarn javax.xml.stream.**

# 保护 WebDAV 相关类（正确包路径）
-keep class com.fam4k007.videoplayer.ui.webdav.** { *; }
-keepclassmembers class com.fam4k007.videoplayer.ui.webdav.** { *; }
-keep class com.fam4k007.videoplayer.domain.webdav.** { *; }
-keepclassmembers class com.fam4k007.videoplayer.domain.webdav.** { *; }
-keep class com.fam4k007.videoplayer.data.model.WebDavAccount { *; }
-keepclassmembers class com.fam4k007.videoplayer.data.model.WebDavAccount { *; }

# OkHttp (WebDAV 依赖)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ============================================
# Bilibili 相关规则
# ============================================

# Jsoup HTML 解析
-keep class org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Gson JSON 解析
-keep class com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Gson 使用泛型和反射进行序列化/反序列化，需要保护相关特性
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# 保护所有使用 @SerializedName 的字段
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 保护 Bilibili 相关数据类（完整保护，防止混淆）
-keep class com.fam4k007.videoplayer.bilibili.** { *; }
-keepclassmembers class com.fam4k007.videoplayer.bilibili.** { *; }

# 保护 Bilibili 下载功能（新增）
-keep class com.fam4k007.videoplayer.download.** { *; }
-keepclassmembers class com.fam4k007.videoplayer.download.** { *; }

# 保护下载相关的数据类和枚举
-keep class com.fam4k007.videoplayer.download.DownloadItem { *; }
-keep class com.fam4k007.videoplayer.download.MediaParseResult { *; }
-keep class com.fam4k007.videoplayer.download.EpisodeInfo { *; }
-keep class com.fam4k007.videoplayer.download.VideoInfo { *; }
-keep class com.fam4k007.videoplayer.download.DownloadFragment { *; }
-keep enum com.fam4k007.videoplayer.download.MediaType { *; }
-keep enum com.fam4k007.videoplayer.download.DownloadStatus { *; }

# 保护下载管理器的关键方法（防止被内联或移除）
-keep class com.fam4k007.videoplayer.download.BilibiliDownloadManager {
    public <methods>;
    private ** parseMediaUrl(...);
    private ** getVideoDetail(...);
    private ** getMediaInfo(...);
    private ** bv2av(...);
    private ** resolveShortUrl(...);
}

# 保护 ViewModel（Compose 和 Activity 需要反射访问）
-keep class com.fam4k007.videoplayer.download.BilibiliDownloadViewModel {
    public <methods>;
}

# 保护 DownloadActivity（Compose 使用）
-keep class com.fam4k007.videoplayer.DownloadActivity { *; }
-keep class com.fam4k007.videoplayer.DownloadActivityKt { *; }

# 保护 CookieManager（SharedPreferences 键名不能混淆）
-keep class com.fam4k007.videoplayer.utils.CookieManager { *; }
-keepclassmembers class com.fam4k007.videoplayer.utils.CookieManager { *; }

# JSON 解析需要的类（org.json）
-keep class org.json.** { *; }
-keepclassmembers class org.json.** { *; }

# ============================================
# DanDanPlay API 相关规则（重要！）
# ============================================

# 保护 DanDanPlay 数据模型类（Gson 反序列化需要）
-keep class com.fam4k007.videoplayer.dandanplay.** { *; }
-keepclassmembers class com.fam4k007.videoplayer.dandanplay.** { *; }

# 特别保护数据类的字段和构造函数（防止被混淆）
-keepclassmembers class com.fam4k007.videoplayer.dandanplay.** {
    <init>(...);
    <fields>;
    public ** get*();
    public ** set*(...);
}

# 保护 @SerializedName 注解的字段
-keepclassmembers class com.fam4k007.videoplayer.dandanplay.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 防止 data class 的方法被移除
-keepclassmembers class com.fam4k007.videoplayer.dandanplay.** {
    public ** copy(...);
    public ** component*();
}

# JSON 解析需要的类（org.json）
-keep class org.json.** { *; }
-keepclassmembers class org.json.** { *; }

# 保护所有 sealed class 和它们的子类（防止反射和序列化问题）
-keep class com.fam4k007.videoplayer.bilibili.model.LoginResult { *; }
-keep class com.fam4k007.videoplayer.bilibili.model.LoginResult$** { *; }

# 保护 BiliBiliPlayActivity 和番剧播放 ViewModel 中的数据类（Gson 反序列化需要）
-keep class com.fam4k007.videoplayer.BiliBiliPlayActivity$** { *; }

# 保护 BiliBiliPlayViewModel 中定义的数据类（Gson 反序列化需要）
-keep class com.fam4k007.videoplayer.presentation.BiliPlayUiState { *; }
-keep class com.fam4k007.videoplayer.presentation.BiliPlayUiState$** { *; }
-keep class com.fam4k007.videoplayer.presentation.SimpleBangumiInfo { *; }
-keep class com.fam4k007.videoplayer.presentation.SimpleEpisode { *; }
-keep class com.fam4k007.videoplayer.presentation.BangumiDetailResponse { *; }
-keep class com.fam4k007.videoplayer.presentation.BangumiDetailResult { *; }
-keep class com.fam4k007.videoplayer.presentation.EpisodeItem { *; }

# 保护番剧索引相关的 sealed class（Gson 和 StateFlow 需要）
-keep class com.fam4k007.videoplayer.presentation.BangumiIndexUiState { *; }
-keep class com.fam4k007.videoplayer.presentation.BangumiIndexUiState$** { *; }
-keep class com.fam4k007.videoplayer.presentation.BangumiDetailUiState { *; }
-keep class com.fam4k007.videoplayer.presentation.BangumiDetailUiState$** { *; }
-keep class com.fam4k007.videoplayer.presentation.BangumiFilterState { *; }

# 保护 BiliBiliLoginActivity 中的 sealed class
-keep class com.fanchen.fam4k007.manager.compose.LoginUiState { *; }
-keep class com.fanchen.fam4k007.manager.compose.LoginUiState$** { *; }

# 保护弹幕下载管理器中的 sealed class
-keep class com.fam4k007.videoplayer.danmaku.BiliBiliDanmakuDownloadManager$DownloadResult { *; }
-keep class com.fam4k007.videoplayer.danmaku.BiliBiliDanmakuDownloadManager$DownloadResult$** { *; }

# 保护所有数据类的构造函数和字段（Gson 需要）
-keepclassmembers class com.fam4k007.videoplayer.bilibili.model.** {
    <init>(...);
    <fields>;
}

# 防止 data class 的 copy 方法被移除
-keepclassmembers class com.fam4k007.videoplayer.bilibili.model.** {
    public ** copy(...);
    public ** component*();
}

# ZXing 二维码
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Coil 图片加载
-keep class coil.** { *; }
-dontwarn coil.**

# ============================================
# Room Database
# ============================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.migration.Migration

# 保持调试信息（方便定位崩溃）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================
# 字幕下载功能相关规则（重要！Gson 反序列化需要）
# ============================================

# 保护字幕下载管理器及其内部私有数据类（SubtitleApiResponse 用于 Gson TypeToken 解析）
-keep class com.fam4k007.videoplayer.subtitle.** { *; }
-keepclassmembers class com.fam4k007.videoplayer.subtitle.** {
    <init>(...);
    <fields>;
    public ** get*();
    public ** component*();
    public ** copy(...);
}

# SubtitleDownloadManager 中的私有内部类也必须保护
-keep class com.fam4k007.videoplayer.subtitle.SubtitleDownloadManager { *; }

-keep class com.fam4k007.videoplayer.subtitle.SubtitleDownloadManager$* { *; }

# 保护 SubtitleSearchActivity 和 SubtitleSearchScreen
-keep class com.fam4k007.videoplayer.SubtitleSearchActivity { *; }
-keep class com.fam4k007.videoplayer.ui.screens.SubtitleSearchScreen { *; }
