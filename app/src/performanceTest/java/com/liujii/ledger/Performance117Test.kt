package com.liujii.ledger

import android.app.UiAutomation
import android.graphics.Rect
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.liujii.ledger.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

// 只由独立性能任务编译；真实时间注入触摸，不使用 Compose 测试时钟快进动画。
class Performance117Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val automation:UiAutomation get()=instrumentation.uiAutomation
    private val durations=ConcurrentLinkedQueue<Long>()

    @Test fun tenThousandSyntheticRowsAcrossScrollingChartAndNavigation()=runBlocking<Unit> {
        require(InstrumentationRegistry.getArguments().getString("allowSyntheticPerformanceData")=="true"){"必须显式启用合成性能夹具"}
        require(Build.HARDWARE=="ranchu"||Build.HARDWARE=="goldfish"){"仅允许专用模拟器，禁止操作用户手机数据"}
        val context=instrumentation.targetContext
        val dao=LedgerDatabase.get(context).dao()
        val original=dao.snapshot()
        require(original.transactions.all{it.note.startsWith("[性能合成]")}){"测试环境含非性能数据，停止以保护已有记录"}
        val account=original.accounts.first{it.active}.id
        val expense=original.categories.first{it.active&&it.type==MoneyType.EXPENSE}.id
        val income=original.categories.first{it.active&&it.type==MoneyType.INCOME}.id
        val method=original.paymentMethods.first{it.active}.id
        val today=java.time.LocalDate.now()
        val zone=java.time.ZoneId.systemDefault()
        dao.insertTransactions(List(10000){i->TransactionEntity(id=100001L+i,type=if(i%3==0)MoneyType.INCOME else MoneyType.EXPENSE,amountCents=101L+i%9000,categoryId=if(i%3==0)income else expense,accountId=account,occurredAt=today.minusDays((i%28).toLong()).atTime(12,0).atZone(zone).toInstant().toEpochMilli()+i,note="[性能合成]记录$i",paymentMethodId=method)})
        val samples=JSONArray()
        val thread=HandlerThread("性能帧收集").apply{start()}
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            lateinit var window:Window
            val listener=Window.OnFrameMetricsAvailableListener{_,metrics,_->durations.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))}
            scenario.onActivity{activity->window=activity.window;window.addOnFrameMetricsAvailableListener(listener,Handler(thread.looper))}
            try {
                awaitNode("明细",false);SystemClock.sleep(700)
                tap("明细");SystemClock.sleep(700)
                val viewport=Rect().also{scenario.onActivity{activity->activity.window.decorView.getWindowVisibleDisplayFrame(it)}}
                samples.put(measure("明细滚动") { repeat(8){swipe(viewport.centerX().toFloat(),viewport.top+viewport.height()*.76f,viewport.centerX().toFloat(),viewport.top+viewport.height()*.3f,320)} })
                tap("统计");SystemClock.sleep(700)
                val chart=Rect().also{awaitNode("收支折线图",true).getBoundsInScreen(it)}
                samples.put(measure("图表拖动") { repeat(8){i->val left=chart.left+chart.width()*.08f;val right=chart.right-chart.width()*.08f;swipe(if(i%2==0)left else right,chart.centerY().toFloat(),if(i%2==0)right else left,chart.centerY().toFloat(),320)} })
                samples.put(measure("页面切换") { repeat(2){for(name in listOf("首页","明细","统计","日历","设置")){tap(name);SystemClock.sleep(400)}} })
                assertEquals(10000,dao.snapshot().transactions.size)
                val outputRoot=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.filesDir.absolutePath
                val directory=java.io.File(outputRoot,"performance117").apply{mkdirs()}
                val result=JSONObject().put("version",BuildConfig.VERSION_NAME).put("hardware",Build.HARDWARE).put("api",Build.VERSION.SDK_INT).put("syntheticRows",10000).put("samples",samples)
                directory.resolve("frames-${BuildConfig.VERSION_NAME}.json").writeText(result.toString(2))
                for((name,id)in listOf("首页" to "home","明细" to "details","统计" to "statistics","日历" to "calendar","设置" to "settings")){
                    tap(name);SystemClock.sleep(450)
                    automation.takeScreenshot()?.let{bitmap->directory.resolve("${id}-${BuildConfig.VERSION_NAME}.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
                }
            } finally {scenario.onActivity{window.removeOnFrameMetricsAvailableListener(listener)}}
        }
        thread.quitSafely()
    }
    private fun measure(name:String,action:()->Unit):JSONObject {
        durations.clear();val start=SystemClock.elapsedRealtime();action();SystemClock.sleep(150)
        val values=durations.toList().filter{it>0}.sorted()
        assertTrue("$name 没有采集到真实帧",values.isNotEmpty())
        fun percentile(p:Double)=values[((values.size-1)*p).toInt()]/1_000_000.0
        return JSONObject().put("scene",name).put("frames",values.size).put("p50Ms",percentile(.5)).put("p95Ms",percentile(.95)).put("over16_67Ms",values.count{it>16_670_000}).put("elapsedMs",SystemClock.elapsedRealtime()-start)
    }
    private fun awaitNode(value:String,prefix:Boolean,navigation:Boolean=false):AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+15000
        while(SystemClock.uptimeMillis()<deadline) {
            val root=automation.rootInActiveWindow
            if(root!=null){val nodes=ArrayDeque<AccessibilityNodeInfo>();nodes.add(root)
                root.refresh()
                val screen=Rect().also{root.getBoundsInScreen(it)}
                while(nodes.isNotEmpty()){val node=nodes.removeFirst();val desc=node.contentDescription?.toString().orEmpty();val text=node.text?.toString().orEmpty()
                    val bounds=Rect().also{node.getBoundsInScreen(it)}
                    if((if(prefix)desc.startsWith(value)||text.startsWith(value) else desc==value||text==value)&&node.isVisibleToUser&&(!navigation||bounds.centerY()>screen.bottom-screen.height()/4))return node
                    for(i in 0 until node.childCount)node.getChild(i)?.let{nodes.add(it)}
                }
            }
            SystemClock.sleep(100)
        }
        error("界面未显示：$value")
    }
    private fun tap(value:String) {
        var node=awaitNode(value,false,true)
        while(!node.isClickable&&node.parent!=null)node=node.parent
        val bounds=Rect().also{node.getBoundsInScreen(it)}
        val down=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            event.source=InputDevice.SOURCE_TOUCHSCREEN
            try{check(automation.injectInputEvent(event,true))}finally{event.recycle()}
        }
        // 无障碍缓存中的selected可能滞后；验证当前可见标题，而非把旧节点当成界面状态。
        val heading=if(value=="首页")"流迹"else value
        val deadline=SystemClock.uptimeMillis()+15000
        while(SystemClock.uptimeMillis()<deadline){
            val root=automation.rootInActiveWindow
            if(root!=null){
                root.refresh()
                val screen=Rect().also{root.getBoundsInScreen(it)}
                val nodes=ArrayDeque<AccessibilityNodeInfo>();nodes.add(root)
                while(nodes.isNotEmpty()){
                    val current=nodes.removeFirst();if(!current.refresh())continue
                    val visible=Rect().also{current.getBoundsInScreen(it)}
                    if(current.text?.toString()==heading&&current.isVisibleToUser&&visible.centerY()<screen.top+screen.height()/3){automation.waitForIdle(350,15000);return}
                    for(i in 0 until current.childCount)current.getChild(i)?.let{nodes.add(it)}
                }
            }
            SystemClock.sleep(100)
        }
        val context=instrumentation.targetContext
        val output=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.filesDir.absolutePath
        val directory=java.io.File(output,"performance117").apply{mkdirs()}
        automation.takeScreenshot()?.let{bitmap->directory.resolve("navigation-failure-${BuildConfig.VERSION_NAME}.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
        error("导航没有切换到：$value，触摸区域=$bounds")
    }
    private fun swipe(x0:Float,y0:Float,x1:Float,y1:Float,duration:Int) {
        val down=SystemClock.uptimeMillis()
        fun inject(action:Int,x:Float,y:Float){val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.source=InputDevice.SOURCE_TOUCHSCREEN;try{check(automation.injectInputEvent(event,true))}finally{event.recycle()}}
        inject(MotionEvent.ACTION_DOWN,x0,y0)
        val steps=20
        for(i in 1..steps){SystemClock.sleep((duration/steps).toLong());val t=i.toFloat()/steps;inject(MotionEvent.ACTION_MOVE,x0+(x1-x0)*t,y0+(y1-y0)*t)}
        inject(MotionEvent.ACTION_UP,x1,y1)
    }
}
