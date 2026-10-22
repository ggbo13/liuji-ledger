package com.liujii.ledger.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

val LocalMotionEnabled=compositionLocalOf { true }
val LocalDarkAppearance=compositionLocalOf { false }

@Composable
internal fun systemAnimationScale(): Float {
    val resolver=LocalContext.current.contentResolver
    fun read()=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
    var value by remember(resolver){mutableFloatStateOf(read())}
    DisposableEffect(resolver) {
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())){override fun onChange(selfChange:Boolean){value=read()}}
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose{resolver.unregisterContentObserver(observer)}
    }
    return value
}

@Composable
internal fun <T> PageTransition(target: T, rank: (T)->Int, modifier: Modifier=Modifier, content: @Composable (T)->Unit) {
    val enabled=LocalMotionEnabled.current
    val distance=with(LocalDensity.current){48.dp.roundToPx()}
    AnimatedContent(targetState=target,modifier=modifier,transitionSpec={
        if(!enabled) EnterTransition.None togetherWith ExitTransition.None
        else {
            val direction=if(rank(targetState)>=rank(initialState))1 else -1
            (slideInHorizontally(tween(280)){direction*distance}+fadeIn(tween(280))) togetherWith
                (slideOutHorizontally(tween(280)){-direction*distance}+fadeOut(tween(180)))
        }.using(SizeTransform(clip=false){_,_->snap()})
    },label="页面过渡"){page->
        val interactive=page==target
        val outgoing=if(interactive)Modifier else Modifier.clearAndSetSemantics{}.pointerInput(page){
            awaitPointerEventScope{while(true)awaitPointerEvent(PointerEventPass.Initial).changes.forEach{it.consume()}}
        }
        Box(Modifier.fillMaxSize().then(outgoing)){content(page)}
    }
}

@Composable
internal fun PressFeedbackButton(onClick:()->Unit,enabled:Boolean=true,modifier:Modifier=Modifier,content:@Composable RowScope.()->Unit) {
    val source=remember{MutableInteractionSource()}
    val pressed by source.collectIsPressedAsState()
    val motion=LocalMotionEnabled.current
    val scale by animateFloatAsState(if(pressed&&motion).98f else 1f,tween(if(motion)100 else 0),label="按钮按压")
    Button(onClick,modifier.graphicsLayer{scaleX=scale;scaleY=scale}.heightIn(min=48.dp),enabled=enabled,interactionSource=source,content=content)
}

@Composable
internal fun AnimatedGroup(expanded:Boolean,content:@Composable ColumnScope.()->Unit) {
    val duration=if(LocalMotionEnabled.current)220 else 0
    AnimatedVisibility(expanded,enter=expandVertically(tween(duration))+fadeIn(tween(duration)),exit=shrinkVertically(tween(duration))+fadeOut(tween(duration))) {
        Column(content=content)
    }
}
