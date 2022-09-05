package com.wenc.multitouchimageview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.wenc.multitouchimageview.ImageMatrixUtil.centerHorizontal
import com.wenc.multitouchimageview.ImageMatrixUtil.centerVertical
import com.wenc.multitouchimageview.ImageMatrixUtil.doubleClick
import com.wenc.multitouchimageview.ImageMatrixUtil.fitCenter
import com.wenc.multitouchimageview.ImageMatrixUtil.fixBoundaryAfterScroll
import com.wenc.multitouchimageview.ImageMatrixUtil.fixBoundaryAfterZoom
import com.wenc.multitouchimageview.ImageMatrixUtil.fixSizeAfterZoom
import com.wenc.multitouchimageview.ImageMatrixUtil.imageIsHigher
import com.wenc.multitouchimageview.ImageMatrixUtil.imageIsWider
import com.wenc.multitouchimageview.ImageMatrixUtil.reachBoundary
import com.wenc.multitouchimageview.ImageMatrixUtil.resume
import com.wenc.multitouchimageview.ImageMatrixUtil.scroll
import com.wenc.multitouchimageview.ImageMatrixUtil.slide
import com.wenc.multitouchimageview.ImageMatrixUtil.zoom

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
     * 该变量代表用户是否正在进行缩放，当开始缩放或者停止缩放，该变量都会改变。
     */
    private var isZooming = false

    /**
     * 该变量代表用户是否正在进行滑动，当开始滑动或者停止滑动，该变量都会改变。
     */
    private var isScrolling = false

    /**
     * 该变量代表点击的完成，用户触发TouchEvent时，
     * Event.Action值为Down的时候会设为false，Up的时候会设为ture，代表完成点击。
     */
    private var isClickFinished = false

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
    private var midPoint = Float2()

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

    private val actionDownRunnable = Runnable {

        // 判断是否消费点击事件
        if (clickCount != 0 && isClickFinished) {
            // 单击
            if (clickCount == 1) {
                if (!isScrolling) {
                    onClickListener?.oneClick()
                    resume()
                }
            }
            // 双击
            if (clickCount == 2) {
                // 进入沉浸模式
                onClickListener?.doubleClick()
                doubleClick(prePos)
            }
        }
        // 消费完成
        clickCount = 0
        isClickFinished = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.action and MotionEvent.ACTION_MASK) {

            MotionEvent.ACTION_DOWN -> {
                // 通知viewPager2禁用滑动功能
                parent.requestDisallowInterceptTouchEvent(true)
                // 点击未完成
                isClickFinished = false
                // 点击次数
                clickCount++
                // 正在滑动
                isScrolling = true
                onScrollEventListener?.onScrollStatusChange(event, isScrolling)
                // 滑动未完成
                isScrollFinished = false
                // 记录上一点位置
                prePos.x = event.x
                prePos.y = event.y
                // 防止重复启动线程
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (clickHandler.hasCallbacks(actionDownRunnable)) {
                        return true
                    }
                }
                // 线程执行
                clickHandler.postDelayed(actionDownRunnable, 320)
            }

            MotionEvent.ACTION_MOVE -> {
                // 消费点击事件
                clickCount = 0
                // 判断是否正在缩放
                if (isZooming) {
                    val newDistance = ImageMatrixUtil.getDistance(event)
                    onZoomEventListener?.onZooming(event, preDistance, newDistance)
                    // 获取放大倍数
                    val scale = newDistance / preDistance
                    // 缩放图片
                    zoom(scale, midPoint)
                    // 保持垂直居中
                    centerVertical()
                    // 保持水平居中
                    centerHorizontal()
                    // 记录历史距离
                    preDistance = newDistance
                }
                // 判断是否正在滑动
                if (isScrolling) {
                    onScrollEventListener?.onScrolling(event, prePos, Float2(event.x, event.y))
                    val xOffset = event.x - prePos.x
                    val yOffset = event.y - prePos.y
                    // 判断图片宽高是否同时大于view的宽高
                    if (imageIsWider() && imageIsHigher()) {
                        // 改变位置
                        scroll(xOffset, yOffset)
                        // 到达边界
                        if (reachBoundary()) {
                            // 通知viewPager2恢复滑动功能
                            parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    // 判断图片宽是否大于view的宽
                    if (imageIsWider() && !imageIsHigher()) {
                        // 只改变x方向位置
                        scroll(xOffset, 0f)
                        // 到达边界
                        if (reachBoundary()) {
                            // 通知viewPager2恢复滑动功能
                            parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    // 判断图片高是否大于view的高（特殊处理）
                    if (imageIsHigher() && !imageIsWider()) {
                        if (xOffset.absoluteValue > 3 * yOffset.absoluteValue) {
                            // 通知viewPager2恢复滑动功能
                            parent.requestDisallowInterceptTouchEvent(false)
                        } else {
                            // 只改变y方向位置
                            scroll(0f, yOffset)
                        }
                    }
                    // 如果图片宽高都和屏幕一样
                    if (!imageIsWider() && !imageIsHigher()) {
                        // 通知viewPager2恢复滑动功能
                        parent.requestDisallowInterceptTouchEvent(false)
                    }
                    velocity.x = xOffset
                    velocity.y = yOffset

                    // 滑动完成了
                    isScrollFinished = true
                }
                // 记录位置
                prePos.x = event.x
                prePos.y = event.y
            }

            MotionEvent.ACTION_UP -> {
                // 判断是否滑动完成
                if (isScrollFinished) {
                    // 拖动结束后若图片离开边界，则回到边界
                    if (!fixBoundaryAfterScroll()) {
                        // 判断图片宽高是否同时大于view的宽高
                        if (imageIsWider() && imageIsHigher()) {
                            slide(velocity)
                        }
                        // 判断图片宽是否大于view的宽
                        if (imageIsWider() && !imageIsHigher()) {
                            slide(Float2(x = velocity.x))
                        }
                        // 判断图片高是否大于view的高（特殊处理）
                        if (imageIsHigher() && !imageIsWider()) {
                            slide(Float2(y = velocity.y))
                        }
                    }
                    isScrollFinished = false
                }
                // 滑动结束
                isScrolling = false
                onScrollEventListener?.onScrollStatusChange(event, isScrolling)
                // 完成点击
                isClickFinished = true
                // 通知viewPager2恢复滑动功能
                parent.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 记录历史距离
                preDistance = ImageMatrixUtil.getDistance(event)
                if (preDistance > 15f) {
                    // 停止滑动
                    isScrolling = false
                    onScrollEventListener?.onScrollStatusChange(event, isScrolling)
                    // 开始缩放
                    isZooming = true
                    onZoomEventListener?.onZoomStatusChange(event, isZooming)
                    // 通知viewPager2禁用滑动功能
                    parent.requestDisallowInterceptTouchEvent(true)
                    // 记录中点位置
                    midPoint = ImageMatrixUtil.getMiddlePoint(event)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 结束缩放
                isZooming = false
                onZoomEventListener?.onZoomStatusChange(event, isZooming)
                // 缩放结束后若图片过小则自动恢复
                fixSizeAfterZoom()
                // 缩放结束后若图片离开边界，则回到边界
                fixBoundaryAfterZoom()
                // 通知viewPager2恢复滑动功能
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }

        return true
    }

    fun setWebImage(thumbnailUrl: String, originalImageUrl: String) {
        val thumbnailGlideUrl = GlideUrl(thumbnailUrl, LazyHeaders.Builder().build())
        val originalImageGlideUrl = GlideUrl(originalImageUrl, LazyHeaders.Builder().build())
        setWebImage(thumbnailGlideUrl, originalImageGlideUrl)
    }

    fun setWebImage(thumbnailUrl: GlideUrl, originalImageUrl: GlideUrl) {
        setWebImage(null, thumbnailUrl, originalImageUrl)
    }

    /**
     * @param placeholder 本地的resource ID，作占位图。
     * @param thumbnailUrl 远端的缩略图地址。
     * @param originalImageUrl 远端的原图地址。
     *
     * 先使用本地资源作占位图，随即加载缩略图，缩略图拿到后再开始拿原图，同时缩略图用作占位图。
     */
    fun setWebImage(placeholder: Int, thumbnailUrl: GlideUrl, originalImageUrl: GlideUrl) {
        Glide.with(this).asDrawable().load(thumbnailUrl).placeholder(placeholder)
            .into(object : CustomTarget<Drawable>() {

                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {

                    Glide.with(context).load(originalImageUrl).placeholder(resource)
                        .into(object : ImageViewTarget<Drawable>(this@MultiTouchImageView) {

                            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                                super.onResourceReady(resource, transition)

                                setImageDrawable(resource)
                            }

                            override fun setResource(resource: Drawable?) {}
                        })
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    /**
     * @param placeholder 本地的drawable，作占位图。
     * @param thumbnailUrl 远端的缩略图地址。
     * @param originalImageUrl 远端的原图地址。
     *
     * 先使用本地drawable作占位图，随即加载缩略图，缩略图拿到后再开始拿原图，同时缩略图用作占位图。
     */
    fun setWebImage(placeholder: Drawable?, thumbnailUrl: GlideUrl, originalImageUrl: GlideUrl) {
        Glide.with(this).asDrawable().load(thumbnailUrl).placeholder(placeholder)
            .into(object : CustomTarget<Drawable>() {

                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {

                    Glide.with(context).load(originalImageUrl).placeholder(resource)
                        .into(object : ImageViewTarget<Drawable>(this@MultiTouchImageView) {

                            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                                super.onResourceReady(resource, transition)

                                setImageDrawable(resource)
                            }

                            override fun setResource(resource: Drawable?) {}
                        })
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
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
            fitCenter()
        }
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        doOnLayout {
            fitCenter()
        }
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        doOnLayout {
            fitCenter()
        }
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        doOnLayout {
            fitCenter()
        }
    }

    fun observe(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(this)
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        fitCenter()
    }
}