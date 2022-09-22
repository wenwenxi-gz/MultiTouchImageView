package com.wenc.multitouchimageview

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sqrt


object ImageMatrixUtil {

    fun ImageView.fitCenter() {
        if (drawable == null) return

        val dWidth = drawable.intrinsicWidth
        val dHeight = drawable.intrinsicHeight

        val widthPercentage = width.toFloat() / dWidth.toFloat()
        val heightPercentage = height.toFloat() / dHeight.toFloat()
        val minPercentage = widthPercentage.coerceAtMost(heightPercentage)

        val targetWidth = (minPercentage * dWidth).roundToInt()
        val targetHeight = (minPercentage * dHeight).roundToInt()

        val matrix = Matrix()
        matrix.setScale(minPercentage, minPercentage)
        matrix.postTranslate((width - targetWidth) * 0.5f, (height - targetHeight) * 0.5f)

        scaleType = ImageView.ScaleType.MATRIX
        imageMatrix = matrix
    }

    fun ImageView.centerCrop() {
        if (drawable == null) return

        val dWidth = drawable.intrinsicWidth
        val dHeight = drawable.intrinsicHeight

        val widthPercentage = width.toFloat() / dWidth.toFloat()
        val heightPercentage = height.toFloat() / dHeight.toFloat()
        val maxPercentage = widthPercentage.coerceAtLeast(heightPercentage)

        val targetWidth = (maxPercentage * dWidth).roundToInt()
        val targetHeight = (maxPercentage * dHeight).roundToInt()

        val matrix = Matrix()
        matrix.setScale(maxPercentage, maxPercentage)
        matrix.postTranslate((width - targetWidth) * 0.5f, (height - targetHeight) * 0.5f)

        scaleType = ImageView.ScaleType.MATRIX
        imageMatrix = matrix
    }

    /**
     * @param xOffset x轴方向的偏移量
     * @param yOffset y轴方向的偏移量
     * 该方法用于滚动图片，根据image和view的大小，进行不同的滚动。
     * 当image的宽小于view的宽，只进行y轴的滚动。
     * 当image的高小于view的高，只进行x轴的滚动。
     * 同时为了适配viewPager2的横向滑动切换页面，需要做一下特别判断：
     * 1、需要判断x轴方向image是否滚出view边界，以放弃滚动，通知viewPager2进行切换。
     * 2、仅进行y轴滚动时，需要判断x轴偏移量与y轴偏移量的比值，以在适当时候放弃y轴滚动，通知viewPager2进行切换。
     *
     * @return [Boolean] 值为false时滚动失败。[Boolean] 值为true时滚动成功。
     */
    fun ImageView.scroll(xOffset: Float, yOffset: Float): Boolean {
        if (drawable == null) return false
        // 图片左上角顶点的坐标
        val position = imageMatrix.getTranslate()
        // 图片的缩放程度
        val scale = imageMatrix.getScale()
        // 原图宽高
        val dWidth = drawable.intrinsicWidth
        val dHeight = drawable.intrinsicHeight
        // 视图宽高
        val vWidth = width
        val vHeight = height
        // 图片宽高
        val iWidth = dWidth * scale.x
        val iHeight = dHeight * scale.y

        val matrix = Matrix(imageMatrix)
        // 如果左右两边离开边界太多，禁止滚动
        if (position.x > 20f || position.x + dWidth * scale.x < vWidth - 20f) return false
        // 如果图片宽高都和屏幕一样，禁止滚动
        if (iWidth <= vWidth && iHeight <= vHeight) return false
        // 如果图片宽高都大于view的宽高，x、y轴都进行滚动
        if (iWidth > vWidth && iHeight > vHeight) matrix.postTranslate(xOffset, yOffset)
        // 如果图片宽大于view的宽，x轴进行滚动
        if (iWidth > vWidth && iHeight <= vHeight) matrix.postTranslate(xOffset, 0f)
        // 如果图片高大于view的高，y轴进行滚动
        if (iWidth <= vWidth && iHeight > vHeight) {
            // 如果横向滑动更明显，则禁止滚动
            if (xOffset.absoluteValue > 3 * yOffset.absoluteValue) return false
            matrix.postTranslate(0f, yOffset)
        }
        imageMatrix = matrix
        return true
    }

    /**
     * @param scale 缩放的倍数
     * @param middle 缩放时围绕的点
     *
     *  先围绕[middle]点进行[scale]倍数的矩阵变化
     *  拿到变化后的矩阵，判断位置是否需要进行调整
     *  需要调整的情况：
     *  1、放大时，如果图片宽高小于视图宽高时，要保持图片水平居中或者垂直居中。
     *  2、缩小时，如果图片宽高大于视图宽高，要保持图片左右边界不进入视图的左右边界，或图片上下边界不进入视图的上下边界。
     */
    fun ImageView.zoom(scale: Float, middle: Float2) {
        if (drawable == null) return

        // 原图宽高
        val dWidth = drawable.intrinsicWidth
        val dHeight = drawable.intrinsicHeight
        // 视图宽高
        val vWidth = width.toFloat()
        val vHeight = height.toFloat()

        // 取出imageView的矩阵进行缩放
        val matrix = Matrix(imageMatrix).also {
            it.postScale(scale, scale, middle.x, middle.y)
            val position = it.getTranslate()
            val mScale = it.getScale()

            val imageWidth = dWidth * mScale.x
            val imageHeight = dHeight * mScale.y

            // 当图片宽小于view宽时居中。否则贴边
            val xOffset = if (imageWidth <= width) getSkew(position.x, imageWidth, vWidth)
            else getOverflow(position.x, imageWidth, vWidth)
            // 当图片高小于view宽时居中。否则贴边
            val yOffset = if (imageHeight <= height) getSkew(position.y, imageHeight, vHeight)
            else getOverflow(position.y, imageHeight, vHeight)
            // 调整位置
            it.postTranslate(xOffset, yOffset)
        }

        // 给imageView矩阵赋值
        imageMatrix = matrix
    }

    /**
     * 缩放结束后触发，当image小于view时，执行动画恢复至fitCenter
     */
    fun ImageView.fixSizeAfterZoom() {
        if (drawable == null) return

        // 图片的缩放程度
        val scale = imageMatrix.getScale()
        // 原图宽高
        val dWidth = drawable.intrinsicWidth
        val dHeight = drawable.intrinsicHeight
        // 视图宽高
        val vWidth = width.toFloat()
        val vHeight = height.toFloat()
        // 图片宽高
        val iWidth = dWidth * scale.x
        val iHeight = dHeight * scale.y

        // 如果图片宽高都小于view的宽高，则恢复大小
        if (iWidth < vWidth && iHeight < vHeight) {
            val widthPercentage = vWidth / dWidth.toFloat()
            val heightPercentage = vHeight / dHeight.toFloat()
            val minPercentage = widthPercentage.coerceAtMost(heightPercentage)
            // 临时矩阵
            val tempMatrix = Matrix()
            ValueAnimator.ofFloat(scale.x, minPercentage).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    tempMatrix.setScale(animatedValue as Float, animatedValue as Float)
                    // 获取偏差
                    val skew = getPositionSkew(tempMatrix)
                    // 调整位置
                    tempMatrix.postTranslate(skew.x, skew.y)
                    imageMatrix = tempMatrix
                }
                start()
            }
        }
    }

    /**
     * 滚动结束后执行，当image边界离开view边界时，执行动画恢复image移动至view边界处。
     * @return [Boolean] 为true时执行了移动。为false代表不需要执行恢复，后续需要进行[slide]滑动。
     */
    fun ImageView.fixBoundaryAfterScroll(): Boolean {
        if (drawable == null) return false
        val matrix = Matrix(imageMatrix)
        // 溢出的位置
        val overflow = getPositionOverflow(imageMatrix)
        // 没有溢出则不需要执行
        if (overflow.x == 0f && overflow.y == 0f) return false

        ValueAnimator.ofObject(Float2Evaluator, Float2(), overflow).apply {
            duration = 350
            addUpdateListener {
                val tempMatrix = Matrix(matrix)
                tempMatrix.postTranslate((animatedValue as Float2).x, (animatedValue as Float2).y)
                imageMatrix = tempMatrix
            }
            interpolator = AccelerateInterpolator()
            start()
        }
        return true
    }

    private fun ImageView.getPositionOverflow(matrix: Matrix): Float2 {
        if (drawable == null) return Float2()
        val position = matrix.getTranslate()
        val scale = matrix.getScale()

        // 原图宽高
        val dWidth = drawable.intrinsicWidth
        val dHeight = drawable.intrinsicHeight
        // 视图宽高
        val vWidth = width.toFloat()
        val vHeight = height.toFloat()
        // 图片宽高
        val iWidth = dWidth * scale.x
        val iHeight = dHeight * scale.y
        // 计算溢出值
        val xOverflow = if (iWidth >= vWidth) getOverflow(position.x, iWidth, vWidth) else 0f
        val yOverflow = if (iHeight >= vHeight) getOverflow(position.y, iHeight, vHeight) else 0f
        return Float2(xOverflow, yOverflow)
    }

    /**
     * @param position 点位置（x轴或y轴）
     * @param image image的宽或高
     * @param view view的宽或高
     * 用于计算image的边界脱离view的边界的距离，
     * 一般当image大于view时使用。
     * @return [Float] 为溢出值，image边界距离view边界的距离。
     */
    private fun getOverflow(position: Float, image: Float, view: Float): Float {
        return if (position > 0f) {
            -position
        } else if (position + image < view) {
            view - (position + image)
        } else 0f
    }

    private fun ImageView.getPositionSkew(matrix: Matrix): Float2 {
        if (drawable == null) return Float2()
        val position = matrix.getTranslate()
        val scale = matrix.getScale()

        val vWidth = width.toFloat()
        val vHeight = height.toFloat()

        val imageWidth = drawable.intrinsicWidth * scale.x
        val imageHeight = drawable.intrinsicHeight * scale.y

        val xSkew = if (imageWidth <= vWidth) getSkew(position.x, imageWidth, vWidth)
        else 0f

        val ySkew = if (imageHeight <= vHeight) getSkew(position.y, imageHeight, vHeight)
        else 0f

        return Float2(xSkew, ySkew)
    }

    /**
     * @param position 点位置（x轴或y轴）
     * @param image image的宽或高
     * @param view view的宽或高
     * 用于计算image当前位置与image相对view居中时的距离，
     * 一般当image小于view时使用。
     * @return [Float] 为偏离值，image偏离居中位置的距离。
     */
    private fun getSkew(position: Float, image: Float, view: Float): Float {
        return (view - image) / 2.0f - position
    }

    /**
     * @param velocity 滑动的速度
     * 根据[velocity]进行滑动，包含了x方向和y方向。
     * 执行动画对矩阵进行平移转换，[velocity]为转换的初始值，并且逐渐衰减。
     * 需要对image和view的大小做判断：
     * 1、image的宽高都大于view的宽高时正常进行滑动
     * 2、仅当image的宽大于view的宽时，仅x轴进行滑动
     * 3、仅当image的高大于view的高时，仅y轴进行滑动
     */
    fun ImageView.slide(velocity: Float2) {
        if (drawable == null) return

        // 图片的缩放程度
        val scale = imageMatrix.getScale()
        // 原图宽高
        val dWidth = drawable.intrinsicWidth
        val dHeight = drawable.intrinsicHeight
        // 视图宽高
        val vWidth = width
        val vHeight = height
        // 图片宽高
        val iWidth = dWidth * scale.x
        val iHeight = dHeight * scale.y

        // 如果图片宽高都和view一样，禁止滑动
        if (iWidth <= vWidth && iHeight <= vHeight) return
        // 如果图片宽高都大于view，则x、y轴都进行滑动
        if (iWidth > vWidth && iHeight > vHeight) slideStart(velocity.x, velocity.y)
        // 如果图片宽大于view的宽，则x轴进行滑动
        if (iWidth > vWidth && iHeight <= vHeight) slideStart(velocity.x, 0f)
        // 如果图片高大于view的高，则y轴进行滑动
        if (iWidth <= vWidth && iHeight > vHeight) slideStart(0f, velocity.y)
    }

    /**
     * @param xVelocity x轴的平移初始值
     * @param yVelocity y轴的平移初始值
     * 开始平移的动画
     */
    private fun ImageView.slideStart(xVelocity: Float, yVelocity: Float) {
        ValueAnimator.ofObject(Float2Evaluator, Float2(xVelocity, yVelocity), Float2()).apply {
            duration = 600
            addUpdateListener {
                sliding(animatedValue as Float2)
            }
            start()
        }
    }

    /**
     * @param offset 动画时，每次平移的量
     * 该值包含x方向和y方向
     * 在动画中，进行offset大小的平移转换后，为了保证image的边界不离开view的边界，
     * 通过[getPositionOverflow]获取溢出值，再进行一个转换调整。
     */
    private fun ImageView.sliding(offset: Float2) {
        if (drawable == null) return
        val tempMatrix = Matrix(imageMatrix).also {
            it.postTranslate(offset.x, offset.y)
            val overflow = getPositionOverflow(it)
            it.postTranslate(overflow.x, overflow.y)
        }
        imageMatrix = tempMatrix
    }

    /**
     * 恢复图片到fitCenter状态
     */
    fun ImageView.resume() {
        if (drawable == null) return

        // 图片左上角顶点的坐标
        val position = imageMatrix.getTranslate()
        // 图片的缩放程度
        val scale = imageMatrix.getScale()
        // 原图宽高
        val dWidth = drawable.intrinsicWidth.toFloat()
        val dHeight = drawable.intrinsicHeight.toFloat()
        // 视图宽高
        val vWidth = width.toFloat()
        val vHeight = height.toFloat()
        // 图片宽高
        val iWidth = dWidth * scale.x
        val iHeight = dHeight * scale.y

        val widthPercentage = vWidth / dWidth
        val heightPercentage = vHeight / dHeight
        val minPercentage = widthPercentage.coerceAtMost(heightPercentage)
        // 获取图片当前矩阵
        val matrix = Matrix(imageMatrix)
        // 大小偏差
        val times = minPercentage / scale.x

        val x = if (vWidth == iWidth) 0f else position.x / (vWidth - iWidth)
        val y = if (vHeight == iHeight) 0f else position.y / (vHeight - iHeight)
        // 缩放所围绕的点
        val point = Float2(width * x, height * y)

        ValueAnimator.ofFloat(1f, times).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val tempMatrix = Matrix(matrix)
                tempMatrix.postScale(animatedValue as Float, animatedValue as Float, point.x, point.y)
                // 获取偏离值
                val skew = getPositionSkew(tempMatrix)
                tempMatrix.postTranslate(skew.x, skew.y)
                imageMatrix = tempMatrix
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    matrix.setScale(minPercentage, minPercentage)
                    // 获取偏离值
                    val skew = getPositionSkew(matrix)
                    // 调整位置
                    matrix.postTranslate(skew.x, skew.y)
                    imageMatrix = matrix
                }

                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationRepeat(animation: Animator?) {}
            })
            start()
        }
    }

    /**
     * 双击时触发
     * 图片较小时会进行放大至centerCrop状态
     * 图片较大时会进行缩小至fitCenter状态
     */
    fun ImageView.doubleClick(clickPos: Float2) {
        if (drawable == null) {
            return
        }
        val dWidth = drawable.intrinsicWidth
        val dHeight = drawable.intrinsicHeight

        val widthPercentage = width.toFloat() / dWidth.toFloat()
        val heightPercentage = height.toFloat() / dHeight.toFloat()

        val minPercentage = widthPercentage.coerceAtMost(heightPercentage)
        val maxPercentage = widthPercentage.coerceAtLeast(heightPercentage)

        val scale = imageMatrix.getScale()
        val position = imageMatrix.getTranslate()

        val matrix = Matrix(imageMatrix)
        val scalePos = clickPos.copy()

        if (scale.x >= maxPercentage) {

            val times = minPercentage / scale.x

            val x = if (width.toFloat() == dWidth * scale.x) 0f else position.x / (width - dWidth * scale.x)
            val y = if (height.toFloat() == dHeight * scale.y) 0f else position.y / (height - dHeight * scale.y)

            val point = Float2(width * x, height * y)

            ValueAnimator.ofFloat(1f, times).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val tempMatrix = Matrix(matrix)
                    tempMatrix.postScale(animatedValue as Float, animatedValue as Float, point.x, point.y)
                    // 获取偏离值
                    val skew = getPositionSkew(tempMatrix)
                    // 调整位置
                    tempMatrix.postTranslate(skew.x, skew.y)
                    imageMatrix = tempMatrix
                }
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator?) {}
                    override fun onAnimationEnd(animation: Animator?) {
                        matrix.setScale(minPercentage, minPercentage)
                        // 获取偏离值
                        val skew = getPositionSkew(matrix)
                        // 调整位置
                        matrix.postTranslate(skew.x, skew.y)
                        imageMatrix = matrix
                    }

                    override fun onAnimationCancel(animation: Animator?) {}
                    override fun onAnimationRepeat(animation: Animator?) {}
                })
                start()
            }

        } else if (scale.x >= minPercentage) {

            val times = maxPercentage / scale.x
            ValueAnimator.ofFloat(1f, times).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val tempMatrix = Matrix(matrix)
                    tempMatrix.postScale(animatedValue as Float, animatedValue as Float, scalePos.x, scalePos.y)
                    // 获取偏离值
                    val skew = getPositionSkew(tempMatrix)
                    // 调整位置
                    tempMatrix.postTranslate(skew.x, skew.y)
                    imageMatrix = tempMatrix
                }
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator?) {}
                    override fun onAnimationEnd(animation: Animator?) {
                        // 获取当前平移位置
                        val translate = imageMatrix.getTranslate()
                        // 设置准确的大小和位置
                        matrix.setScale(maxPercentage, maxPercentage)
                        matrix.postTranslate(translate.x, translate.y)
                        imageMatrix = matrix
                    }

                    override fun onAnimationCancel(animation: Animator?) {}
                    override fun onAnimationRepeat(animation: Animator?) {}
                })
                start()
            }
        }
    }

    private fun Matrix.getTranslate(): Float2 {
        val floatArray = FloatArray(9)
        this.getValues(floatArray)
        return Float2(floatArray[2], floatArray[5])
    }

    private fun Matrix.getScale(): Float2 {
        val floatArray = FloatArray(9)
        this.getValues(floatArray)
        return Float2(floatArray[0], floatArray[4])
    }

    //获取距离
    fun getDistance(event: MotionEvent): Float {
        //获取两点间距离
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }

    fun getMiddlePoint(event: MotionEvent): Float2 {
        val midX = (event.getX(1) + event.getX(0)) / 2
        val midY = (event.getY(1) + event.getY(0)) / 2
        return Float2(midX, midY)
    }
}