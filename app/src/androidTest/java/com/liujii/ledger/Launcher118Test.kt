package com.liujii.ledger

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class Launcher118Test {
    @Test fun launcherAndMonochromeResourcesDrawWithAndroidMask() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val launcher=context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
        launcher.setBounds(0,0,256,256)
        launcher.draw(Canvas(bitmap))
        val launcherPixels=IntArray(256*256)
        bitmap.getPixels(launcherPixels,0,256,0,0,256,256)
        val visiblePagePixels=launcherPixels.count{Color.alpha(it)>200&&Color.red(it)>210&&Color.green(it)>210&&Color.blue(it)>180}
        assertTrue("自适应图标应包含纸白账页，只有背景不能通过",visiblePagePixels>2000)
        capture(bitmap,"launcher")
        val mono=context.getDrawable(R.drawable.ic_launcher_monochrome)!!
        val monoBitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
        mono.setBounds(0,0,256,256)
        mono.draw(Canvas(monoBitmap))
        val pixels=IntArray(256*256)
        monoBitmap.getPixels(pixels,0,256,0,0,256,256)
        assertTrue("单色图标必须有独立可见轮廓",pixels.count{Color.alpha(it)>200}>600)
        if(Build.VERSION.SDK_INT>=33)assertNotNull("Android 13起使用单色图标资源",launcher.monochrome)
        capture(monoBitmap,"monochrome")
    }

    private fun capture(bitmap:Bitmap,name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val output=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val dir=(output?.let{java.io.File(it)}?:context.filesDir).resolve("visual118").apply{mkdirs()}
        dir.resolve("icon_$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
}
