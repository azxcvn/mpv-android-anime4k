package com.fam4k007.videoplayer.bilibili.danmaku

import com.fam4k007.videoplayer.bilibili.auth.BiliBiliAuthManager
import com.fam4k007.videoplayer.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Bilibili 原生弹幕加载器
 * 使用 gRPC DmSegMobile API 分段获取弹幕，输出 DanmakuPlayerView 兼容的 XML 格式。
 *
 * PiliPlus 参考: DmGrpc.dmSegMobile(cid, segmentIndex)
 */
object BilibiliDanmakuLoader {

    private const val TAG = "BiliDanmakuLoader"
    private const val GRPC_HOST = "grpc.biliapi.net"
    private const val DM_SEG_MOBILE_PATH = "/bilibili.community.service.dm.v1.DM/DmSegMobile"
    private const val SEGMENT_MS = 6 * 60 * 1000 // 每段 6 分钟

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 弹幕数据（单段结果） */
    data class DanmakuSegment(
        val segmentIndex: Int,
        val xmlContent: String,  // 可直接喂给 DanmakuPlayerView 的 XML
        val isClosed: Boolean,   // state == 1 表示该视频弹幕已关闭
    )

    /**
     * 获取指定分段的弹幕
     * @param cid B站视频 cid
     * @param segmentIndex 分段索引（0-based，每段6分钟）
     * @param cookieString 登录 Cookie
     */
    suspend fun fetchSegment(cid: Long, segmentIndex: Int, cookieString: String): DanmakuSegment? =
        withContext(Dispatchers.IO) {
            try {
                // Bilibili segment_index 是 1-based
                val reqBody = buildDmSegMobileReq(cid, segmentIndex + 1)
                val grpcBody = wrapGrpcFrame(reqBody)

                val request = Request.Builder()
                    .url("https://$GRPC_HOST$DM_SEG_MOBILE_PATH")
                    .post(grpcBody.toRequestBody("application/grpc".toMediaType()))
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Referer", "https://www.bilibili.com")
                    .apply {
                        if (cookieString.isNotEmpty()) addHeader("Cookie", cookieString)
                    }
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Logger.w(TAG, "DmSegMobile HTTP ${response.code} for cid=$cid seg=$segmentIndex")
                    return@withContext null
                }

                val respBytes = response.body?.bytes() ?: return@withContext null
                // 跳过 gRPC 5字节帧头
                if (respBytes.size <= 5) return@withContext null
                val protoBytes = respBytes.copyOfRange(5, respBytes.size)

                val parsed = parseDmSegMobileReply(protoBytes)
                if (parsed == null) {
                    Logger.w(TAG, "Failed to parse DmSegMobileReply for cid=$cid seg=$segmentIndex")
                    return@withContext null
                }

                val xml = danmakuElemsToXml(parsed.elems)
                Logger.d(TAG, "Fetched ${parsed.elems.size} danmaku for cid=$cid seg=$segmentIndex")
                DanmakuSegment(segmentIndex, xml, parsed.state == 1)
            } catch (e: Exception) {
                Logger.w(TAG, "fetchSegment error for cid=$cid seg=$segmentIndex: ${e.message}")
                null
            }
        }

    /**
     * 计算指定播放进度（毫秒）所在的分段索引
     */
    fun calcSegmentIndex(positionMs: Long): Int = (positionMs / SEGMENT_MS).toInt()

    // ==================== Protobuf 序列化 ====================

    /** 构建 DmSegMobileReq 的 protobuf 字节 */
    private fun buildDmSegMobileReq(oid: Long, segmentIndex: Int): ByteArray {
        val buf = ByteArrayOutputStream()
        // field 2: oid (int64, wire type 0 = varint)
        writeVarint(buf, (2 shl 3) or 0)
        writeVarint(buf, oid)
        // field 3: type = 1 (int32, wire type 0 = varint)
        writeVarint(buf, (3 shl 3) or 0)
        writeVarint(buf, 1)
        // field 4: segment_index (int64, wire type 0 = varint)
        writeVarint(buf, (4 shl 3) or 0)
        writeVarint(buf, segmentIndex.toLong())
        return buf.toByteArray()
    }

    /** gRPC 帧包装: 1字节压缩标志(0) + 4字节大端长度 */
    private fun wrapGrpcFrame(payload: ByteArray): ByteArray {
        val frame = ByteArray(5 + payload.size)
        frame[0] = 0 // no compression
        frame[1] = ((payload.size shr 24) and 0xFF).toByte()
        frame[2] = ((payload.size shr 16) and 0xFF).toByte()
        frame[3] = ((payload.size shr 8) and 0xFF).toByte()
        frame[4] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, frame, 5, payload.size)
        return frame
    }

    // ==================== Protobuf 反序列化 ====================

    data class ParsedReply(val elems: List<ParsedElem>, val state: Int)

    data class ParsedElem(
        val id: Long, val progress: Int, val mode: Int, val fontSize: Int,
        val color: Long, val midHash: String, val content: String,
        val ctime: Long, val weight: Int, val pool: Int, val idStr: String,
    )

    /** 手动解析 DmSegMobileReply protobuf */
    private fun parseDmSegMobileReply(data: ByteArray): ParsedReply? {
        return try {
            val elems = mutableListOf<ParsedElem>()
            var state = 0
            var pos = 0

            while (pos < data.size) {
                val tag = readVarint(data, pos)
                pos = tag.second
                val fieldNum = (tag.first ushr 3).toInt()
                val wireType = (tag.first and 0x07).toInt()

                when {
                    fieldNum == 1 && wireType == 2 -> {
                        // repeated DanmakuElem (length-delimited)
                        val len = readVarint(data, pos)
                        pos = len.second
                        val elemEnd = pos + len.first.toInt()
                        val elem = parseDanmakuElem(data, pos, elemEnd)
                        if (elem != null) elems.add(elem)
                        pos = elemEnd
                    }
                    fieldNum == 2 && wireType == 0 -> {
                        state = readVarint(data, pos).first.toInt()
                        pos = readVarint(data, pos).second
                    }
                    wireType == 0 -> pos = readVarint(data, pos).second  // skip unknown varint
                    wireType == 2 -> {
                        val len = readVarint(data, pos)
                        pos = len.second + len.first.toInt()  // skip unknown length-delimited
                    }
                    else -> break  // wire type 5 (32-bit) etc, stop
                }
            }
            ParsedReply(elems, state)
        } catch (e: Exception) {
            Logger.w("StreamProxy", "parseDmSegMobileReply error: ${e.message}")
            null
        }
    }

    /** 解析单个 DanmakuElem */
    private fun parseDanmakuElem(data: ByteArray, start: Int, end: Int): ParsedElem? {
        var id = 0L; var progress = 0; var mode = 0; var fontSize = 0
        var color = 16777215L; var midHash = ""; var content = ""
        var ctime = 0L; var weight = 0; var pool = 0; var idStr = ""
        var pos = start

        while (pos < end) {
            val tag = readVarint(data, pos)
            pos = tag.second
            val fieldNum = (tag.first ushr 3).toInt()
            val wireType = (tag.first and 0x07).toInt()

            when {
                fieldNum == 1 && wireType == 0 -> { id = readVarint(data, pos).first; pos = readVarint(data, pos).second }
                fieldNum == 2 && wireType == 0 -> { progress = readVarint(data, pos).first.toInt(); pos = readVarint(data, pos).second }
                fieldNum == 3 && wireType == 0 -> { mode = readVarint(data, pos).first.toInt(); pos = readVarint(data, pos).second }
                fieldNum == 4 && wireType == 0 -> { fontSize = readVarint(data, pos).first.toInt(); pos = readVarint(data, pos).second }
                fieldNum == 5 && wireType == 0 -> { color = readVarint(data, pos).first; pos = readVarint(data, pos).second }
                fieldNum == 6 && wireType == 2 -> { val r = readString(data, pos); midHash = r.first; pos = r.second }
                fieldNum == 7 && wireType == 2 -> { val r = readString(data, pos); content = r.first; pos = r.second }
                fieldNum == 8 && wireType == 0 -> { ctime = readVarint(data, pos).first; pos = readVarint(data, pos).second }
                fieldNum == 9 && wireType == 0 -> { weight = readVarint(data, pos).first.toInt(); pos = readVarint(data, pos).second }
                fieldNum == 11 && wireType == 0 -> { pool = readVarint(data, pos).first.toInt(); pos = readVarint(data, pos).second }
                fieldNum == 12 && wireType == 2 -> { val r = readString(data, pos); idStr = r.first; pos = r.second }
                wireType == 0 -> pos = readVarint(data, pos).second
                wireType == 2 -> { val len = readVarint(data, pos); pos = len.second + len.first.toInt() }
                else -> break
            }
        }
        if (content.isEmpty()) return null
        return ParsedElem(id, progress, mode, fontSize, color, midHash, content, ctime, weight, pool, idStr)
    }

    // ==================== XML 转换 ====================

    /** 将弹幕列表转为 Bilibili XML 格式（DanmakuPlayerView 兼容） */
    fun danmakuElemsToXml(elems: List<ParsedElem>): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<i>")
        for (e in elems) {
            // d 标签属性格式: time,mode,fontsize,color,ctime,pool,midHash,id,weight
            val time = String.format("%.3f", e.progress / 1000.0)
            val attr = "$time,${e.mode},${e.fontSize},${e.color},${e.ctime},${e.pool},${e.midHash},${e.id},${e.weight}"
            appendLine("<d p=\"$attr\">${e.content.escapeXml()}</d>")
        }
        appendLine("</i>")
    }

    private fun String.escapeXml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    // ==================== Varint / 工具 ====================

    private fun writeVarint(buf: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v ushr 7 != 0L) {
            buf.write((v.toInt() and 0x7F) or 0x80)
            v = v ushr 7
        }
        buf.write(v.toInt() and 0x7F)
    }

    /** 返回 (value, newPosition) */
    private fun readVarint(data: ByteArray, pos: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var p = pos
        while (p < data.size) {
            val b = data[p++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result to p
    }

    /** 读取 length-delimited string */
    private fun readString(data: ByteArray, pos: Int): Pair<String, Int> {
        val len = readVarint(data, pos)
        val strStart = len.second
        val strEnd = strStart + len.first.toInt()
        val str = String(data, strStart, len.first.toInt(), Charsets.UTF_8)
        return str to strEnd
    }
}
