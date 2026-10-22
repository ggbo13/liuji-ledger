package com.liujii.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liujii.ledger.LedgerViewModel
import com.liujii.ledger.data.MoneyType
import com.liujii.ledger.data.TransactionRow
import com.liujii.ledger.data.TransferRow
import com.liujii.ledger.data.accountBalance
import com.liujii.ledger.data.totalAssets
import com.liujii.ledger.data.budgetPace
import com.liujii.ledger.data.BudgetPace
import com.liujii.ledger.data.statisticsSummary
import com.liujii.ledger.data.statisticsChartSeries
import com.liujii.ledger.data.isEffective
import com.liujii.ledger.monthRange
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun HomeScreen(vm: LedgerViewModel, onSeeAll: () -> Unit, onManageBudget: () -> Unit, onManageAccounts: () -> Unit) {
    val all by vm.transactions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val transfers by vm.transfers.collectAsState()
    val adjustments by vm.adjustments.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val recurring by vm.recurring.collectAsState()
    var editingRow by remember { mutableStateOf<TransactionRow?>(null) }
    var postingMessage by remember { mutableStateOf<String?>(null) }
    val today = rememberCurrentDate()
    LaunchedEffect(all.isNotEmpty(),today){vm.refreshBackupStatus(all.isNotEmpty())}
    val zone = ZoneId.systemDefault()
    val yearMonth = YearMonth.from(today)
    val range = remember(yearMonth, zone) { monthRange(today) }
    val summary = remember(all, range, zone) { statisticsSummary(all, yearMonth.atDay(1), yearMonth.atEndOfMonth(), zone) }
    val month = summary.rows
    val income = summary.incomeCents
    val expense = summary.expenseCents
    val trail = remember(month, today, zone) {
        if (month.isEmpty()) emptyList() else {
            val series = statisticsChartSeries(month, today.withDayOfMonth(1), today, zone)
            series.income.zip(series.expense) { inc, exp -> inc - exp }
        }
    }
    val budget = remember(budgets, yearMonth) { budgets.lastOrNull { it.yearMonth==yearMonth.toString() && it.categoryId==null } }
    val upcoming = remember(recurring, today, zone) { recurring.filter { it.isEffective() && dateOf(it.nextAt)<=today.plusDays(7) }.sortedBy { it.nextAt }.take(3) }
    val recent = remember(all, transfers) { (all.map{RecentMovement(it.occurredAt,it,null)}+transfers.map{RecentMovement(it.occurredAt,null,it)}).sortedByDescending{it.at}.take(5) }
    ScreenColumn("流迹", "${today.monthValue} 月 · 每一笔都有去处") {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("本月结余", color=MaterialTheme.colorScheme.onSurfaceVariant)
            AmountText(income-expense, large=true)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                SummaryItem("收入", income, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                SummaryItem("支出", expense, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            CashTrail(trail)
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("账本","管理",onManageAccounts)
        Column(Modifier.fillMaxWidth().padding(vertical=6.dp)) {
            Text("把生活、工作和特定项目分开记录",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            accounts.take(4).forEach{book->Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(9.dp).background(Color(book.color),CircleShape));Text(book.name,Modifier.padding(start=9.dp).weight(1f));Text("账本",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            if(accounts.isEmpty())Text("还没有可用账本",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("本月预算", "管理", onManageBudget)
        GlassCard(Modifier.fillMaxWidth()) {
            if(budget == null) Text("尚未设置总预算，可在设置中添加", color=MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                val progress = (expense.toFloat()/budget.limitCents).coerceAtLeast(0f)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)){Text("已用",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(money(expense),fontFamily=FontFamily.Monospace)}
                    Column(Modifier.weight(1f)){Text("额度",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(money(budget.limitCents),fontFamily=FontFamily.Monospace)}
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress={progress.coerceAtMost(1f)}, Modifier.fillMaxWidth().height(8.dp), color=if(progress>1) Coral else Mint, trackColor=MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(8.dp))
                val pace=remember(expense,budget.limitCents,today){budgetPace(expense,budget.limitCents,today)}
                Text("${pace.label} · " + if(progress>1) "超出 ${money(expense-budget.limitCents)}" else "还可使用 ${money(budget.limitCents-expense)}", color=if(pace!=BudgetPace.NORMAL) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if(upcoming.isNotEmpty()) {
            Spacer(Modifier.height(24.dp)); SectionHeader("待入账与近期账单")
            GlassCard(Modifier.fillMaxWidth()) {
                upcoming.forEachIndexed { index, rule ->
                    Row(Modifier.fillMaxWidth().padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background((if(rule.type==MoneyType.INCOME) Mint else Coral).copy(alpha=.12f), CircleShape), contentAlignment=Alignment.Center) { Text(if(rule.type==MoneyType.INCOME) "入" else "支", color=if(rule.type==MoneyType.INCOME) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary) }
                        Column(Modifier.padding(start=12.dp).weight(1f)) { Text(rule.title, fontWeight=FontWeight.Medium); Text(dateText(rule.nextAt), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        val posting=rule.id in vm.postingRuleIds.value
                        TextButton(onClick={vm.postRecurring(rule,onError={postingMessage=it}){inserted->if(!inserted)postingMessage="这期账单已经入账，请刷新后查看。"}},enabled=!posting) { Icon(Icons.Default.Check, null); Text(if(posting)"处理中"else"入账") }
                    }
                    if(index<upcoming.lastIndex) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
                }
            }
        }

        Spacer(Modifier.height(24.dp)); SectionHeader("最近流水", "查看全部", onSeeAll)
        GlassCard(Modifier.fillMaxWidth()) {
            if(recent.isEmpty()) Text("还没有账目，点右下角 + 记下第一笔", color=MaterialTheme.colorScheme.onSurfaceVariant)
            recent.forEach { movement->movement.transaction?.let{row->Surface(Modifier.fillMaxWidth().clickable{editingRow=row},color=Color.Transparent){TransactionCompact(row)}};movement.transfer?.let{TransferCompact(it)} }
        }
    }
    editingRow?.let { row -> TransactionEditor(vm,row,onDismiss={editingRow=null}) }
    postingMessage?.let{AlertDialog(onDismissRequest={postingMessage=null},title={Text("账单提示")},text={Text(it)},confirmButton={TextButton(onClick={postingMessage=null}){Text("知道了")}})}
}

private data class RecentMovement(val at:Long,val transaction:TransactionRow?,val transfer:TransferRow?)

@Composable private fun SummaryItem(label:String, amount:Long, color:Color, modifier:Modifier) { Column(modifier) { Text(label, color=MaterialTheme.colorScheme.onSurfaceVariant); Text(money(amount), color=color, fontWeight=FontWeight.Bold) } }

@Composable
private fun CashTrail(values: List<Long>) {
    val mint = Mint
    val coral = Coral
    val trendColor = MaterialTheme.colorScheme.onSurfaceVariant
    val max = remember(values) { values.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1) ?: 1L }
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
        Text("每日净额趋势", style=MaterialTheme.typography.labelLarge, fontWeight=FontWeight.SemiBold, modifier=Modifier.weight(1f))
        Text("收入 − 支出", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
    if(values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment=Alignment.Center) {
            Text("暂无每日收支数据", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val path = Path()
            values.forEachIndexed { i, value ->
                val x = if(values.size == 1) size.width/2 else size.width*i/(values.size-1)
                val y = size.height/2 - (value.toFloat()/max)*size.height*.34f
                if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
                drawCircle(if(value>=0) mint else coral, 4.dp.toPx(), Offset(x,y))
            }
            if(values.size>1) drawPath(path, color=trendColor.copy(alpha=.8f), style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            drawLine(trendColor.copy(alpha=.25f), Offset(0f,size.height/2), Offset(size.width,size.height/2), 1.dp.toPx())
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
        Text("● 收入较多", color=MaterialTheme.colorScheme.secondary, style=MaterialTheme.typography.labelSmall)
        Text("● 支出较多", color=MaterialTheme.colorScheme.tertiary, style=MaterialTheme.typography.labelSmall)
        Text("— 收支平衡线", color=trendColor, style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun TransactionCompact(t: com.liujii.ledger.data.TransactionRow, trailing: (@Composable () -> Unit)? = null) {
    val density=LocalDensity.current
    val amount=money(if(t.type==MoneyType.EXPENSE)-t.amountCents else t.amountCents,t.type==MoneyType.INCOME)
    val amountStyle=MaterialTheme.typography.titleMedium.copy(fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold)
    val textMeasurer=rememberTextMeasurer()
    val amountWidth=remember(amount,amountStyle,density){textMeasurer.measure(amount,amountStyle,maxLines=1,softWrap=false).size.width}
    BoxWithConstraints(Modifier.fillMaxWidth().padding(end=16.dp)) {
    val stacked=density.fontScale>=1.5f||with(density){amountWidth.toDp()}>maxWidth*.46f
    val amountContent:@Composable ()->Unit={
        Text(amount,color=if(t.type==MoneyType.INCOME)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,style=amountStyle,softWrap=true,overflow=TextOverflow.Visible,textAlign=androidx.compose.ui.text.style.TextAlign.End)
    }
    Row(Modifier.fillMaxWidth().padding(vertical=9.dp), verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.width(5.dp).height(36.dp).background(Color(t.categoryColor),CircleShape))
        Column(Modifier.weight(1f).padding(start=12.dp,end=if(stacked)0.dp else 12.dp)) {
            Text(t.note.ifBlank { t.categoryName },maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("${t.categoryName} · ${t.paymentMethodName} · ${dateText(t.occurredAt)}",maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            // 大字体或大金额使用完整宽度，避免把名称挤掉或把金额截断。
            if(stacked)Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.End){amountContent()}
        }
        if(!stacked)amountContent()
        trailing?.invoke()
    }
    }
}

@Composable
fun TransferCompact(t: TransferRow, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(end=16.dp).padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=.13f),CircleShape),contentAlignment=Alignment.Center){Icon(Icons.AutoMirrored.Filled.ArrowForward,null,tint=MaterialTheme.colorScheme.primary)}
        Column(Modifier.padding(start=12.dp).weight(1f)){Text(t.note.ifBlank{"账户转账"},maxLines=1);Text("${t.fromAccountName} → ${t.toAccountName} · ${dateText(t.occurredAt)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Text(money(t.amountCents),fontWeight=FontWeight.Bold)
        trailing?.invoke()
    }
}
