package com.wenc.multitouchimageview

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isImageImmersive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val imageView = findViewById<MultiTouchImageView>(R.id.imageView)

        imageView.apply {
            setImageResource(R.mipmap.ic_launcher)

            zoomMaxTimes = 4
            zoomDampingFactor = 2
            scrollDampingFactor = 3

            onClickListener = object : MultiTouchImageView.OnClickListener {
                override fun oneClick() {
                    // 切换沉浸模式
                    isImageImmersive = !isImageImmersive
                    if (isImageImmersive) {
                        ObjectAnimator.ofInt(rootView, "backgroundColor", Color.WHITE, Color.BLACK).apply {
                            setEvaluator(ArgbEvaluator())
                            duration = 280
                            start()
                        }
                    } else {
                        ObjectAnimator.ofInt(rootView, "backgroundColor", Color.BLACK, Color.WHITE).apply {
                            setEvaluator(ArgbEvaluator())
                            duration = 280
                            start()
                        }
                    }
                }

                override fun doubleClick() {
                    // 进入沉浸模式
                    if (!isImageImmersive) {
                        ObjectAnimator.ofInt(rootView, "backgroundColor", Color.WHITE, Color.BLACK).apply {
                            setEvaluator(ArgbEvaluator())
                            duration = 280
                            start()
                        }
                        isImageImmersive = true
                    }
                }
            }

            onZoomEventListener = object : MultiTouchImageView.OnZoomEventListener {
                override fun onZoomStatusChange(event: MotionEvent, boolean: Boolean) {
                    // 进入沉浸模式
                    if (!isImageImmersive) {
                        ObjectAnimator.ofInt(rootView, "backgroundColor", Color.WHITE, Color.BLACK).apply {
                            setEvaluator(ArgbEvaluator())
                            duration = 280
                            start()
                        }
                        isImageImmersive = true
                    }
                }

                override fun onZooming(event: MotionEvent, oldDistance: Float, newDistance: Float) {}
            }
        }
    }
}