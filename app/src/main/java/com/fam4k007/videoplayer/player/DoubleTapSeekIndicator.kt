package com.fam4k007.videoplayer.player

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.fam4k007.videoplayer.R

/**
 * 双击跳转指示器视图
 * 显示三角箭头动画和跳转秒数
 * 参考自 mpvKt 项目的实现
 */
class DoubleTapSeekIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val ICON_ANIMATION_DURATION = 750L
    }

    private var triangleContainer: LinearLayout
    private var tri1: ImageView
    private var tri2: ImageView
    private var tri3: ImageView
    private var secondsText: TextView

    var cycleDuration: Long = ICON_ANIMATION_DURATION
        set(value) {
            firstAnimator.duration = value / 5
            secondAnimator.duration = value / 5
            thirdAnimator.duration = value / 5
            fourthAnimator.duration = value / 5
            fifthAnimator.duration = value / 5
            field = value
        }

    var seconds: Int = 0
        set(value) {
            secondsText.text = if (value > 0) {
                "+${value}s"
            } else {
                "${value}s"
            }
            field = value
        }

    var isForward: Boolean = true
        set(value) {
            // 前进：箭头朝右（0度），后退：箭头朝左（180度）
            triangleContainer.rotation = if (value) 0f else 180f
            field = value
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.double_tap_seek_indicator, this, true)
        orientation = VERTICAL
        
        triangleContainer = findViewById(R.id.triangleContainer)
        tri1 = findViewById(R.id.tri1)
        tri2 = findViewById(R.id.tri2)
        tri3 = findViewById(R.id.tri3)
        secondsText = findViewById(R.id.doubleTapSeconds)
    }

    /**
     * 开始播放动画
     */
    fun start() {
        stop()
        firstAnimator.start()
    }

    /**
     * 停止动画
     */
    fun stop() {
        firstAnimator.cancel()
        secondAnimator.cancel()
        thirdAnimator.cancel()
        fourthAnimator.cancel()
        fifthAnimator.cancel()
        reset()
    }

    /**
     * 重置所有三角形透明度
     */
    private fun reset() {
        tri1.alpha = 0f
        tri2.alpha = 0f
        tri3.alpha = 0f
    }

    // 第一阶段：显示第一个三角形
    private val firstAnimator: ValueAnimator = CustomValueAnimator(
        start = {
            tri1.alpha = 0f
            tri2.alpha = 0f
            tri3.alpha = 0f
        },
        update = { tri1.alpha = it },
        end = { secondAnimator.start() }
    )

    // 第二阶段：显示第二个三角形
    private val secondAnimator: ValueAnimator = CustomValueAnimator(
        start = {
            tri1.alpha = 1f
            tri2.alpha = 0f
            tri3.alpha = 0f
        },
        update = { tri2.alpha = it },
        end = { thirdAnimator.start() }
    )

    // 第三阶段：显示第三个三角形，同时淡出第一个
    private val thirdAnimator: ValueAnimator = CustomValueAnimator(
        start = {
            tri1.alpha = 1f
            tri2.alpha = 1f
            tri3.alpha = 0f
        },
        update = {
            tri1.alpha = 1f - it
            tri3.alpha = it
        },
        end = { fourthAnimator.start() }
    )

    // 第四阶段：淡出第二个三角形
    private val fourthAnimator: ValueAnimator = CustomValueAnimator(
        start = {
            tri1.alpha = 0f
            tri2.alpha = 1f
            tri3.alpha = 1f
        },
        update = { tri2.alpha = 1f - it },
        end = { fifthAnimator.start() }
    )

    // 第五阶段：淡出第三个三角形，然后循环
    private val fifthAnimator: ValueAnimator = CustomValueAnimator(
        start = {
            tri1.alpha = 0f
            tri2.alpha = 0f
            tri3.alpha = 1f
        },
        update = { tri3.alpha = 1f - it },
        end = { firstAnimator.start() }
    )

    /**
     * 自定义 ValueAnimator，简化动画配置
     */
    private inner class CustomValueAnimator(
        val start: () -> Unit,
        val update: (Float) -> Unit,
        val end: () -> Unit
    ) : ValueAnimator() {
        private var isEnding = false

        init {
            duration = cycleDuration / 5
            setFloatValues(0f, 1f)

            addUpdateListener {
                update(it.animatedValue as Float)
            }

            addListener(object : AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    start()
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!isEnding) {
                        isEnding = true
                        try {
                            end()
                        } finally {
                            isEnding = false
                        }
                    }
                }

                override fun onAnimationCancel(animation: Animator) = Unit
                override fun onAnimationRepeat(animation: Animator) = Unit
            })
        }
    }
}
