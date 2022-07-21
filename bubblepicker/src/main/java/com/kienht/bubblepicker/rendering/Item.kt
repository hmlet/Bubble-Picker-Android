package com.kienht.bubblepicker.rendering

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.opengl.GLES20.*
import android.opengl.Matrix
import android.text.DynamicLayout
import android.text.Layout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import com.kienht.bubblepicker.model.BubbleGradient
import com.kienht.bubblepicker.model.PickerItem
import com.kienht.bubblepicker.physics.CircleBody
import com.kienht.bubblepicker.rendering.BubbleShader.U_MATRIX
import com.kienht.bubblepicker.resizeBitmap
import com.kienht.bubblepicker.toTexture
import com.nostra13.universalimageloader.cache.memory.impl.WeakMemoryCache
import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration
import com.nostra13.universalimageloader.core.assist.ImageScaleType
import com.nostra13.universalimageloader.core.assist.ImageSize
import org.jbox2d.common.Vec2
import java.lang.ref.WeakReference

/**
 * Created by irinagalata on 1/19/17.
 */
data class Item(val context: WeakReference<Context>,
                val pickerItem: PickerItem, val circleBody: CircleBody, val isAlwaysSelected: Boolean) {


    val x: Float
        get() = circleBody.physicalBody.position.x

    val y: Float
        get() = circleBody.physicalBody.position.y

    val radius: Float
        get() = circleBody.radius

    val initialPosition: Vec2
        get() = circleBody.position

    val currentPosition: Vec2
        get() = circleBody.physicalBody.position

    private var isVisible = true
        get() = circleBody.isVisible

    private var texture: Int = 0

    private lateinit var textures: IntArray

    private var index: Int = 0

    private var imageTexture: Int = 0

    private lateinit var textLayout: DynamicLayout

    private lateinit var canvas: Canvas

    private val currentTexture: Int
        get() = if (circleBody.increased || circleBody.isIncreasing) imageTexture else texture

    private val bitmapSize = 256f

    private val gradient: LinearGradient?
        get() {
            return pickerItem.gradient?.let {
                val horizontal = it.direction == BubbleGradient.HORIZONTAL
                LinearGradient(if (horizontal) 0f else bitmapSize / 2f,
                        if (horizontal) bitmapSize / 2f else 0f,
                        if (horizontal) bitmapSize else bitmapSize / 2f,
                        if (horizontal) bitmapSize / 2f else bitmapSize,
                        it.startColor, it.endColor, Shader.TileMode.CLAMP)
            }
        }

    fun drawItself(programId: Int, index: Int, scaleX: Float, scaleY: Float) {
        glActiveTexture(GL_TEXTURE)
        glBindTexture(GL_TEXTURE_2D, currentTexture)
        glUniform1i(glGetUniformLocation(programId, BubbleShader.U_TEXT), 0)
        glUniform1i(glGetUniformLocation(programId, BubbleShader.U_VISIBILITY), if (isVisible) 1 else -1)
        glUniformMatrix4fv(glGetUniformLocation(programId, U_MATRIX), 1, false, calculateMatrix(scaleX, scaleY), 0)
        glDrawArrays(GL_TRIANGLE_STRIP, index * 4, 4)
        //drawText(canvas)
        //Log.d(TAG, "drawItself: ${pickerItem.title} $currentTexture")
    }



    fun bindTextures(textureIds: IntArray, index: Int) {
        Log.d(TAG, "bindTextures: $index")
        this.textures = textureIds
        this.index = index
        texture = bindTexture(textureIds, index * 2, false)
        imageTexture = bindTexture(textureIds, index * 2 + 1, true)
    }

    private fun createBitmap(isSelected: Boolean): Bitmap {
        var bitmap = if (!TextUtils.isEmpty(pickerItem.imgUrl) && pickerItem.isUseImgUrl) {
            val imageLoader = ImageLoader.getInstance()
            val defaultOptions = DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .imageScaleType(ImageScaleType.EXACTLY)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build()
            val config = ImageLoaderConfiguration.Builder(context.get())
                    .defaultDisplayImageOptions(defaultOptions)
                    .memoryCache(WeakMemoryCache())
                    .build()
            imageLoader.init(config)
            val bmp = imageLoader.loadImageSync(pickerItem.imgUrl, ImageSize(bitmapSize.toInt() / 2, bitmapSize.toInt() /2))
            bmp.resizeBitmap(bitmapSize.toInt(), bitmapSize.toInt())
        } else {
            Bitmap.createBitmap(bitmapSize.toInt(), bitmapSize.toInt(), Bitmap.Config.ARGB_4444)
        }

        val bitmapConfig: Bitmap.Config = bitmap.config ?: Bitmap.Config.ARGB_8888

        bitmap = bitmap.copy(bitmapConfig, true)
        canvas = Canvas(bitmap)
        if (isSelected && !pickerItem.isUseImgUrl || TextUtils.isEmpty(pickerItem.imgUrl)) {
            drawImage(canvas)
        }

        //drawIcon(canvas)

        drawBackground(canvas, isSelected)
        drawText(canvas, isSelected)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, withImage: Boolean) {
        val bgPaint = Paint()
        bgPaint.style = Paint.Style.FILL
        pickerItem.color?.let { bgPaint.color = pickerItem.color!! }
        pickerItem.gradient?.let { bgPaint.shader = gradient }
        if (withImage) {
            bgPaint.alpha = (pickerItem.overlayAlpha * 255).toInt()
        }
        canvas.drawRect(0f, 0f, bitmapSize, bitmapSize, bgPaint)
    }

    private fun drawText(canvas: Canvas, isSelected: Boolean) {
        Log.d(TAG, "drawText: $canvas ${pickerItem.title} $isSelected")


        if (pickerItem.title == null) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {

            color = if (isSelected) {
                pickerItem.textColorSelected ?: Color.parseColor("#ffffff")
            }else {
                pickerItem.textColor ?: Color.parseColor("#ffffff")
            }

            textSize = pickerItem.textSize
            typeface = pickerItem.typeface
        }
        val maxTextHeight = bitmapSize / 2f
        textLayout = placeText(paint)
        while (textLayout.height > maxTextHeight) {
            paint.textSize--
            textLayout = placeText(paint)
        }

        canvas.translate((bitmapSize - textLayout.width) / 2f, (bitmapSize - textLayout.height) / 2f)

        textLayout.draw(canvas)
    }

    private fun placeText(paint: TextPaint): DynamicLayout {
        val text = pickerItem.title ?: ""
        return DynamicLayout(text, paint, (bitmapSize * 0.7).toInt(),
            Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
    }

    private fun drawIcon(canvas: Canvas) {
        pickerItem.icon?.let {
            val width = it.intrinsicWidth
            val height = it.intrinsicHeight

            val left = (bitmapSize / 2 - width / 2).toInt()
            val right = (bitmapSize / 2 + width / 2).toInt()

            if (pickerItem.title == null) {
                it.bounds = Rect(left, (bitmapSize / 2 - height / 2).toInt(), right, (bitmapSize / 2 + height / 2).toInt())
            } else if (pickerItem.iconOnTop) {
                it.bounds = Rect(left, (bitmapSize / 2 - height).toInt(), right, (bitmapSize / 2).toInt())
            } else {
                it.bounds = Rect(left, (bitmapSize / 2).toInt(), right, (bitmapSize / 2 + height).toInt())
            }

            it.draw(canvas)
        }
    }

    private  fun getBitmapFromVectorDrawable(drawable: Drawable): Bitmap? {

        val bitmap = Bitmap.createBitmap(
            50,
            50,
            Bitmap.Config.ARGB_8888) ?: return null
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    private fun drawImage(canvas: Canvas) {
        pickerItem.imgDrawable?.let {
            val bm = getBitmapFromVectorDrawable(it) ?: return

            val height = bm.height.toFloat()
            val width = bm.width.toFloat()
            val ratio = height.coerceAtLeast(width) / height.coerceAtMost(width)
            val bitmapHeight = if (height < width) bitmapSize else bitmapSize * ratio
            val bitmapWidth = if (height < width) bitmapSize * ratio else bitmapSize
            it.bounds = Rect(0, 0, bitmapWidth.toInt(), bitmapHeight.toInt())
            it.draw(canvas)

        }
    }

    private fun bindTexture(textureIds: IntArray, index: Int, withImage: Boolean): Int {

        Log.d(TAG, "bindTexture: ${pickerItem.title} $index $withImage ${textureIds.joinToString()}")

        glGenTextures(1, textureIds, index)
        createBitmap(withImage).toTexture(textureIds[index])
        return textureIds[index]
    }

    private fun calculateMatrix(scaleX: Float, scaleY: Float) = FloatArray(16).apply {
        Matrix.setIdentityM(this, 0)
        Matrix.translateM(this, 0, currentPosition.x * scaleX - initialPosition.x,
                currentPosition.y * scaleY - initialPosition.y, 0f)
    }



    private val TAG = "NIENTAG-BP"

}