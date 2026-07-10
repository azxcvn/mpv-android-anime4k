package com.fam4k007.videoplayer.manager

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import `is`.xyz.mpv.MPVLib
import com.fam4k007.videoplayer.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * 视频缩略图管理器
 * 使用 MPVLib.grabThumbnailFast 从 MPV 解码管线直接抓取帧，
 * 速度快、支持所有视频源（本地 / WebDAV / 网络流），
 * 配合时间桶分片 LruCache + 预取 + 多层并发控制。
 *
 * 并发策略（借鉴 mpvRx）：
 * - Semaphore(1) 限制同时只有一个提取在执行
 * - ConcurrentHashMap<Deferred> 防止同一桶重复发起
 * - 预取在空闲时进行，不阻塞当前请求
 */
class VideoThumbnailManager {
    companion object {
        private const val TAG = "VideoThumbnailManager"
        private const val THUMBNAIL_MAX_SIZE = 320
        private const val BUCKETS_PER_SECOND = 2f
        private const val PREFETCH_RADIUS = 5
        private const val CACHE_MAX_KB = 20 * 1024
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val thumbnailCache = object : LruCache<String, Bitmap>(CACHE_MAX_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    private val generationSemaphore = Semaphore(1)
    private val ongoingOperations = ConcurrentHashMap<String, Deferred<Bitmap?>>()

    private var currentSource: String? = null
    private var videoDurationSec: Float = 0f
    private var isInitialized = AtomicBoolean(false)
    private var preloadJob: Job? = null

    fun initializeVideo(uri: Uri, durationMs: Long, isWebDav: Boolean = false) {
        if (isInitialized.get() && currentSource != null) return
        thumbnailCache.evictAll()
        ongoingOperations.clear()
        currentSource = resolveSourcePath()
        videoDurationSec = durationMs / 1000f
        isInitialized.set(true)
        Logger.d(TAG, "初始化完成，source=$currentSource, duration=${videoDurationSec}s")
    }

    private fun resolveSourcePath(): String? =
        runCatching { MPVLib.getPropertyString("stream-open-filename") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { MPVLib.getPropertyString("path") }.getOrNull()
            ?.takeIf { it.isNotBlank() }

    fun getThumbnailAt(positionSec: Long): Bitmap? {
        val source = currentSource ?: return null
        return thumbnailCache.get(cacheKey(source, bucket(positionSec.toFloat())))
    }

    suspend fun extractThumbnailRealtime(positionSec: Long): Bitmap? {
        val source = currentSource ?: return null
        val b = bucket(positionSec.toFloat())
        val key = cacheKey(source, b)

        thumbnailCache.get(key)?.let { return it }
        findNearbyCached(source, b)?.let { return it }

        ongoingOperations[key]?.let { return it.await() }

        val deferred = scope.async {
            generationSemaphore.withPermit { loadThumbnail(source, b) }
        }
        ongoingOperations[key] = deferred
        return try {
            deferred.await()?.also { thumbnailCache.put(key, it) }
        } finally {
            ongoingOperations.remove(key)
        }
    }

    fun warmThumbnail(positionSec: Long) {
        scope.launch {
            generationSemaphore.withPermit {
                val source = currentSource ?: return@launch
                val b = bucket(positionSec.toFloat())
                val key = cacheKey(source, b)
                if (thumbnailCache.get(key) == null) {
                    loadThumbnail(source, b)?.let { thumbnailCache.put(key, it) }
                    Logger.d(TAG, "预热完成 @ ${positionSec}s")
                }
            }
        }
    }

    fun preloadAroundPosition(centerSec: Long) {
        preloadJob?.cancel()
        preloadJob = scope.launch {
            val source = currentSource ?: return@launch
            val centerBucket = bucket(centerSec.toFloat())
            val maxBucket = if (videoDurationSec > 0f) bucket(videoDurationSec) else Int.MAX_VALUE

            for (offset in 0..PREFETCH_RADIUS) {
                if (!isActive) return@launch
                listOf(centerBucket + offset, centerBucket - offset).forEach { b ->
                    if (b in 0..maxBucket) {
                        val key = cacheKey(source, b)
                        if (thumbnailCache.get(key) == null && !ongoingOperations.containsKey(key)) {
                            generationSemaphore.withPermit {
                                loadThumbnail(source, b)?.let { thumbnailCache.put(key, it) }
                            }
                        }
                    }
                }
                delay(2)
            }
        }
    }

    fun isThumbnailSupported(): Boolean = currentSource != null && videoDurationSec > 0f

    fun release() {
        preloadJob?.cancel()
        scope.cancel()
        ongoingOperations.clear()
        thumbnailCache.evictAll()
        currentSource = null
        videoDurationSec = 0f
        isInitialized.set(false)
        runCatching { MPVLib.clearThumbnailCache() }
        Logger.d(TAG, "已释放")
    }

    // ── 内部 ────────────────────────────────────────────

    private fun loadThumbnail(source: String, bucket: Int): Bitmap? {
        val timeSec = bucketTime(bucket)
        return runCatching {
            MPVLib.grabThumbnailFast(source, timeSec.toDouble(), THUMBNAIL_MAX_SIZE)
        }.onFailure { e ->
            Logger.w(TAG, "grabThumbnailFast 失败 @ ${timeSec}s: ${e.message}")
        }.getOrNull()?.also {
            Logger.d(TAG, "提取成功 @ ${timeSec}s (bucket=$bucket, size=${it.width}x${it.height})")
        }
    }

    private fun findNearbyCached(source: String, bucket: Int): Bitmap? {
        for (d in 1..PREFETCH_RADIUS) {
            thumbnailCache.get(cacheKey(source, bucket - d))?.let { return it }
            thumbnailCache.get(cacheKey(source, bucket + d))?.let { return it }
        }
        return null
    }

    private fun bucket(p: Float) = (p * BUCKETS_PER_SECOND).roundToInt().coerceAtLeast(0)
    private fun bucketTime(b: Int) = (b / BUCKETS_PER_SECOND).coerceAtLeast(0f)
    private fun cacheKey(s: String, b: Int) = "$s|$b|$THUMBNAIL_MAX_SIZE"
}
