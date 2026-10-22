package com.liujii.ledger.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liujii.ledger.data.AppearanceSettings
import com.liujii.ledger.data.MoneyType
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.liujii.ledger.data.resolveBackgroundColors

private data class BackgroundImage(val bitmap:android.graphics.Bitmap?,val failed:Boolean=false)
private val backgroundCache by lazy { object:android.util.LruCache<String,BackgroundImage>(16*1024*1024){override fun sizeOf(key:String,value:BackgroundImage)=value.bitmap?.allocationByteCount?:1} }

@Composable
fun Background(settings: AppearanceSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val targetWidth = windowSize.width.coerceAtLeast(1)
    val targetHeight = windowSize.height.coerceAtLeast(1)
    val gradient=resolveBackgroundColors(settings,LocalDarkAppearance.current)
    val gradientStart by animateColorAsState(Color(gradient.first),tween(if(LocalMotionEnabled.current)250 else 0),label="背景起色")
    val gradientEnd by animateColorAsState(Color(gradient.second),tween(if(LocalMotionEnabled.current)250 else 0),label="背景终色")
    val backgroundImage by produceState(BackgroundImage(null),settings.imageUri,targetWidth,targetHeight,settings.backgroundType) {
        val uri=settings.imageUri
        if(settings.backgroundType=="image"&&uri!=null) {
            val key="$uri-${targetWidth.toInt()}-${targetHeight.toInt()}"
            value=backgroundCache.get(key)?:withContext(Dispatchers.IO){
                val bitmap=loadSampledBackground(context,Uri.parse(uri),targetWidth.toInt(),targetHeight.toInt())
                BackgroundImage(bitmap,bitmap==null).also{if(bitmap!=null)backgroundCache.put(key,it)}
            }
        } else value=BackgroundImage(null)
    }
    val loadedBitmap=backgroundImage.bitmap
    val imageUnavailable = settings.backgroundType == "image" && backgroundImage.failed
    Box(Modifier.fillMaxSize()) {
        if (settings.backgroundType == "image" && settings.imageUri != null) {
            if (loadedBitmap != null) Image(loadedBitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale=ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(gradientStart,gradientEnd))))
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(gradientStart,gradientEnd))))
        }
        val alpha=com.liujii.ledger.data.backgroundOverlayAlpha(settings.overlayAlpha)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha=alpha)))
        // 自定义背景不是 Material Surface，需要显式向整个页面提供主题前景色。
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
        if(imageUnavailable) Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=8.dp),shape=CircleShape,color=MaterialTheme.colorScheme.errorContainer.copy(alpha=.95f)){Text("背景图片已失效，已使用默认背景",Modifier.padding(horizontal=14.dp,vertical=8.dp),color=MaterialTheme.colorScheme.onErrorContainer,style=MaterialTheme.typography.labelMedium)}
    }
}

private fun loadSampledBackground(context: android.content.Context, uri: Uri, targetWidth: Int, targetHeight: Int): android.graphics.Bitmap? = runCatching {
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
    var sample=1
    while(bounds.outWidth/sample>targetWidth.coerceAtLeast(1)*2 || bounds.outHeight/sample>targetHeight.coerceAtLeast(1)*2) sample*=2
    val options=BitmapFactory.Options().apply{inSampleSize=sample.coerceAtLeast(1);inPreferredConfig=android.graphics.Bitmap.Config.RGB_565}
    context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
}.getOrNull()

@Composable
fun ScreenColumn(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=24.dp, bottom=100.dp)) {
        Text(title, style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold)
        if(subtitle != null) Text(subtitle, color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(top=3.dp, bottom=18.dp)) else Spacer(Modifier.height(18.dp))
        content()
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(18.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha=.91f)).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=.30f), RoundedCornerShape(20.dp)).padding(contentPadding)) {
        // 半透明卡片同样要提供 onSurface，避免黑夜模式继承到默认黑色。
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

private val markerColors = listOf(
    0xFFE76F64 to "珊瑚红", 0xFF2FAE83 to "薄荷绿", 0xFF4D8BB8 to "雾蓝",
    0xFF9A78C6 to "柔紫", 0xFFE0A04B to "琥珀黄", 0xFF2DB7B4 to "青绿",
    0xFF778899 to "石墨灰"
)

fun canonicalMarkerColor(value: Long): Long {
    val originalFamily = when (value) {
        0xFFE8C264 -> 0xFFE0A04B
        0xFF4CAF50, 0xFF2FBE83 -> 0xFF2FAE83
        0xFFE75F09 -> 0xFFE0A04B
        0xFF667F74, 0xFF808080, 0xFF84847E -> 0xFF778899
        0xFF9B63FF -> 0xFF9A78C6
        0xFFD67862 -> 0xFFE76F64
        else -> null
    }
    if (originalFamily != null) return originalFamily
    return markerColors.minBy { (candidate, _) ->
        val red = ((value shr 16) and 0xFF) - ((candidate shr 16) and 0xFF)
        val green = ((value shr 8) and 0xFF) - ((candidate shr 8) and 0xFF)
        val blue = (value and 0xFF) - (candidate and 0xFF)
        red * red + green * green + blue * blue
    }.first
}

fun sanitizeMoneyInput(raw: String): String {
    val normalized = raw.replace('．', '.').replace('，', '.')
    val result = StringBuilder()
    var hasDot = false
    var decimals = 0
    normalized.forEach { char ->
        when {
            char.isDigit() && (!hasDot || decimals < 2) -> {
                result.append(char)
                if (hasDot) decimals++
            }
            char == '.' && !hasDot -> {
                if (result.isEmpty()) result.append('0')
                result.append(char)
                hasDot = true
            }
        }
    }
    return result.toString().take(12)
}

@Composable
fun MarkerColorPicker(selectedColor: Long, onColorSelected: (Long) -> Unit) {
    val selectedFamily = canonicalMarkerColor(selectedColor)
    val visibleColors = markerColors
    val scroll=rememberScrollState()
    val density=androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(Unit) { withFrameNanos{};scroll.scrollTo(with(density){(visibleColors.indexOfFirst{it.first==selectedFamily}*52).dp.roundToPx()}) }
    Column {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        visibleColors.forEach { (value, name) ->
            MarkerColorSwatch(value, name, selectedFamily == value) { onColorSelected(value) }
        }
    }
    Text("当前选择：${markerColors.first{it.first==selectedFamily}.second}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
    }
}

@Composable
private fun MarkerColorSwatch(value: Long, name: String, selected: Boolean, onClick: () -> Unit) {
    val swatch = Color(value)
    Box(
        Modifier.size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$name${if (selected) "，已选中" else ""}" },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(Modifier.size(42.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
        }
        Box(Modifier.size(32.dp).background(swatch, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(Icons.Default.Check, null, Modifier.size(20.dp),
                    tint = if (swatch.luminance() > .179f) Color.Black else Color.White)
            }
        }
    }
}

fun money(cents: Long, signed: Boolean = false): String {
    val prefix = if(signed && cents > 0) "+" else ""
    return prefix + NumberFormat.getCurrencyInstance(Locale.CHINA).format(java.math.BigDecimal.valueOf(cents,2))
}

fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
fun dateText(millis: Long): String = dateOf(millis).format(DateTimeFormatter.ofPattern("MM月dd日"))

@Composable
fun TypeBadge(type: MoneyType) {
    val color = if(type == MoneyType.INCOME) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
    Text(if(type==MoneyType.INCOME) "收入" else "支出", color=color, style=MaterialTheme.typography.labelMedium, modifier=Modifier.background(color.copy(alpha=.12f), CircleShape).padding(horizontal=9.dp, vertical=4.dp))
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
        Text(title, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.SemiBold, modifier=Modifier.weight(1f))
        if(action != null) TextButton(onClick=onAction) { Text(action) }
    }
}

@Composable
fun DialogAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    TextButton(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=48.dp).widthIn(min=64.dp)) {
        Text(label,softWrap=true,textAlign=androidx.compose.ui.text.style.TextAlign.Center,
            color=if(destructive&&enabled)MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified)
    }
}

@Composable
fun AmountText(cents: Long, type: MoneyType? = null, large: Boolean = false) {
    val color = when(type) { MoneyType.INCOME -> MaterialTheme.colorScheme.secondary; MoneyType.EXPENSE -> MaterialTheme.colorScheme.tertiary; null -> MaterialTheme.colorScheme.onSurface }
    Text(money(cents, type==MoneyType.INCOME), color=color, fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold, style=if(large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium)
}
