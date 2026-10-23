package com.liujii.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liujii.ledger.data.*

@Composable
internal fun OfficialThemeGallery(settings: AppearanceSettings,enabled:Boolean=true,onSelected:(String)->Unit) {
    val fontScale=LocalDensity.current.fontScale
    val dark=LocalDarkAppearance.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns=if(maxWidth<280.dp||fontScale>=1.3f)1 else 2
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            officialThemePresets.chunked(columns).forEach{row->
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    row.forEach{preset->
                        val active=themePreset(settings.themeId).id==preset.id
                        val colors=themeColorScheme(preset,dark)
                        Surface(onClick={onSelected(preset.id)},enabled=enabled,
                            modifier=Modifier.weight(1f).semantics{selected=active;stateDescription=if(active)"使用中"else"未选择"},
                            shape=RoundedCornerShape(16.dp),
                            color=if(active)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha=.6f),
                            border=BorderStroke(if(active)2.dp else 1.dp,if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                val start=Color(if(dark)preset.darkStart else preset.lightStart)
                                val end=Color(if(dark)preset.darkEnd else preset.lightEnd)
                                Box(Modifier.fillMaxWidth().height(72.dp).background(Brush.linearGradient(listOf(start,end)),RoundedCornerShape(10.dp)).padding(8.dp)) {
                                    Surface(shape=RoundedCornerShape(8.dp),color=colors.surface.copy(alpha=.91f)) {
                                        Column(Modifier.fillMaxSize().padding(8.dp)) {
                                            Box(Modifier.fillMaxWidth(.45f).height(4.dp).background(colors.onSurface.copy(alpha=.65f),RoundedCornerShape(2.dp)))
                                            Spacer(Modifier.height(6.dp))
                                            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                                                val p=Path().apply{moveTo(0f,size.height*.85f);cubicTo(size.width*.3f,size.height*.9f,size.width*.65f,size.height*.1f,size.width,size.height*.22f)}
                                                drawPath(p,colors.primary,style=Stroke(2.dp.toPx()))
                                                drawCircle(colors.primary,2.dp.toPx(),Offset(size.width,size.height*.22f))
                                            }
                                        }
                                    }
                                }
                                Text(preset.name,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp,Alignment.CenterHorizontally)) {
                                    if(active)Icon(Icons.Default.CheckCircle,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                                    Text(if(active)"使用中"else"点按应用",style=MaterialTheme.typography.labelMedium,color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackgroundChoices(settings:AppearanceSettings,enabled:Boolean,onThemeBackground:()->Unit,onPickImage:()->Unit,onRestoreImage:()->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onThemeBackground,enabled=enabled,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(12.dp)){Text("主题背景",maxLines=1,softWrap=false)}
        OutlinedButton(onPickImage,enabled=enabled,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(12.dp)){Icon(Icons.Default.Image,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("本地图片",maxLines=1,softWrap=false)}
        if(settings.imageUri!=null&&settings.backgroundType!="image")OutlinedButton(onRestoreImage,enabled=enabled,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(12.dp)){Text("恢复上次图片",maxLines=1,softWrap=false)}
    }
}
