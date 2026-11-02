package com.liujii.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liujii.ledger.LedgerViewModel
import com.liujii.ledger.data.*
import java.time.YearMonth
import java.time.ZoneId
import java.time.LocalDate

@Composable
fun HomeScreen(vm: LedgerViewModel, onSeeAll: () -> Unit, onManageBudget: () -> Unit,
    onManageAccounts: () -> Unit, onAdd: () -> Unit = {}) {
    val all by vm.transactions.collectAsState()
    val books by vm.accounts.collectAsState()
    val transfers by vm.transfers.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val recurring by vm.recurring.collectAsState()
    val today = rememberCurrentDate()
    val month = YearMonth.from(today)
    val zone = ZoneId.systemDefault()
    val summary = remember(all, month, zone) { statisticsSummary(all, month.atDay(1), month.atEndOfMonth(), zone) }
    val trail = remember(summary.rows, today, zone) {
        if (summary.rows.isEmpty()) emptyList() else statisticsChartSeries(summary.rows, month.atDay(1), today, zone)
            .let { it.income.zip(it.expense) { income, expense -> income - expense } }
    }
    val budget = remember(budgets, month) { budgets.lastOrNull { it.yearMonth == month.toString() && it.categoryId == null } }
    val upcoming = rememberHomeUpcomingRules(recurring, today, zone)
    val recent = remember(all, transfers) {
        (all.map { it.occurredAt to it } + transfers.map { it.occurredAt to it }).sortedByDescending { it.first }.take(5)
    }
    var editing by remember { mutableStateOf<TransactionRow?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val postingIds by vm.postingRuleIds
    LaunchedEffect(all.isNotEmpty(), today) { vm.refreshBackupStatus(all.isNotEmpty()) }
    LedgerHomeContent(today,summary,trail,budget,recent,books.size,upcoming,postingIds,
        onSeeAll,onManageBudget,onManageAccounts,onAdd,onEdit={editing=it},onPostRule={rule->
            vm.postRecurring(rule,onError={message=it}){inserted->if(!inserted)message="这期账单已经入账，请刷新后查看。"}
        })
    editing?.let { row -> TransactionEditor(vm, row, onDismiss = { editing = null }) }
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("账单提示") }, text = { Text(it) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("知道了") } }) }
}

@Composable
internal fun rememberHomeUpcomingRules(rules:List<RecurringRuleEntity>,today:LocalDate,zone:ZoneId):List<RecurringRuleEntity> =
    remember(rules,today,zone) {
        rules.filter { it.isEffective() && java.time.Instant.ofEpochMilli(it.nextAt).atZone(zone).toLocalDate() <= today.plusDays(7) }
            .sortedBy { it.nextAt }.take(3)
    }

/** 纯展示层也用于合成截图测试，不需要接触用户数据库。 */
@Composable
internal fun LedgerHomeContent(today:LocalDate,summary:StatisticsSummary,trail:List<Long>,budget:BudgetEntity?,
    recent:List<Pair<Long,Any>>,bookCount:Int,upcoming:List<RecurringRuleEntity>,postingIds:Set<Long>,
    onSeeAll:()->Unit,onManageBudget:()->Unit,onManageAccounts:()->Unit,onAdd:()->Unit,
    onEdit:(TransactionRow)->Unit,onPostRule:(RecurringRuleEntity)->Unit) {
    val month=YearMonth.from(today)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 32.dp)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("流迹",Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                TextButton(onClick=onManageAccounts,contentPadding=PaddingValues(horizontal=8.dp),
                    modifier=Modifier.heightIn(min=48.dp).semantics{contentDescription="管理账本，已启用$bookCount 个"}) {
                    Icon(Icons.Default.Book,null,Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("账本",maxLines=1)
                }
                FilledIconButton(onAdd,Modifier.size(48.dp),shape=CircleShape) { Icon(Icons.Default.Add,"记一笔") }
            }
            LedgerPaper(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 10.dp, bottomStart = 3.dp, bottomEnd = 3.dp)) {
                        Text("${month.monthValue} 月账页", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(month.year.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    // 淡纹只负责绘制，不参加摘要的高度测量。
                    Box(Modifier.matchParentSize(),contentAlignment=Alignment.CenterEnd) {
                        Text("¥", Modifier.clearAndSetSemantics {}, color = MaterialTheme.colorScheme.primary.copy(alpha = .045f),
                            fontSize = 84.sp, fontWeight = FontWeight.Light)
                    }
                    Column {
                        Text("本月结余", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(summary.incomeCents - summary.expenseCents), style = MaterialTheme.typography.headlineLarge,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                HomeIncomeExpense(summary.incomeCents, summary.expenseCents)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                Spacer(Modifier.height(8.dp))
                CashTrail(trail, compact = true)
            }
            Spacer(Modifier.height(12.dp))
            SectionHeader("本月预算", "管理", onManageBudget)
            if (budget == null) {
                Text("尚未设置总预算",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val remaining = budget.limitCents - summary.expenseCents
                val pace = remember(summary.expenseCents, budget.limitCents, today) { budgetPace(summary.expenseCents, budget.limitCents, today) }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (remaining < 0) "已超出 ${money(-remaining)}" else "还可使用 ${money(remaining)}", fontWeight = FontWeight.Medium)
                    Text(pace.label, style = MaterialTheme.typography.bodySmall,
                        color = if (pace == BudgetPace.NORMAL) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary)
                }
                BudgetTicks(summary.expenseCents, budget.limitCents, Modifier.padding(top = 10.dp, bottom = 6.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("已用 ${money(summary.expenseCents)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("额度 ${money(budget.limitCents)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionHeader("最近流水", "查看全部", onSeeAll)
        }
        if (recent.isEmpty()) Text("还没有账目，点右上角 + 记下第一笔", Modifier.padding(horizontal = 20.dp, vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        recent.forEachIndexed { index, (_, movement) ->
            val at = when (movement) { is TransactionRow -> movement.occurredAt; is TransferRow -> movement.occurredAt; else -> 0L }
            val previousAt = recent.getOrNull(index - 1)?.first
            if (previousAt == null || dateOf(previousAt) != dateOf(at)) {
                Text(dateText(at), Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f))
                    .padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .88f)) {
                Column(Modifier.fillMaxWidth().then(if (movement is TransactionRow) Modifier.clickable { onEdit(movement) } else Modifier).padding(start = 20.dp)) {
                    when (movement) { is TransactionRow -> TransactionCompact(movement); is TransferRow -> TransferCompact(movement) }
                    HorizontalDivider(Modifier.padding(end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                }
            }
        }
        if (upcoming.isNotEmpty()) Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            SectionHeader("待入账与近期账单")
            GlassCard(Modifier.fillMaxWidth()) {
                upcoming.forEach { rule ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.title, fontWeight = FontWeight.Medium)
                            Text(dateText(rule.nextAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val posting = rule.id in postingIds
                        TextButton(onClick = { onPostRule(rule) }, enabled = !posting) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Text(if (posting) "处理中" else "入账")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeIncomeExpense(income: Long, expense: Long) {
    val stacked = LocalDensity.current.fontScale >= 1.5f
    val incomePart: @Composable () -> Unit = { HomeAmount("收入", income, MaterialTheme.colorScheme.secondary) }
    val expensePart: @Composable () -> Unit = { HomeAmount("支出", expense, MaterialTheme.colorScheme.tertiary) }
    if (stacked) Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { incomePart(); expensePart() }
    else Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) { incomePart() }
        Box(Modifier.width(1.dp).height(45.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)))
        Column(Modifier.weight(1f)) { expensePart() }
    }
}

@Composable
private fun HomeAmount(label: String, amount: Long, color: Color) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(money(amount), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = color)
}
