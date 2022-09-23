package com.wenc.multitouchimageview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.doOnLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.wenc.multitouchimageview.EventUtil.getDistance
import com.wenc.multitouchimageview.EventUtil.getMiddlePoint
import com.wenc.multitouchimageview.EventUtil.getOffset
import com.wenc.multitouchimageview.ImageProcessor.centerCrop
import com.wenc.multitouchimageview.ImageProcessor.fitCenter
import com.wenc.multitouchimageview.ImageProcessor.fixBoundary
import com.wenc.multitouchimageview.ImageProcessor.fixSize
import com.wenc.multitouchimageview.ImageProcessor.resumeCenterCrop
import com.wenc.multitouchimageview.ImageProcessor.resumeFitCenter
import com.wenc.multitouchimageview.ImageProcessor.scroll
import com.wenc.multitouchimageview.ImageProcessor.slide
import com.wenc.multitouchimageview.ImageProcessor.switchSize
import com.wenc.multitouchimageview.ImageProcessor.zoom
import kotlin.math.absoluteValue

/**
 * @author 温文曦
 *
 * 继承ImageView，对其TouchEvent进行改写。
 * 图片的ScaleType会自动设置成Matrix。
 * 提供了双指缩放，单指滑动，双击放大，单击初恢复的交互功能。
 *
 */
class MultiTouchImageView : AppCompatImageView, DefaultLifecycleObserver {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    /**
     * 该变量为控制缩放时的最大缩放值的倍数
     * 该值乘以 [maxPercentage] 为最终的最大缩放量
     *
     * @date 2022.09.23
     */
    var zoomMaxTimes: Int = 2
        set(value) {
            field = when {
                value < 1 -> 1
                value > 10 -> 10
                else -> value
            }
        }

    /**
     * 该变量为缩放图片时超出最大缩放值后的阻尼因子
     * 当缩放量超出最大缩放量时，根据超出值与最大值的比值，得出相应的比例，保证超出范围越大，则阻尼越大。
     * 该值可以控制阻尼大小。
     *
     * @date 2022.09.23
     */
    var zoomDampingFactor: Int = 2
        set(value) {
            field = if (value < 1) field else value
        }

    /**
     * 该变量为滚动图片时超出视图边界后后的阻尼因子
     * 当图片边界超出边界时，根据超出值（溢出值）与视图的比值，得出相应的比例，保证溢出范围越大，则阻尼越大。
     * 该值可以控制阻尼大小。
     *
     * @date 2022.09.23
     */
    var scrollDampingFactor: Int = 10
        set(value) {
            field = if (value < 1) field else value
        }

    /**
     * 该值为视图与图片的宽度比，和高度比之间更大的比值
     * 一般当centerCrop时，将matrix的scale设置到该值
     * 即将图片的短边设置到于视图相等的scale值。
     *
     * @date 2022.09.23
     */
    var maxPercentage = 0f

    /**
     * 该值为视图与图片的宽度比，和高度比之间更小的比值
     * 一般当fitCenter时，将matrix的scale设置到该值
     * 即将图片的长边设置到于视图相等的scale值。
     *
     * @date 2022.09.23
     */
    var minPercentage = 0f

    /**
     * drawable的实际大小。
     *
     * @date 2022.09.23
     */
    var dWidth = 0f
    var dHeight = 0f

    /**
     * view的实际大小。
     *
     * @date 2022.09.23
     */
    var vWidth = 0f
    var vHeight = 0f

    /**
     * 原始的ScaleType
     * 只要在第一次调用[setScaleType]时赋值
     * 该值用于判断图片如何显示。
     * 目前仅支持 fitCenter 和 centerCrop
     * @sample [android.widget.ImageView.ScaleType]
     *
     * @date 2022.09.23
     */
    private var initialScaleType: ScaleType? = null

    /**
     * 该变量代表用户是否正在进行缩放，当开始缩放或者停止缩放，该变量都会改变。
     */
    private var isZooming = false

    /**
     * 该变量代表用户是否正在进行滑动，当开始滑动或者停止滑动，该变量都会改变。
     */
    private var isScrolling = false

    /**
     * 该变量代表滑动的完成，用户触发TouchEvent时，
     * Event.Action值为Down的时候会设为false，
     * 当进行触发了滑动，会设为true，说明用户进行了滑动事件。
     */
    private var isScrollFinished = false

    /**
     * 该变量记录前一次Event双指的距离。
     */
    private var preDistance = 0f

    /**
     * 该变量记录点击的次数。Down时会+1，触发MOVE或者消费了点击事件，会置0
     */
    private var clickCount = 0

    /**
     * 该变量记录前一次Event点击的位置。
     */
    private val prePos = Float2()

    /**
     * 该变量记录本次Event双指的中点位置。
     */
    private val midPoint = Float2()

    /**
     * 该变量记录本次scroll完成时的move产生的位移差，
     * 用作自动滑动的参数。
     */
    private val velocity = Float2()

    /**
     * 处理点击事件
     * Down下后会delay一个点击事件，执行时会根据click的次数，执行不同的方法
     */
    private val clickHandler = Handler(Looper.getMainLooper())

    /**
     * 单击或双击事件的监听。
     */
    var onClickListener: OnClickListener? = null

    /**
     * 缩放事件的监听。
     */
    var onZoomEventListener: OnZoomEventListener? = null

    /**
     * 滑动事件的监听。
     */
    var onScrollEventListener: OnScrollEventListener? = null

    /**
     * 点击事件延时事件，单位：毫秒
     * 反应的是双击事件中两次点击的时间间隔，值限制在0-1000毫秒之间
     * 当[clickEventDelay]为0 时，双击时间将无效
     *
     * @date 2022.09.23
     */
    var clickEventDelay = 250L
        set(value) {
            if (value < 0) return
            if (value > 1000) return
            field = value
        }

    /**
     * 在touchEvent中，当event的action为[MotionEvent.ACTION_UP]时，判断[clickCount]是否不为0而执行。
     * 该runnable会delay[clickEventDelay]毫秒再执行。
     * 执行时可根据当前的[clickCount]值判断时单击还是双击
     *
     * @date 2022.09.23
     */
    private val actionClickRunnable = Runnable {
        // 判断是否消费点击事件
        if (clickCount != 0) {
            // 单击
            if (clickCount == 1) {
                onClickListener?.oneClick()
                resumeBySingleClick()
            }
            // 双击
            if (clickCount == 2) {
                // 进入沉浸模式
                onClickListener?.doubleClick()
                zoomByDoubleClick(prePos)
            }
            // 消费完成
            clickCount = 0
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.action and MotionEvent.ACTION_MASK) {

            MotionEvent.ACTION_DOWN -> {
                // 禁止父View拦截
                parent.requestDisallowInterceptTouchEvent(true)
                // 点击次数增加
                if (clickCount < 2) clickCount++
                // 单指滑动开始
                isScrolling = true
                onScrollEventListener?.onScrollStatusChange(event, isScrolling)
                // 滑动未完成
                isScrollFinished = false
                // 记录上一点位置
                recordPreviousPosition(event)
            }

            MotionEvent.ACTION_MOVE -> {
                // 判断是否正在滑动
                if (isScrolling) scrolling(event)
                // 判断是否正在缩放
                if (isZooming) zooming(event)
                // 记录位置
                recordPreviousPosition(event)
            }

            MotionEvent.ACTION_UP -> {
                // 判断是否滑动完成
                if (isScrollFinished) {
                    // 恢复或滑动
                    fixOrSlideAfterScroll()
                }
                // 滑动结束
                isScrolling = false
                // 调用接口
                onScrollEventListener?.onScrollStatusChange(event, isScrolling)

                // 允许父View拦截
                parent.requestDisallowInterceptTouchEvent(false)

                // 如果点击次数没有被消费则开启线程处理点击事件
                if (clickCount != 0) {
                    // 防止重复启动线程
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (clickHandler.hasCallbacks(actionClickRunnable)) {
                            return true
                        }
                    }
                    // 线程执行
                    clickHandler.postDelayed(actionClickRunnable, clickEventDelay)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 记录历史距离
                recordPreviousDistance(event)
                // 误触判断
                if (preDistance < 10.0f) return false
                // 消费点击事件
                clickCount = 0
                // 停止单指滑动
                isScrolling = false
                onScrollEventListener?.onScrollStatusChange(event, isScrolling)
                // 开始双指缩放
                isZooming = true
                onZoomEventListener?.onZoomStatusChange(event, isZooming)
                // 允许父View拦截
                parent.requestDisallowInterceptTouchEvent(true)
                // 记录中点位置
                recordMiddlePoint(event)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 结束双指缩放
                isZooming = false
                onZoomEventListener?.onZoomStatusChange(event, isZooming)
                // 缩放结束后若图片过小则自动恢复
                fixSizeAfterZoom()
            }
        }
        return true
    }

    interface OnClickListener {
        fun oneClick()
        fun doubleClick()
    }

    interface OnZoomEventListener {
        fun onZoomStatusChange(event: MotionEvent, boolean: Boolean)
        fun onZooming(event: MotionEvent, oldDistance: Float, newDistance: Float)
    }

    interface OnScrollEventListener {
        fun onScrollStatusChange(event: MotionEvent, boolean: Boolean)
        fun onScrolling(event: MotionEvent, oldPos: Float2, newPos: Float2)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        doOnLayout {
            captureDrawable()
            transform()
        }
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        doOnLayout {
            captureDrawable()
            transform()
        }
    }

    override fun setScaleType(scaleType: ScaleType?) {
        super.setScaleType(scaleType)
        if (initialScaleType == null) {
            initialScaleType = scaleType
        }
    }

    fun observe(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(this)
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        transform()
    }

    /**
     * 捕获drawable的相关信息
     * 用于初始化[dWidth]、[dHeight]、[vWidth]、[vHeight]
     * 最主要是得到[maxPercentage]和[minPercentage]
     * 在[transform]执行之前得到以上的值，才能正常显示图片。
     *
     * @date 2022.09.23
     */
    private fun captureDrawable() {
        if (this.drawable != null) {
            // 原图宽高
            dWidth = this.drawable.intrinsicWidth.toFloat()
            dHeight = this.drawable.intrinsicHeight.toFloat()
            // 视图宽高
            vWidth = width.toFloat()
            vHeight = height.toFloat()
            // 图片和视图比例
            val widthPercentage = width.toFloat() / this.drawable.intrinsicWidth.toFloat()
            val heightPercentage = height.toFloat() / this.drawable.intrinsicHeight.toFloat()
            maxPercentage = widthPercentage.coerceAtLeast(heightPercentage)
            minPercentage = widthPercentage.coerceAtMost(heightPercentage)
        }
    }

    /**
     * 根据[initialScaleType]值，对图片进行对应的矩阵变化，显示正确的图片效果。
     * [initialScaleType]在第一次[setScaleType]时得到值。
     *
     * @date 2022.09.23
     */
    private fun transform() {
        when (initialScaleType) {
            ScaleType.FIT_CENTER -> fitCenter()
            ScaleType.CENTER_CROP -> centerCrop()
            else -> {}
        }
    }

    /**
     * 在[isZooming] 为 true时触发
     * 计算当前两指的距离，与上一次两指的距离的比值，得出所需要放大的倍数。
     * 通过[zoom]方法进行缩放。
     *
     * @date 2022.09.23
     */
    private fun zooming(event: MotionEvent) {
        val currDistance = event.getDistance()
        onZoomEventListener?.onZooming(event, preDistance, currDistance)
        // 获取放大倍数
        val times = currDistance / preDistance
        // 缩放图片
        zoom(times, midPoint, zoomMaxTimes, zoomDampingFactor)
        // 记录历史距离
        preDistance = currDistance
    }

    /**
     * 在[isScrolling] 为 true时触发
     * 计算当前手指的位置和上一次手指的位置的位移量
     * 通过[scroll]方法进行滚动。如果滚动返回false，说明无法再进行滚动，通知父view可拦截点击事件。
     * scroll结束后调用[recordVelocity]记录[velocity]，用于[slide]
     *
     * @date 2022.09.23
     */
    private fun scrolling(event: MotionEvent) {
        // 记录位移量
        val offset = event.getOffset(prePos)
        // 当本次的点和上次的点一致时，不触发。（华为手机在点击时经常同时触发move，但位置没有改变，因此需要加此判断。）
        if (offset.x.absoluteValue < 0.03f && offset.y.absoluteValue < 0.03f) return
        // 消费点击事件
        clickCount = 0
        // 正在滑动
        onScrollEventListener?.onScrolling(event, prePos, Float2(event.x, event.y))
        // 无法滚动则允许父View拦截事件
        if (!scroll(offset, scrollDampingFactor)) {
            parent.requestDisallowInterceptTouchEvent(false)
        }
        // 记录速度
        recordVelocity(offset)
        // 滑动完成了
        isScrollFinished = true
    }

    /**
     * 用于scroll完成
     * 恢复图片到正常边界位置，或者进行自动滑动
     *
     * @date 2022.09.23
     */
    private fun fixOrSlideAfterScroll() {
        // 拖动结束后若图片离开边界，则回到边界
        if (!fixBoundary()) {
            // 没有离开边界则根据速度滑动
            slide(velocity)
        }
        isScrollFinished = false
    }

    /**
     * 用于zoom完成
     * 恢复图片的大小。
     *
     * @date 2022.09.23
     */
    private fun fixSizeAfterZoom() {
        fixSize(zoomMaxTimes)
    }

    /**
     * 单击击时触发
     * 根据[initialScaleType]的值，将图片恢复到对应的显示位置。
     *
     * @date 2022.09.23
     */
    private fun resumeBySingleClick() {
        when (initialScaleType) {
            ScaleType.FIT_CENTER -> resumeFitCenter()
            ScaleType.CENTER_CROP -> resumeCenterCrop()
            ScaleType.MATRIX -> {}
            else -> {}
        }
    }

    /**
     * 双击击时触发
     * 将图片放大或缩小。
     *
     * @date 2022.09.23
     */
    private fun zoomByDoubleClick(prePosition: Float2) {
        val copyPosition = prePosition.copy()
        switchSize(copyPosition)
    }

    // 记录上一个位置
    private fun recordPreviousPosition(event: MotionEvent) {
        prePos.x = event.x
        prePos.y = event.y
    }

    // 记录重点位置
    private fun recordMiddlePoint(event: MotionEvent) {
        val middlePoint = event.getMiddlePoint()
        midPoint.x = middlePoint.x
        midPoint.y = middlePoint.y
    }

    // 记录速度
    private fun recordVelocity(offset: Float2) {
        velocity.x = offset.x
        velocity.y = offset.y
    }

    // 记录上一次距离
    private fun recordPreviousDistance(event: MotionEvent) {
        preDistance = event.getDistance()
    }
}