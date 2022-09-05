# MultiTouchImageView
能够缩放和滑动图片。
能够加载网络图片。
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

### 设置网络图片

能够设置网络图片，并且可以先加载缩略图，再加载原图。原图未加载完成时，会使用缩略图进行占位。

```kotlin
val imageView = findViewById<MultiTouchImageView>(R.id.imageView)
imageView.setWebImage(glideUrlThumbnail, glideUrl)
```

也可以使用本地图片作缩略图未加载完成时的占位。

```kotlin
val imageView = findViewById<MultiTouchImageView>(R.id.imageView)
imageView.setWebImage(resourceId, glideUrlThumbnail, glideUrl)
```

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
