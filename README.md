# MultiTouchImageView
## 功能介绍

- 主要功能：能够进行缩放和滑动图片。

- 特点：
  - 适配viewPager2（横向翻页），滑动时判断边界，在合适的时机才会触发viewPager2的翻页。
  - 提供单击和双击事件接口，单击会恢复图片大小，双击放大或缩小图片。
  - 图片恢复大小时的过渡动画，双击放大缩小图片的过渡动画。

## 下载

使用gradle。

```groovy
repositories {
	...
	maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.wenwenxi-gz:MultiTouchImageView:Tag'
}
```

使用maven。

```xml
<repositories>
	<repository>
	    <id>jitpack.io</id>
	    <url>https://jitpack.io</url>
	</repository>
</repositories>
<dependency>
	<groupId>com.github.wenwenxi-gz</groupId>
	<artifactId>MultiTouchImageView</artifactId>
	<version>Tag</version>
</dependency>
```

## 效果展示

### 双指缩放

两只手指放上屏幕即可进行缩放效果。

![zoom](readmeRes/zoom.gif)

### 单指滚动

图片放大以后，可以进行滚动查看，快速滚动松开手指，会自动滑动一段距离。

![scrollandslide](readmeRes/scrollandslide.gif)

### 单击和双击

单击会让图片恢复到居中位置。快速双击会让图片进行放大或缩小。

## 简单使用？

### 第一步

将布局文件中的imageView替换成MultiTouchImageView。

例子：父布局是ConstraintLayout。

```xml
<com.wenc.multitouchimageview.MultiTouchImageView
    android:id="@+id/imageView"
    android:layout_width="0dp"
    android:layout_height="0dp"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent"
    tools:srcCompat="@tools:sample/avatars" />
```

### 第二步

获取该对象，并且设置本地图片。

```kotlin
val imageView = findViewById<MultiTouchImageView>(R.id.imageView)
imageView.setImageResource(R.mipmap.ic_launcher)
```

设置图片后变可以进行缩放和滑动功能。

## 进阶使用：

### 进入沉浸式

```kotlin
imageView.apply {
    observe(viewLifecycleOwner)

    onClickListener = object : MultiTouchImageView.OnClickListener {
        override fun oneClick() {
            // 切换沉浸模式：改变背景色，或隐藏状态栏等操作
            
        }

        override fun doubleClick() {
            // 进入沉浸模式：改变背景色，或隐藏状态栏等操作
            
        }
    }

    onZoomEventListener = object : MultiTouchImageView.OnZoomEventListener {
        override fun onZoomStatusChange(event: MotionEvent, boolean: Boolean) {
            // 进入沉浸模式：改变背景色，或隐藏状态栏等操作
            
        }

        override fun onZooming(event: MotionEvent, oldDistance: Float, newDistance: Float) {}
    }
}
```

## 感谢

本库由于个人项目需要而实现，并作此分享，如有不兼容或者效果不够完善的地方，请谅解。希望大家提出意见，我会不断进行修正优化，谢谢。
