package com.liujii.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liujii.ledger.LedgerViewModel
import com.liujii.ledger.data.MoneyType
import com.liujii.ledger.data.CategoryEntity
import com.liujii.ledger.data.AccountEntity
import com.liujii.ledger.data.TransferRow
import com.liujii.ledger.data.AccountAdjustmentRow
import com.liujii.ledger.data.TransactionRow
import com.liujii.ledger.data.accountBalance
import com.liujii.ledger.data.totalAssets
import com.liujii.ledger.data.periodChange
import com.liujii.ledger.data.statisticsSummary
import com.liujii.ledger.data.statisticsChartSeries
import com.liujii.ledger.data.StatisticsChartSeries
import com.liujii.ledger.data.ChartGestureAxis
import com.liujii.ledger.data.chartAxisAfterSlop
import com.liujii.ledger.data.chartIndexAt
import com.liujii.ledger.data.StatementScope
import com.liujii.ledger.data.statementDateRange
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private enum class Period(val label:String) { WEEK("本周"), MONTH("本月"), YEAR("本年") }
// 账本是记录范围，旧资产计算界面保留供兼容审查，当前版本不作为产品入口。
private const val LEGACY_ASSET_REPORT_ENABLED=false

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun StatsScreen(vm: LedgerViewModel, onAdd: (() -> Unit)? = null) {
    val all by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val transfers by vm.transfers.collectAsState()
    val adjustments by vm.adjustments.collectAsState()
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    var detailCategoryId by remember { mutableStateOf<Long?>(null) }
    var editingRow by remember { mutableStateOf<TransactionRow?>(null) }
    val today=rememberCurrentDate()
    var statementScope by rememberSaveable { mutableStateOf<StatementScope?>(null) }
    var statementMonth by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var statementYear by rememberSaveable { mutableIntStateOf(today.year) }
    val zone=ZoneId.systemDefault()
    val start=remember(period,today){when(period){Period.WEEK->today.minusDays((today.dayOfWeek.value-1).toLong());Period.MONTH->today.withDayOfMonth(1);Period.YEAR->today.withDayOfYear(1)}}
    val summary=remember(all,start,today,zone){statisticsSummary(all,start,today,zone)}
    val rows=summary.rows
    val income=summary.incomeCents; val expense=summary.expenseCents
    val days=ChronoUnit.DAYS.between(start,today).toInt()+1
    val previousStart=when(period){Period.WEEK->start.minusWeeks(1);Period.MONTH->start.minusMonths(1);Period.YEAR->start.minusYears(1)}
    val previousEnd=when(period){Period.WEEK->today.minusWeeks(1);Period.MONTH->today.minusMonths(1);Period.YEAR->today.minusYears(1)}
    val previousSummary=remember(all,previousStart,previousEnd,zone){statisticsSummary(all,previousStart,previousEnd,zone)}
    val previousIncome=previousSummary.incomeCents
    val previousExpense=previousSummary.expenseCents
    ScreenColumn("统计", "看清钱的来路与去处",onAdd=onAdd) {
        Row(Modifier.fillMaxWidth().padding(bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { p ->
                Surface(
                    selected=period==p,
                    onClick={period=p},
                    modifier=Modifier.weight(1f).heightIn(min=48.dp),
                    shape=RoundedCornerShape(topStart=5.dp,topEnd=12.dp,bottomStart=5.dp,bottomEnd=5.dp),
                    color=if(period==p)MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor=MaterialTheme.colorScheme.onSurface,
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                        Text(p.label,maxLines=1,softWrap=false)
                    }
                }
            }
        }
        StatementReportLinks(
            monthLabel="${today.year} 年 ${today.monthValue} 月",
            yearLabel="${today.year} 年",
            onMonthClick={
                statementMonth=YearMonth.from(today).toString()
                statementScope=StatementScope.MONTH
            },
            onYearClick={
                statementYear=today.year
                statementScope=StatementScope.YEAR
            },
        )
        Spacer(Modifier.height(12.dp))
        if (LEGACY_ASSET_REPORT_ENABLED) {
        Spacer(Modifier.height(16.dp));SectionHeader("资产趋势")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("当前总资产",color=MaterialTheme.colorScheme.onSurfaceVariant);Text(money(totalAssets(accounts,all,transfers,adjustments)),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(14.dp));AssetTrendChart(accounts,all,transfers,adjustments,start,today)
            val balances=accounts.filter{it.includeInTotal}.map{it to accountBalance(it,all,transfers,adjustments)}
            val positiveTotal=balances.sumOf{(_,value)->value.coerceAtLeast(0)}.coerceAtLeast(1)
            balances.sortedByDescending{it.second}.forEach{(account,value)->val pct=(value.coerceAtLeast(0).toFloat()/positiveTotal);Column(Modifier.padding(top=9.dp)){Row(Modifier.fillMaxWidth()){Text(account.name,Modifier.weight(1f));Text(money(value))};LinearProgressIndicator({pct},Modifier.fillMaxWidth().padding(top=5.dp),color=androidx.compose.ui.graphics.Color(account.color),trackColor=MaterialTheme.colorScheme.surfaceVariant)}}
        }
        Spacer(Modifier.height(12.dp))
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) { Column(Modifier.weight(1f)){Text("收入",color=MaterialTheme.colorScheme.onSurfaceVariant);AmountText(income,MoneyType.INCOME)};Column(Modifier.weight(1f)){Text("支出",color=MaterialTheme.colorScheme.onSurfaceVariant);AmountText(-expense,MoneyType.EXPENSE)} }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Text(periodChange(income,previousIncome),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(periodChange(expense,previousExpense),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Text("上期对比：$previousStart 至 $previousEnd",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=5.dp))
            Spacer(Modifier.height(20.dp))
            TrendChart(rows,start,today)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(start.toString(),style=MaterialTheme.typography.labelSmall);Text(today.toString(),style=MaterialTheme.typography.labelSmall)}
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            GlassCard(Modifier.weight(1f)){Text("日均支出",color=MaterialTheme.colorScheme.onSurfaceVariant);Text(money(expense/days.coerceAtLeast(1)),fontWeight=FontWeight.Bold)}
            GlassCard(Modifier.weight(1f)){Text("收支结余",color=MaterialTheme.colorScheme.onSurfaceVariant);Text(money(income-expense),fontWeight=FontWeight.Bold,color=if(income>=expense)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary)}
        }
        Spacer(Modifier.height(22.dp));SectionHeader("支出分类")
        GlassCard(Modifier.fillMaxWidth()) {
            val groups=summary.categoryExpenses
            if(groups.isEmpty())Text("当前周期还没有支出",color=MaterialTheme.colorScheme.onSurfaceVariant)
            groups.forEach{(categoryId,amount)->val c=categories.find{it.id==categoryId};val pct=if(expense==0L)0f else amount.toFloat()/expense;Column(Modifier.fillMaxWidth().clickable{detailCategoryId=categoryId}.padding(vertical=9.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(c?.name?:"已停用分类",Modifier.weight(1f));Text("${(pct*100).toInt()}%  ${money(amount)}");Spacer(Modifier.width(6.dp));Icon(Icons.Default.ChevronRight,"查看${c?.name?:"分类"}明细",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)};LinearProgressIndicator({pct},Modifier.fillMaxWidth().padding(top=7.dp),color=androidx.compose.ui.graphics.Color(c?.color?:0xFF89949C),trackColor=MaterialTheme.colorScheme.surfaceVariant)}}
        }
    }
    statementScope?.let { scope ->
        val month=runCatching{YearMonth.parse(statementMonth)}.getOrDefault(YearMonth.from(today))
        StatementReportSheet(
            scope=scope,
            month=month,
            year=statementYear,
            today=today,
            rows=all,
            categories=categories,
            onMonthChange={statementMonth=it.toString()},
            onYearChange={statementYear=it},
            onDismiss={statementScope=null},
            onEdit={row->statementScope=null;editingRow=row},
        )
    }
    detailCategoryId?.let { categoryId ->
        val category=categories.find{it.id==categoryId}
        val details=remember(rows,categoryId){rows.filter{it.type==MoneyType.EXPENSE&&it.categoryId==categoryId}.sortedByDescending{it.occurredAt}}
        val detailTotal=remember(details){details.sumOf{it.amountCents}}
        ModalBottomSheet(onDismissRequest={detailCategoryId=null}) {
            Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=28.dp)) {
                Text("${category?.name?:"分类"}明细",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                Text("${start} 至 ${today} · ${details.size} 笔 · ${money(detailTotal)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp,bottom=10.dp))
                StatisticsCategoryDetails(details){row->detailCategoryId=null;editingRow=row}
            }
        }
    }
    editingRow?.let { row -> TransactionEditor(vm,row,onDismiss={editingRow=null}) }
}

@Composable
internal fun StatementReportLinks(
    monthLabel:String,
    yearLabel:String,
    onMonthClick:()->Unit,
    onYearClick:()->Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        StatementReportLink(
            title="月总账单",
            subtitle="$monthLabel · 完整收支与全部流水",
            icon={Icon(Icons.AutoMirrored.Filled.ReceiptLong,null,Modifier.size(22.dp))},
            onClick=onMonthClick,
        )
        HorizontalDivider(Modifier.padding(vertical=6.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f))
        StatementReportLink(
            title="年总账单",
            subtitle="$yearLabel · 全年收支与分类汇总",
            icon={Icon(Icons.Default.CalendarMonth,null,Modifier.size(22.dp))},
            onClick=onYearClick,
        )
    }
}

@Composable
private fun StatementReportLink(
    title:String,
    subtitle:String,
    icon:@Composable ()->Unit,
    onClick:()->Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=64.dp).padding(vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=.12f),RoundedCornerShape(13.dp)),
            contentAlignment=Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary){icon()}
        }
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatementReportSheet(
    scope:StatementScope,
    month:YearMonth,
    year:Int,
    today:LocalDate,
    rows:List<TransactionRow>,
    categories:List<CategoryEntity>,
    onMonthChange:(YearMonth)->Unit,
    onYearChange:(Int)->Unit,
    onDismiss:()->Unit,
    onEdit:(TransactionRow)->Unit,
) {
    val zone=ZoneId.systemDefault()
    val anchor=if(scope==StatementScope.MONTH)month.atDay(1) else LocalDate.of(year,1,1)
    val range=remember(scope,anchor){statementDateRange(scope,anchor)}
    val currentRange=remember(scope,today){statementDateRange(scope,today)}
    val summary=remember(rows,range,zone){statisticsSummary(rows,range.start,range.end,zone)}
    val canNext=range.end<currentRange.end
    val canCurrent=anchor.year!=today.year||(scope==StatementScope.MONTH&&month.monthValue!=today.monthValue)
    val title=if(scope==StatementScope.MONTH)"${month.year} 年 ${month.monthValue} 月" else "$year 年"
    val previousLabel=if(scope==StatementScope.MONTH)"上一个月" else "上一年"
    val nextLabel=if(scope==StatementScope.MONTH)"下一个月" else "下一年"
    ModalBottomSheet(onDismissRequest=onDismiss) {
        StatementReportContent(
            title=title,
            scopeLabel=if(scope==StatementScope.MONTH)"月总账单" else "年总账单",
            range=range,
            summary=summary,
            categories=categories,
            previousLabel=previousLabel,
            nextLabel=nextLabel,
            canNext=canNext,
            canCurrent=canCurrent,
            onPrevious={
                if(scope==StatementScope.MONTH)onMonthChange(month.minusMonths(1))
                else onYearChange(year-1)
            },
            onNext={
                if(scope==StatementScope.MONTH)onMonthChange(month.plusMonths(1))
                else onYearChange(year+1)
            },
            onCurrent={
                if(scope==StatementScope.MONTH)onMonthChange(YearMonth.from(today))
                else onYearChange(today.year)
            },
            onEdit=onEdit,
        )
    }
}

@Composable
internal fun StatementReportContent(
    title:String,
    scopeLabel:String,
    range:com.liujii.ledger.data.StatementDateRange,
    summary:com.liujii.ledger.data.StatisticsSummary,
    categories:List<CategoryEntity>,
    previousLabel:String,
    nextLabel:String,
    canNext:Boolean,
    canCurrent:Boolean,
    onPrevious:()->Unit,
    onNext:()->Unit,
    onCurrent:()->Unit,
    onEdit:(TransactionRow)->Unit,
) {
    val incomeGroups=remember(summary.rows){summary.rows.filter{it.type==MoneyType.INCOME}.groupBy{it.categoryId}.map{it.key to it.value.sumOf{r->r.amountCents}}.sortedByDescending{it.second}}
    val sortedRows=remember(summary.rows){summary.rows.sortedByDescending{it.occurredAt}}
    LazyColumn(
        Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal=20.dp),
        contentPadding=PaddingValues(bottom=28.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onPrevious,modifier=Modifier.size(48.dp)) {
                    Icon(Icons.Default.ChevronLeft,previousLabel)
                }
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(scopeLabel,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)
                }
                IconButton(onClick=onNext,enabled=canNext,modifier=Modifier.size(48.dp)) {
                    Icon(Icons.Default.ChevronRight,nextLabel)
                }
            }
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                TextButton(
                    onClick=onCurrent,
                    enabled=canCurrent,
                    modifier=Modifier.heightIn(min=48.dp),
                ) {
                    Text(if(scopeLabel=="月总账单")"回到本月" else "回到本年")
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("收支总览",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    StatementMetric("收入",summary.incomeCents,MaterialTheme.colorScheme.secondary,Modifier.weight(1f))
                    StatementMetric("支出",-summary.expenseCents,MaterialTheme.colorScheme.tertiary,Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Bottom) {
                    StatementMetric(
                        "结余",
                        summary.incomeCents-summary.expenseCents,
                        if(summary.incomeCents>=summary.expenseCents)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                        Modifier.weight(1f),
                    )
                    Column(Modifier.weight(1f)) {
                        Text("账目笔数",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${summary.rows.size} 笔",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    }
                }
                Text(
                    "${range.start} 至 ${range.end}",
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier=Modifier.padding(top=12.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
        }
        item {
            StatementCategorySection(
                title="支出分类",
                emptyText="这个周期还没有支出",
                groups=summary.categoryExpenses,
                total=summary.expenseCents,
                rows=summary.rows,
                categories=categories,
            )
            Spacer(Modifier.height(16.dp))
            StatementCategorySection(
                title="收入分类",
                emptyText="这个周期还没有收入",
                groups=incomeGroups,
                total=summary.incomeCents,
                rows=summary.rows,
                categories=categories,
            )
            Spacer(Modifier.height(20.dp))
            SectionHeader("全部流水")
        }
        if(sortedRows.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("这个周期还没有账目",color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            itemsIndexed(sortedRows,key={_,row->row.id}) { index,row ->
                Surface(Modifier.fillMaxWidth().clickable{onEdit(row)},color=androidx.compose.ui.graphics.Color.Transparent) {
                    TransactionCompact(row)
                }
                if(index<sortedRows.lastIndex)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
            }
        }
    }
}

@Composable
private fun StatementMetric(label:String,amount:Long,color:androidx.compose.ui.graphics.Color,modifier:Modifier) {
    val density=LocalDensity.current
    Column(modifier) {
        Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            money(amount),
            color=color,
            fontFamily=FontFamily.Monospace,
            fontWeight=FontWeight.Bold,
            maxLines=1,
            softWrap=false,
            autoSize=TextAutoSize.StepBased(
                minFontSize=with(density){10.dp.toSp()},
                maxFontSize=20.sp,
                stepSize=.5.sp,
            ),
        )
    }
}

@Composable
private fun StatementCategorySection(
    title:String,
    emptyText:String,
    groups:List<Pair<Long,Long>>,
    total:Long,
    rows:List<TransactionRow>,
    categories:List<CategoryEntity>,
) {
    SectionHeader(title)
    GlassCard(Modifier.fillMaxWidth()) {
        if(groups.isEmpty())Text(emptyText,color=MaterialTheme.colorScheme.onSurfaceVariant)
        groups.forEach{(categoryId,amount)->
            val category=categories.find{it.id==categoryId}
            val source=rows.firstOrNull{it.categoryId==categoryId}
            val name=category?.name?:source?.categoryName?:"已停用分类"
            val color=category?.color?:source?.categoryColor?:0xFF778899
            val pct=if(total==0L)0f else amount.toFloat()/total
            Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(androidx.compose.ui.graphics.Color(color),RoundedCornerShape(50)))
                    Text(name,Modifier.weight(1f).padding(start=9.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("${(pct*100).roundToInt()}%  ${money(amount)}",fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium)
                }
                LinearProgressIndicator(
                    progress={pct.coerceIn(0f,1f)},
                    modifier=Modifier.fillMaxWidth().padding(top=7.dp),
                    color=androidx.compose.ui.graphics.Color(color),
                    trackColor=MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun StatisticsCategoryDetails(details:List<TransactionRow>,onEdit:(TransactionRow)->Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=520.dp)) {
        itemsIndexed(details,key={_,row->row.id}) { index,row ->
            Surface(Modifier.fillMaxWidth().clickable{onEdit(row)},color=androidx.compose.ui.graphics.Color.Transparent){TransactionCompact(row)}
            if(index<details.lastIndex)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
        }
    }
}

@Composable
private fun AssetTrendChart(accounts:List<AccountEntity>,transactions:List<TransactionRow>,transfers:List<TransferRow>,adjustments:List<AccountAdjustmentRow>,start:LocalDate,end:LocalDate){
    val zone=ZoneId.systemDefault();val dates=generateSequence(start){if(it<end)it.plusDays(1)else null}.toList();val values=dates.map{d->val at=d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1;totalAssets(accounts,transactions,transfers,adjustments,at)}
    val min=values.minOrNull()?:0;val max=values.maxOrNull()?:1;val span=(max-min).coerceAtLeast(1);val line=MaterialTheme.colorScheme.primary;val grid=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f)
    Canvas(Modifier.fillMaxWidth().height(110.dp)){repeat(3){i->val y=size.height*i/2;drawLine(grid,Offset(0f,y),Offset(size.width,y),1.dp.toPx())};if(values.isNotEmpty()){val path=Path();values.forEachIndexed{i,v->val x=if(values.size==1)size.width/2 else size.width*i/(values.size-1);val y=size.height-((v-min).toFloat()/span)*size.height*.82f-size.height*.08f;if(i==0)path.moveTo(x,y)else path.lineTo(x,y);if(values.size<16)drawCircle(line,3.dp.toPx(),Offset(x,y))};drawPath(path,line,style=Stroke(2.5.dp.toPx()))}}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(start.toString(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(end.toString(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}

@Composable
private fun TrendChart(rows:List<com.liujii.ledger.data.TransactionRow>,start:LocalDate,end:LocalDate){
    val zone=ZoneId.systemDefault()
    val series=remember(rows,start,end,zone){statisticsChartSeries(rows,start,end,zone)}
    StatisticsTrendChart(series)
}

@Composable
internal fun StatisticsTrendChart(series:StatisticsChartSeries){
    val dates=series.dates
    val inc=series.income
    val exp=series.expense
    val max=series.maxAmount
    val gridColor=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f)
    val cursorColor=MaterialTheme.colorScheme.onSurface.copy(alpha=.72f)
    // 游标每帧变化只绘制覆盖层；线段路径仅随数据、颜色或画布尺寸重建。
    val cachedLines=remember(series,gridColor){Modifier.drawWithCache{
        fun path(values:List<Long>)=Path().apply{values.forEachIndexed{i,v->
            val x=if(dates.size==1)size.width/2 else size.width*i/(dates.size-1).coerceAtLeast(1)
            val y=size.height-(v.toFloat()/max)*size.height*.9f
            if(i==0)moveTo(x,y)else lineTo(x,y)
        }}
        val incomePath=path(inc);val expensePath=path(exp)
        onDrawBehind{
            repeat(4){i->val y=size.height*i/3;drawLine(gridColor,Offset(0f,y),Offset(size.width,y),1.dp.toPx())}
            drawPath(incomePath,Mint,style=Stroke(2.5.dp.toPx()));drawPath(expensePath,Coral,style=Stroke(2.5.dp.toPx()))
            val radius=if(dates.size<16)3.dp.toPx() else 1.5.dp.toPx()
            inc.forEachIndexed{i,v->drawCircle(Mint,radius,Offset(if(dates.size==1)size.width/2 else size.width*i/(dates.size-1).coerceAtLeast(1),size.height-v.toFloat()/max*size.height*.9f))}
            exp.forEachIndexed{i,v->drawCircle(Coral,radius,Offset(if(dates.size==1)size.width/2 else size.width*i/(dates.size-1).coerceAtLeast(1),size.height-v.toFloat()/max*size.height*.9f))}
        }
    }}
    var selectedIndex by remember(series) { mutableStateOf<Int?>(null) }
    var accessibilityIndex by remember(series) { mutableIntStateOf(0) }
    val summaryIndex=(selectedIndex?:accessibilityIndex).takeIf{it in dates.indices}
    BoxWithConstraints(
        Modifier.fillMaxWidth().heightIn(min=232.dp).semantics {
            contentDescription=if(summaryIndex==null)"收支折线图，暂无数据" else "收支折线图。${dates[summaryIndex]}，收入${money(inc[summaryIndex])}，支出${money(exp[summaryIndex])}"
            customActions=if(dates.isEmpty())emptyList() else listOf(CustomAccessibilityAction("前一天"){accessibilityIndex=(accessibilityIndex-1).coerceAtLeast(0);selectedIndex=accessibilityIndex;true},CustomAccessibilityAction("后一天"){accessibilityIndex=(accessibilityIndex+1).coerceAtMost(dates.lastIndex);selectedIndex=accessibilityIndex;true})
        }.pointerInput(series) {
            fun selectAt(x:Float){selectedIndex=chartIndexAt(x,size.width.toFloat(),dates.size)}
            awaitEachGesture {
                try {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    selectAt(down.position.x)
                    var axis=ChartGestureAxis.UNDECIDED
                    while(true) {
                        val event=awaitPointerEvent()
                        // 只跟踪首次按下的手指，抬起后不把选择交给另一根手指。
                        val change=event.changes.firstOrNull{it.id==down.id}?:break
                        if(!change.pressed||change.isConsumed)break
                        axis=chartAxisAfterSlop(axis,change.position.x-down.position.x,change.position.y-down.position.y,viewConfiguration.touchSlop)
                        when(axis) {
                            ChartGestureAxis.VERTICAL->selectedIndex=null
                            ChartGestureAxis.HORIZONTAL->{selectAt(change.position.x);change.consume()}
                            ChartGestureAxis.UNDECIDED->selectAt(change.position.x)
                        }
                    }
                } finally {
                    selectedIndex=null
                }
            }
        }
    ){
        Box(Modifier.fillMaxWidth().height(180.dp).align(Alignment.BottomCenter)){
        Spacer(Modifier.matchParentSize().graphicsLayer().then(cachedLines))
        Canvas(Modifier.matchParentSize()){
            fun point(index:Int,value:Long)=Offset(if(dates.size==1)size.width/2 else size.width*index/dates.lastIndex,size.height-(value.toFloat()/max)*size.height*.9f)
            selectedIndex?.let{i->val incomePoint=point(i,inc[i]);val expensePoint=point(i,exp[i]);drawLine(cursorColor,Offset(incomePoint.x,0f),Offset(incomePoint.x,size.height),1.5.dp.toPx());drawCircle(Mint,5.dp.toPx(),incomePoint);drawCircle(Coral,5.dp.toPx(),expensePoint)}
        }
        }
        selectedIndex?.let{i->
            val tooltipWidth=(170*androidx.compose.ui.platform.LocalDensity.current.fontScale).dp.coerceAtMost(maxWidth)
            val fraction=if(dates.size<=1).5f else i.toFloat()/dates.lastIndex
            val x=(maxWidth*fraction-tooltipWidth/2).coerceIn(0.dp,(maxWidth-tooltipWidth).coerceAtLeast(0.dp))
            Surface(Modifier.offset(x=x).width(tooltipWidth),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.97f),shadowElevation=5.dp){
                Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                    Text(dates[i].toString(),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
                    Text("收入 ${money(inc[i])}",color=MaterialTheme.colorScheme.secondary,fontWeight=FontWeight.Medium)
                    Text("支出 ${money(exp[i])}",color=MaterialTheme.colorScheme.tertiary,fontWeight=FontWeight.Medium)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){Text("● 收入",color=MaterialTheme.colorScheme.secondary);Spacer(Modifier.width(18.dp));Text("● 支出",color=MaterialTheme.colorScheme.tertiary)}
}
