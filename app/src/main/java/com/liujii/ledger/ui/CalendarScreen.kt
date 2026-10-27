package com.liujii.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.liujii.ledger.LedgerViewModel
import com.liujii.ledger.data.MoneyType
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(vm: LedgerViewModel, onAdd: (() -> Unit)? = null){
    val all by vm.transactions.collectAsState();val transfers by vm.transfers.collectAsState();var month by rememberSaveable{mutableStateOf(YearMonth.now())};var selected by rememberSaveable{mutableStateOf(LocalDate.now())}
    var editingRow by remember { mutableStateOf<com.liujii.ledger.data.TransactionRow?>(null) }
    val today=rememberCurrentDate()
    val zone=java.time.ZoneId.systemDefault()
    val rowsByDate=remember(all,zone){all.groupBy{dateOf(it.occurredAt)}}
    val totalsByDate=remember(rowsByDate){rowsByDate.mapValues{(_,rows)->CalendarDayTotals(rows.filter{it.type==MoneyType.INCOME}.sumOf{it.amountCents},rows.filter{it.type==MoneyType.EXPENSE}.sumOf{it.amountCents})}}
    val selectedRows=rowsByDate[selected].orEmpty()
    val selectedTransfers=remember(transfers,selected,zone){transfers.filter{dateOf(it.occurredAt)==selected}}
    val selectedEntries=remember(selectedRows,selectedTransfers){(selectedRows.map{it.occurredAt to it}+selectedTransfers.map{it.occurredAt to it}).sortedByDescending{it.first}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=24.dp,bottom=100.dp)) {
        item(key="heading"){LedgerPageHeading("日历","按天回看收支节奏",onAdd);Spacer(Modifier.height(16.dp))}
        item(key="month"){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${month.year} 年 ${month.monthValue} 月",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);TextButton({month=YearMonth.from(today);selected=today}){Text("今天",maxLines=1)}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){IconButton({month=month.minusMonths(1);selected=month.atDay(1)}){Icon(Icons.Default.ChevronLeft,"上个月")};IconButton({month=month.plusMonths(1);selected=month.atDay(1)}){Icon(Icons.Default.ChevronRight,"下个月")}}
        Spacer(Modifier.height(8.dp));GlassCard(Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=6.dp,vertical=12.dp)){
            CalendarMonthGrid(month,selected,totalsByDate,onSelect={selected=it})
        }
        }
        val selectedCount=selectedRows.size+selectedTransfers.size
        item(key="day-heading"){Spacer(Modifier.height(24.dp));SectionHeader("${selected.monthValue} 月 ${selected.dayOfMonth} 日",if(selectedCount>0)"$selectedCount 笔" else null)}
        if(selectedEntries.isEmpty())item(key="empty-day"){GlassCard(Modifier.fillMaxWidth()){Text("这一天还没有账目",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        itemsIndexed(selectedEntries,key={_,pair->when(val row=pair.second){is com.liujii.ledger.data.TransactionRow->"t${row.id}";is com.liujii.ledger.data.TransferRow->"f${row.id}";else->error("未知流水")}}){index,pair->
            val corner=20.dp
            Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface.copy(alpha=.91f),shape=RoundedCornerShape(topStart=if(index==0)corner else 0.dp,topEnd=if(index==0)corner else 0.dp,bottomStart=if(index==selectedEntries.lastIndex)corner else 0.dp,bottomEnd=if(index==selectedEntries.lastIndex)corner else 0.dp)) {
                Column(Modifier.padding(start=16.dp,top=4.dp,bottom=4.dp)){
                    when(val entry=pair.second){
                        is com.liujii.ledger.data.TransactionRow->Surface(Modifier.fillMaxWidth().clickable{editingRow=entry},color=Color.Transparent){TransactionCompact(entry)}
                        is com.liujii.ledger.data.TransferRow->TransferCompact(entry)
                    }
                    if(index<selectedEntries.lastIndex)HorizontalDivider(Modifier.padding(end=16.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
                }
            }
        }
    }
    editingRow?.let { TransactionEditor(vm,it,onDismiss={editingRow=null}) }
}

internal data class CalendarDayTotals(val incomeCents:Long=0,val expenseCents:Long=0)

internal fun calendarWeeks(month:YearMonth):List<List<LocalDate?>> {
    val offset=month.atDay(1).dayOfWeek.value-1
    return List((offset+month.lengthOfMonth()+6)/7){week->
        List(7){column->(week*7+column-offset+1).takeIf{it in 1..month.lengthOfMonth()}?.let{month.atDay(it)}}
    }
}

@Composable
internal fun CalendarMonthGrid(month:YearMonth,selected:LocalDate,totalsByDate:Map<LocalDate,CalendarDayTotals>,modifier:Modifier=Modifier,onSelect:(LocalDate)->Unit) {
    val density=LocalDensity.current
    val weeks=remember(month){calendarWeeks(month)}
    // 七列只分配现有宽度，大字体不再把星期日推到屏幕外。
    Column(modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()){listOf("一","二","三","四","五","六","日").forEach{Text(it,Modifier.weight(1f),textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,softWrap=false,lineHeight=1.25.em,autoSize=TextAutoSize.StepBased(minFontSize=with(density){8.dp.toSp()},maxFontSize=14.sp,stepSize=.25.sp))}}
            HorizontalDivider(Modifier.padding(vertical=8.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
            weeks.forEach{week->
                Row(Modifier.fillMaxWidth()){
                    week.forEach{d->
                        if(d!=null){
                            val totals=totalsByDate[d]?:CalendarDayTotals()
                            CalendarDayCell(
                                day=d.dayOfMonth,
                                selected=selected==d,
                                incomeCents=totals.incomeCents,
                                expenseCents=totals.expenseCents,
                                modifier=Modifier.weight(1f),
                                onClick={onSelect(d)},
                            )
                        }else Spacer(Modifier.weight(1f).height(78.dp))
                    }
                }
            }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    selected: Boolean,
    incomeCents: Long,
    expenseCents: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.heightIn(min=80.dp).clip(RoundedCornerShape(12.dp)).semantics{contentDescription="${day}日，收入${money(incomeCents)}，支出${money(expenseCents)}${if(selected)"，已选中"else""}"}.clickable(onClick=onClick).padding(horizontal=1.dp, vertical=4.dp),
        horizontalAlignment=Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
            val density=LocalDensity.current
            val circleSize=minOf(maxWidth,(28*density.fontScale).dp)
            Box(Modifier.size(circleSize).background(if(selected)MaterialTheme.colorScheme.primary else Color.Transparent,RoundedCornerShape(50)),contentAlignment=Alignment.Center) {
                Text(day.toString(),color=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,maxLines=1,softWrap=false,lineHeight=1.2.em,autoSize=TextAutoSize.StepBased(minFontSize=with(density){8.dp.toSp()},maxFontSize=14.sp,stepSize=.25.sp))
            }
        }
        Spacer(Modifier.height(2.dp))
        CalendarAmount(if(incomeCents>0) "+${formatCalendarAmount(incomeCents)}" else "", MaterialTheme.colorScheme.secondary)
        CalendarAmount(if(expenseCents>0) "-${formatCalendarAmount(expenseCents)}" else "", MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun CalendarAmount(value: String, color: Color) {
    val density=LocalDensity.current
    // 零收支的空行不随系统字体放大，但仍为收入、支出各保留独立位置。
    Box(Modifier.fillMaxWidth().heightIn(min=16.dp),contentAlignment=Alignment.Center) {
    if(value.isNotEmpty())Text(
        text=value,
        modifier=Modifier.fillMaxWidth(),
        color=color,
        fontSize=11.sp,
        lineHeight=1.3.em,
        fontFamily=FontFamily.Monospace,
        fontWeight=FontWeight.SemiBold,
        textAlign=TextAlign.Center,
        maxLines=1,
        softWrap=false,
        overflow=TextOverflow.Clip,
        // 只缩放密集月历里的金额，完整金额仍可在当日列表及无障碍描述中查看。
        autoSize=TextAutoSize.StepBased(minFontSize=with(density){6.dp.toSp()},maxFontSize=11.sp,stepSize=.25.sp),
    )
    }
}

fun formatCalendarAmount(cents:Long):String=when{
    cents>=10000000000L->"%.2f亿".format(java.util.Locale.CHINA,cents/10000000000.0)
    cents>=1000000L->"%.2f万".format(java.util.Locale.CHINA,cents/1000000.0)
    cents>=100000L->"%.2f千".format(java.util.Locale.CHINA,cents/100000.0)
    else->"%.2f".format(java.util.Locale.CHINA,cents/100.0)
}
