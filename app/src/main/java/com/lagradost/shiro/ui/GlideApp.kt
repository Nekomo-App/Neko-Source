package com.lagradost.shiro.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.lagradost.shiro.BuildConfig

@GlideModule
class ShiroGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)
        // LruBitmapPool can crash with "Cannot obtain size for recycled Bitmap"
        // when trimMemory/clearMemory runs while Glide still owns a recycled
        // bitmap (observed as a FATAL on device during onTrimMemory). Wrap the
        // pool so a poisoned entry degrades to a skipped put/trim instead of a crash.
        val poolSize = MemorySizeCalculator.Builder(context).build().bitmapPoolSize
        builder.apply {
            setBitmapPool(SafeBitmapPool(LruBitmapPool(poolSize.toLong())))
            if (BuildConfig.BUILD_TYPE != "debug") {
                setLogLevel(Log.ERROR)
            }
            RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .signature(ObjectKey(System.currentTimeMillis().toShort()))
        }
    }
}

private class SafeBitmapPool(private val delegate: LruBitmapPool) : BitmapPool {
    override fun getMaxSize(): Long = delegate.maxSize

    override fun setSizeMultiplier(sizeMultiplier: Float) {
        runCatching { delegate.setSizeMultiplier(sizeMultiplier) }
    }

    override fun put(bitmap: Bitmap?) {
        if (bitmap == null) return
        runCatching { delegate.put(bitmap) }.onFailure {
            runCatching { bitmap.recycle() }
        }
    }

    override fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap =
        runCatching { delegate.get(width, height, config) }.getOrNull()
            ?: Bitmap.createBitmap(width, height, config)

    override fun getDirty(width: Int, height: Int, config: Bitmap.Config): Bitmap =
        runCatching { delegate.getDirty(width, height, config) }.getOrNull()
            ?: Bitmap.createBitmap(width, height, config)

    override fun clearMemory() {
        runCatching { delegate.clearMemory() }
    }

    override fun trimMemory(level: Int) {
        runCatching { delegate.trimMemory(level) }
    }
}
