package com.wenc.multitouchimageview

import android.animation.TypeEvaluator

object Float2Evaluator : TypeEvaluator<Float2> {
    override fun evaluate(fraction: Float, startValue: Float2, endValue: Float2): Float2 {
        val offset = endValue - startValue
        return startValue + offset.times(fraction)
    }
}