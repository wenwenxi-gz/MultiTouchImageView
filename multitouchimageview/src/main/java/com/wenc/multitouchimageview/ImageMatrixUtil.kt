package com.wenc.multitouchimageview

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
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

    fun ImageView.fixSizeAfterZoom() {
        if (drawable == null) return

        val dWidth = drawable.intrinsicWidth
        val dHeight = drawable.intrinsicHeight

        val scale = imageMatrix.getScale()

        if (dWidth * scale.x < width && dHeight * scale.y < height) {

            val widthPercentage = width.toFloat() / dWidth.toFloat()
            val heightPercentage = height.toFloat() / dHeight.toFloat()
            val minPercentage = widthPercentage.coerceAtMost(heightPercentage)

            val tempMatrix = Matrix()

            ValueAnimator.ofFloat(scale.x, minPercentage).apply {
                duration = 300

                addUpdateListener {

                    tempMatrix.setScale(animatedValue as Float, animatedValue as Float)
                    imageMatrix = tempMatrix
                    centerHorizontal()
                    centerVertical()
                }

                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

    fun ImageView.scroll(xOffset: Float, yOffset: Float) {
        val matrix = Matrix(imageMatrix)
        matrix.postTranslate(xOffset, yOffset)
        imageMatrix = matrix
    }

    fun ImageView.imageIsWider(): Boolean {
        val scale = imageMatrix.getScale()
        return drawable.intrinsicWidth * scale.x > width
    }

    fun ImageView.imageIsHigher(): Boolean {
        val scale = imageMatrix.getScale()
        return drawable.intrinsicHeight * scale.y > height
    }

    fun ImageView.zoom(scale: Float, middle: Float2) {
        // 取出imageView的矩阵切进行缩放
        val matrix = Matrix(imageMatrix).also {
            it.postScale(scale, scale, middle.x, middle.y)
        }
        // 给imageView矩阵赋值
        imageMatrix = matrix
    }

    fun ImageView.fixBoundaryAfterZoom() {
        if (drawable == null) return

        val matrix = Matrix(imageMatrix)
        val position = imageMatrix.getTranslate()
        val scale = imageMatrix.getScale()

        val imageWidth = drawable.intrinsicWidth * scale.x
        val imageHeight = drawable.intrinsicHeight * scale.y

        if (imageHeight < height && imageWidth < width) return

        val targetX = if (imageWidth >= width) {
            if (position.x > 0f) {
                -position.x
            } else if (position.x + imageWidth < width) {
                width - (position.x + imageWidth)
            } else 0f
        } else 0f

        val targetY = if (imageHeight >= height) {
            if (position.y > 0f) {
                -position.y
            } else if (position.y + imageHeight < height) {
                height - (position.y + imageHeight)
            } else 0f
        } else 0f

        matrix.postTranslate(targetX, targetY)

        imageMatrix = matrix
    }

    fun ImageView.fixBoundaryAfterScroll(): Boolean {
        if (drawable == null) return false

        val matrix = Matrix(imageMatrix)
        val position = imageMatrix.getTranslate()
        val scale = imageMatrix.getScale()

        val imageWidth = drawable.intrinsicWidth * scale.x
        val imageHeight = drawable.intrinsicHeight * scale.y

        val targetX = if (imageWidth >= width) {
            if (position.x > 0f) {
                -position.x
            } else if (position.x + imageWidth < width) {
                width - (position.x + imageWidth)
            } else 0f
        } else 0f

        val targetY = if (imageHeight >= height) {
            if (position.y > 0f) {
                -position.y
            } else if (position.y + imageHeight < height) {
                height - (position.y + imageHeight)
            } else 0f
        } else 0f

        if (targetX == 0f && targetY == 0f) return false

        ValueAnimator.ofObject(Float2Evaluator, Float2(), Float2(targetX, targetY)).apply {
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

    fun ImageView.slide(velocity: Float2) {
        ValueAnimator.ofObject(Float2Evaluator, velocity, Float2()).apply {
            duration = 600
            addUpdateListener {
                val tempMatrix = Matrix(imageMatrix)
                tempMatrix.postTranslate((animatedValue as Float2).x, (animatedValue as Float2).y)

                val scale = tempMatrix.getScale()
                val position = tempMatrix.getTranslate()

                val imageWidth = drawable.intrinsicWidth * scale.x
                val imageHeight = drawable.intrinsicHeight * scale.y

                val targetX = if (imageWidth >= width) {
                    if (position.x > 0f) {
                        -position.x
                    } else if (position.x + imageWidth < width) {
                        width - (position.x + imageWidth)
                    } else 0f
                } else 0f

                val targetY = if (imageHeight >= height) {
                    if (position.y > 0f) {
                        -position.y
                    } else if (position.y + imageHeight < height) {
                        height - (position.y + imageHeight)
                    } else 0f
                } else 0f

                tempMatrix.postTranslate(targetX, targetY)

                imageMatrix = tempMatrix
            }
            start()
        }
    }

    fun ImageView.centerHorizontal() {
        if (drawable == null) return

        val matrix = Matrix(imageMatrix)
        val position = imageMatrix.getTranslate()
        val scale = imageMatrix.getScale()

        val imageWidth = drawable.intrinsicWidth * scale.x

        if (imageWidth > width) return

        val targetX = (width - imageWidth) / 2f

        matrix.postTranslate(targetX - position.x, 0f)

        imageMatrix = matrix
    }

    fun ImageView.centerVertical() {
        if (drawable == null) return

        val matrix = Matrix(imageMatrix)
        val position = imageMatrix.getTranslate()
        val scale = imageMatrix.getScale()

        val imageHeight = drawable.intrinsicHeight * scale.y

        if (imageHeight > height) return

        val targetY = (height - imageHeight) / 2f

        matrix.postTranslate(0f, targetY - position.y)

        imageMatrix = matrix
    }

    fun ImageView.reachBoundary(): Boolean {
        if (drawable == null) return false

        val position = imageMatrix.getTranslate()
        val scale = imageMatrix.getScale()

        if (position.x > 20f) return true

        if (position.x + drawable.intrinsicWidth * scale.x < width - 20f) return true

        return false
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

    fun ImageView.resume() {
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

        if (scale.x >= maxPercentage) {

            val times = minPercentage / scale.x

            val x = if (width.toFloat() == dWidth * scale.x) 0f else position.x / (width - dWidth * scale.x)
            val y = if (height.toFloat() == dHeight * scale.y) 0f else position.y / (height - dHeight * scale.y)

            val point = Float2(width * x, height * y)

            ValueAnimator.ofFloat(1f, times).apply {
                duration = 300

                addUpdateListener {
                    val tempMatrix = Matrix(matrix)
                    tempMatrix.postScale(animatedValue as Float, animatedValue as Float, point.x, point.y)
                    imageMatrix = tempMatrix
                    centerHorizontal()
                    centerVertical()
                }

                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator?) {

                    }

                    override fun onAnimationEnd(animation: Animator?) {
                        val targetWidth = minPercentage * dWidth
                        val targetHeight = minPercentage * dHeight

                        matrix.setScale(minPercentage, minPercentage)
                        matrix.postTranslate((width - targetWidth) * 0.5f, (height - targetHeight) * 0.5f)

                        imageMatrix = matrix
                    }

                    override fun onAnimationCancel(animation: Animator?) {

                    }

                    override fun onAnimationRepeat(animation: Animator?) {

                    }
                })

                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

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

                addUpdateListener {
                    val tempMatrix = Matrix(matrix)
                    tempMatrix.postScale(animatedValue as Float, animatedValue as Float, point.x, point.y)
                    imageMatrix = tempMatrix
                    centerHorizontal()
                    centerVertical()
                }

                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator?) {

                    }

                    override fun onAnimationEnd(animation: Animator?) {
                        val targetWidth = minPercentage * dWidth
                        val targetHeight = minPercentage * dHeight

                        matrix.setScale(minPercentage, minPercentage)
                        matrix.postTranslate((width - targetWidth) * 0.5f, (height - targetHeight) * 0.5f)

                        imageMatrix = matrix
                    }

                    override fun onAnimationCancel(animation: Animator?) {

                    }

                    override fun onAnimationRepeat(animation: Animator?) {

                    }
                })

                interpolator = DecelerateInterpolator()
                start()
            }

        } else if (scale.x >= minPercentage) {

            val times = maxPercentage / scale.x
            ValueAnimator.ofFloat(1f, times).apply {
                duration = 300

                addUpdateListener {
                    val tempMatrix = Matrix(matrix)
                    tempMatrix.postScale(animatedValue as Float, animatedValue as Float, scalePos.x, scalePos.y)
                    imageMatrix = tempMatrix
                    centerHorizontal()
                    centerVertical()
                }

                interpolator = DecelerateInterpolator()
                start()
            }
        }
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