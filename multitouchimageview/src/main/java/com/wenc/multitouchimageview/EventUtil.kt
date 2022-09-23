package com.wenc.multitouchimageview

import android.view.MotionEvent
import kotlin.math.sqrt

object EventUtil {
    // 获取距离
    fun MotionEvent.getDistance(): Float {
        //获取两点间距离
        val x = getX(0) - getX(1)
        val y = getY(0) - getY(1)
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }

    // 获取中点
    fun MotionEvent.getMiddlePoint(): Float2 {
        val midX = (getX(1) + getX(0)) / 2
        val midY = (getY(1) + getY(0)) / 2
        return Float2(midX, midY)
    }

    // 获取位移量
    fun MotionEvent.getOffset(prePoint: Float2): Float2 {
        return Float2(x - prePoint.x, y - prePoint.y)
    }
}