package com.liujii.ledger.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

data class BackgroundImage(val bitmap:Bitmap?,val failed:Boolean=false)

// 解码与缓存集中在数据边界；校验原图片时也使旧URI缓存失效，不能靠旧图假装恢复成功。
object BackgroundImages {
    private val cache=object:android.util.LruCache<String,BackgroundImage>(16*1024*1024){override fun sizeOf(key:String,value:BackgroundImage)=value.bitmap?.allocationByteCount?:1}
    fun load(context:Context,uri:Uri,width:Int,height:Int):BackgroundImage {
        val key="$uri-$width-$height"
        return cache.get(key)?:BackgroundImage(decode(context,uri,width,height)).let{result->
            val value=result.copy(failed=result.bitmap==null)
            if(value.bitmap!=null)cache.put(key,value)
            value
        }
    }
    fun validate(context:Context,uri:Uri) {
        cache.snapshot().keys.filter{it.startsWith("$uri-")}.forEach{cache.remove(it)}
        val bitmap=decode(context,uri,1024,1024)?:error("图片已失效或内容损坏，当前背景未改变，请重新选择图片。")
        bitmap.recycle()
    }
    private fun decode(context:Context,uri:Uri,width:Int,height:Int):Bitmap?=runCatching {
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth>0&&bounds.outHeight>0)
        var sample=1
        while(bounds.outWidth/sample>width.coerceAtLeast(1)*2||bounds.outHeight/sample>height.coerceAtLeast(1)*2)sample*=2
        val options=BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.RGB_565}
        context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
    }.getOrNull()
}
