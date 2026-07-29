package com.fam4k007.videoplayer.domain.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import androidx.appcompat.app.AppCompatActivity
import `is`.xyz.mpv.MPVLib

/**
 * 画中画（PiP）辅助类
 * 参考 mpvEx 实现，利用 Android 原生 PiP 实现小窗播放
 * 同一 SurfaceView 在 PiP 模式下继续渲染，无需额外 Surface
 */
class PipHelper(
    private val activity: AppCompatActivity,
    private val mpvView: com.fam4k007.videoplayer.player.CustomMPVView
) {
    companion object {
        private const val TAG = "PipHelper"
        private const val PIP_INTENTS_FILTER = "com.fam4k007.PIP_ACTION"
        private const val PIP_INTENT_ACTION = "pip_action_code"
        private const val PIP_PLAY = 1
        private const val PIP_PAUSE = 2
        private const val PIP_REWIND = 3
        private const val PIP_FORWARD = 4
    }

    private var pipReceiver: BroadcastReceiver? = null

    fun onPictureInPictureModeChanged(isInPipMode: Boolean) {
        if (isInPipMode) {
            registerPipReceiver()
        } else {
            unregisterPipReceiver()
        }
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun registerPipReceiver() {
        pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.getIntExtra(PIP_INTENT_ACTION, 0) ?: return
                Log.d(TAG, "PiP action: $action")
                when (action) {
                    PIP_PLAY -> MPVLib.setPropertyBoolean("pause", false)
                    PIP_PAUSE -> MPVLib.setPropertyBoolean("pause", true)
                    PIP_REWIND -> MPVLib.command("seek", "-10", "relative+keyframes")
                    PIP_FORWARD -> MPVLib.command("seek", "10", "relative+keyframes")
                }
                updatePictureInPictureParams()
            }
        }

        val filter = IntentFilter(PIP_INTENTS_FILTER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(pipReceiver, filter)
        }
    }

    private fun unregisterPipReceiver() {
        pipReceiver?.let {
            runCatching { activity.unregisterReceiver(it) }
            pipReceiver = null
        }
    }

    fun updatePictureInPictureParams() {
        if (activity.isFinishing || activity.isDestroyed) return
        runCatching { activity.setPictureInPictureParams(buildPipParams()) }
    }

    private fun buildPipParams(): PictureInPictureParams =
        PictureInPictureParams.Builder().apply {
            getVideoAspectRatio()?.let { ratio ->
                setAspectRatio(ratio)
                setSourceRectHint(calculateSourceRect(ratio))
            }
            setActions(createPipActions())
        }.build()

    private fun getVideoAspectRatio(): Rational? {
        val width = MPVLib.getPropertyInt("video-out-params/dw") ?: 0
        val height = MPVLib.getPropertyInt("video-out-params/dh") ?: 0
        if (width == 0 || height == 0) return null
        return Rational(width, height).takeIf { it.toFloat() in 0.5f..2.39f }
    }

    private fun calculateSourceRect(aspectRatio: Rational): Rect {
        val viewWidth = mpvView.width.toFloat()
        val viewHeight = mpvView.height.toFloat()
        val videoAspect = aspectRatio.toFloat()
        val viewAspect = viewWidth / viewHeight

        return if (viewAspect < videoAspect) {
            val height = viewWidth / videoAspect
            val top = ((viewHeight - height) / 2).toInt()
            Rect(0, top, viewWidth.toInt(), (height + top).toInt())
        } else {
            val width = viewHeight * videoAspect
            val left = ((viewWidth - width) / 2).toInt()
            Rect(left, 0, (width + left).toInt(), viewHeight.toInt())
        }
    }

    private fun createPipActions(): List<RemoteAction> {
        val isPlaying = MPVLib.getPropertyBoolean("pause") == false
        return listOf(
            createRemoteAction("快退", android.R.drawable.ic_media_rew, PIP_REWIND),
            if (isPlaying)
                createRemoteAction("暂停", android.R.drawable.ic_media_pause, PIP_PAUSE)
            else
                createRemoteAction("播放", android.R.drawable.ic_media_play, PIP_PLAY),
            createRemoteAction("快进", android.R.drawable.ic_media_ff, PIP_FORWARD)
        )
    }

    private fun createRemoteAction(
        title: String,
        icon: Int,
        actionCode: Int
    ): RemoteAction {
        val intent = Intent(PIP_INTENTS_FILTER).apply {
            putExtra(PIP_INTENT_ACTION, actionCode)
            setPackage(activity.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            activity, actionCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return RemoteAction(Icon.createWithResource(activity, icon), title, title, pendingIntent)
    }

    fun enterPipMode() {
        runCatching {
            activity.enterPictureInPictureMode(buildPipParams())
        }.onFailure {
            Log.e(TAG, "Failed to enter PiP mode", it)
        }
    }

    fun destroy() {
        unregisterPipReceiver()
    }
}
