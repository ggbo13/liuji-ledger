package com.liujii.ledger.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 所有主页面共用同一套账页页眉；操作区不覆盖正文。 */
@Composable
internal fun LedgerPageHeading(title: String, subtitle: String? = null, onAdd: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        }
        onAdd?.let {
            FilledIconButton(onClick = it, modifier = Modifier.size(48.dp), shape = CircleShape) {
                Icon(Icons.Default.Add, "记一笔")
            }
        }
    }
}

/** 只有摘要使用叠页，其余分组维持轻量边框，避免每条流水都是一张大卡片。 */
@Composable
internal fun LedgerPaper(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    Box(modifier.padding(bottom = 7.dp)) {
        Box(Modifier.matchParentSize().padding(horizontal = 7.dp).offset(y = 7.dp)
            .clip(shape).background(colors.outlineVariant.copy(alpha = .5f)))
        Box(Modifier.matchParentSize().padding(horizontal = 3.dp).offset(y = 4.dp)
            .clip(shape).background(colors.surfaceVariant))
        Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface.copy(alpha = .94f))
            .border(1.dp, colors.outlineVariant.copy(alpha = .5f), shape).padding(18.dp)) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() }
        }
    }
}

@Composable
internal fun BudgetTicks(expenseCents: Long, limitCents: Long, modifier: Modifier = Modifier) {
    val actualProgress = if (limitCents > 0) (expenseCents.toDouble() / limitCents).coerceAtLeast(0.0) else 0.0
    val progress = actualProgress.coerceAtMost(1.0)
    val fill = if (expenseCents > limitCents) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)
    Canvas(modifier.fillMaxWidth().height(28.dp).semantics {
        contentDescription = "预算使用${(actualProgress * 100).toInt()}%，已用${money(expenseCents)}，额度${money(limitCents)}"
    }) {
        val gap = 3.dp.toPx()
        val width = ((size.width - gap * 19) / 20).coerceAtLeast(0f)
        repeat(20) { index ->
            val height = if ((index + 1) % 5 == 0) size.height else size.height * .72f
            val left = index * (width + gap)
            val top = size.height - height
            drawRoundRect(track, androidx.compose.ui.geometry.Offset(left, top),
                androidx.compose.ui.geometry.Size(width, height), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            val portion = (progress * 20 - index).coerceIn(0.0, 1.0).toFloat()
            if (portion > 0) drawRoundRect(fill, androidx.compose.ui.geometry.Offset(left, size.height - height * portion),
                androidx.compose.ui.geometry.Size(width, height * portion), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        }
    }
}

internal data class LedgerTab(val label: String, val icon: ImageVector)

/** 书签只在底栏内部移动，页面离场层无法挡住连续切换。 */
@Composable
internal fun LedgerNavigation(tabs: List<LedgerTab>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .97f)) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                val tabWidth = maxWidth / tabs.size
                val target = tabWidth * selected
                val left by animateDpAsState(target, tween(if (LocalMotionEnabled.current) 220 else 0), label = "书签滑动")
                val bookmarkColor=MaterialTheme.colorScheme.primaryContainer
                val bookmarkLine=MaterialTheme.colorScheme.primary.copy(alpha=.2f)
                Canvas(Modifier.matchParentSize()) {
                    val origin=androidx.compose.ui.geometry.Offset(left.toPx(),0f)
                    val area=androidx.compose.ui.geometry.Size(tabWidth.toPx(),size.height)
                    val radius=androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                    drawRoundRect(bookmarkColor,origin,area,radius)
                    drawRoundRect(bookmarkLine,origin,area,radius,style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    tabs.forEachIndexed { index, tab ->
                        val active = selected == index
                        Column(Modifier.weight(1f).heightIn(min = 64.dp)
                            .selectable(active, role = Role.Tab, onClick = { onSelect(index) }).padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(tab.icon, null, Modifier.size(22.dp), tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(tab.label, style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
