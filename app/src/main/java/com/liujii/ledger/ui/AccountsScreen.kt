package com.liujii.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.liujii.ledger.LedgerViewModel
import com.liujii.ledger.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun AccountType.label(): String = when(this) {
    AccountType.CASH -> "现金"
    AccountType.BANK_CARD -> "银行卡"
    AccountType.EWALLET -> "电子钱包"
    AccountType.OTHER -> "其他"
}

@Composable
fun AccountsScreen(vm: LedgerViewModel, onBack: () -> Unit, onTransfer: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val transactions by vm.transactions.collectAsState()
    val transfers by vm.transfers.collectAsState()
    val adjustments by vm.adjustments.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var calibrating by remember { mutableStateOf<AccountEntity?>(null) }
    var selected by remember { mutableStateOf<AccountEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val ordered = remember(accounts) { accounts.toMutableStateList() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 38.dp.toPx() }
    val total = totalAssets(accounts, transactions, transfers, adjustments)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=12.dp,bottom=32.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回") }
            Text("我的资产",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f))
            TextButton(onClick={showNew=true}) { Text("新增账户") }
        }
        Text("钱在哪里，一目了然",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(start=48.dp,bottom=18.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text("总资产",color=MaterialTheme.colorScheme.onSurfaceVariant)
            AmountText(total,large=true)
            Text("仅统计已选择“计入总资产”的账户",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("账户",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
            OutlinedButton(onClick=onTransfer,enabled=accounts.size>=2) { Icon(Icons.Default.SwapHoriz,null);Text(" 转账") }
        }
        Text("点账户查看流水；按住右侧手柄拖动排序。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=8.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            ordered.forEachIndexed { index, account ->
                key(account.id) {
                    val balance = accountBalance(account,transactions,transfers,adjustments)
                    Row(Modifier.fillMaxWidth().clickable{selected=account}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(Color(account.color).copy(alpha=.16f),CircleShape),contentAlignment=Alignment.Center) {
                            Icon(when(account.type){AccountType.CASH->Icons.Default.Payments;AccountType.BANK_CARD->Icons.Default.CreditCard;AccountType.EWALLET->Icons.Default.AccountBalanceWallet;AccountType.OTHER->Icons.Default.Savings},null,tint=Color(account.color))
                        }
                        Column(Modifier.padding(start=12.dp).weight(1f)) { Text(account.name);Text(account.type.label(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(money(balance),color=if(balance>=0)MaterialTheme.colorScheme.onSurface else Coral)
                        IconButton(onClick={editing=account}) { Icon(Icons.Default.Edit,"编辑${account.name}") }
                        Box(Modifier.size(44.dp).pointerInput(account.id,ordered.size){
                            detectDragGesturesAfterLongPress(
                                onDragStart={draggingId=account.id;dragDistance=0f},
                                onDragCancel={draggingId=null;dragDistance=0f},
                                onDragEnd={vm.reorderAccounts(ordered.map{it.id});draggingId=null;dragDistance=0f},
                                onDrag={change,amount->change.consume();dragDistance+=amount.y;var current=ordered.indexOfFirst{it.id==account.id};while(dragDistance>threshold&&current<ordered.lastIndex){ordered.add(current+1,ordered.removeAt(current));dragDistance-=threshold;current++};while(dragDistance< -threshold&&current>0){ordered.add(current-1,ordered.removeAt(current));dragDistance+=threshold;current--}}
                            )
                        },contentAlignment=Alignment.Center) { Icon(Icons.Default.DragHandle,"拖动${account.name}排序",tint=if(draggingId==account.id)Mint else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if(index<ordered.lastIndex)HorizontalDivider(Modifier.padding(start=54.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
                }
            }
        }
    }

    if(showNew) AccountDialog(vm,null,onDismiss={showNew=false})
    editing?.let { account -> AccountDialog(vm,account,onDismiss={editing=null},onDeactivate={vm.deactivateAccount(account){error->message=error?:"账户已停用"};editing=null}) }
    calibrating?.let { account -> CalibrateDialog(vm,account){calibrating=null} }
    selected?.let { account -> AccountDetailSheet(account,transactions,transfers,adjustments,onDismiss={selected=null},onCalibrate={selected=null;calibrating=account}) }
    message?.let { AlertDialog(onDismissRequest={message=null},confirmButton={TextButton({message=null}){Text("知道了")}},title={Text(if(it.contains("已停用"))"操作完成" else "暂时无法停用")},text={Text(it)}) }
}

@Composable
private fun AccountDialog(vm:LedgerViewModel,existing:AccountEntity?,onDismiss:()->Unit,onDeactivate:(()->Unit)?=null) {
    var name by remember{mutableStateOf(existing?.name?:"")};var type by remember{mutableStateOf(existing?.type?:AccountType.CASH)}
    var opening by remember{mutableStateOf(existing?.let{"%.2f".format(it.openingBalanceCents/100.0)}?:"0.00")};var include by remember{mutableStateOf(existing?.includeInTotal?:true)};var color by remember{mutableLongStateOf(canonicalMarkerColor(existing?.color?:0xFF4D8BB8))}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing==null)"新增账户" else "编辑账户")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        OutlinedTextField(name,{name=it.take(12)},label={Text("账户名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("账户类型",Modifier.padding(top=12.dp));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){AccountType.entries.forEach{t->FilterChip(type==t,{type=t},{Text(t.label())})}}
        OutlinedTextField(opening,{opening=it.filter{x->x.isDigit()||x=='.'||x=='-'}.take(14)},label={Text("期初余额")},prefix={Text("¥ ")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=10.dp))
        Text("标识颜色",Modifier.padding(top=12.dp,bottom=8.dp));MarkerColorPicker(color){color=it}
        Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically){Text("计入总资产",Modifier.weight(1f));Switch(include,{include=it})}
        if(onDeactivate!=null)TextButton(onClick=onDeactivate){Text("停用账户",color=Coral)}
    }},confirmButton={TextButton({vm.saveAccount(existing?.id?:0,name,type,opening,color,include,onDismiss)},enabled=name.isNotBlank()&&opening.toBigDecimalOrNull()!=null){Text("保存")}},dismissButton={TextButton(onDismiss){Text("取消")}})
}

private data class AccountMovement(val at:Long,val title:String,val subtitle:String,val amount:Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDetailSheet(account:AccountEntity,transactions:List<TransactionRow>,transfers:List<TransferRow>,adjustments:List<AccountAdjustmentRow>,onDismiss:()->Unit,onCalibrate:()->Unit) {
    val balance=accountBalance(account,transactions,transfers,adjustments)
    val movements=(transactions.filter{it.accountId==account.id}.map{AccountMovement(it.occurredAt,it.note.ifBlank{it.categoryName},it.categoryName,if(it.type==MoneyType.INCOME)it.amountCents else -it.amountCents)}+
        transfers.filter{it.fromAccountId==account.id||it.toAccountId==account.id}.map{val incoming=it.toAccountId==account.id;AccountMovement(it.occurredAt,it.note.ifBlank{"账户转账"},if(incoming)"来自 ${it.fromAccountName}" else "转至 ${it.toAccountName}",if(incoming)it.amountCents else -it.amountCents)}+
        adjustments.filter{it.accountId==account.id}.map{AccountMovement(it.occurredAt,it.note,"余额校准",it.deltaCents)}).sortedByDescending{it.at}
    ModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.fillMaxWidth().padding(horizontal=22.dp).padding(bottom=28.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(account.name,style=MaterialTheme.typography.headlineSmall);Text(account.type.label(),color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton(onCalibrate){Text("校准余额")}}
        Text("当前余额",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=14.dp));AmountText(balance,large=true)
        HorizontalDivider(Modifier.padding(vertical=14.dp));Text("账户流水",style=MaterialTheme.typography.titleMedium)
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())){if(movements.isEmpty())Text("还没有流水",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=18.dp));movements.forEach{m->Row(Modifier.fillMaxWidth().padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(m.title);Text("${m.subtitle} · ${dateText(m.at)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text((if(m.amount>0)"+" else "")+money(m.amount),color=if(m.amount>=0)Mint else Coral)}}}
    }}
}

@Composable private fun CalibrateDialog(vm:LedgerViewModel,account:AccountEntity,onDismiss:()->Unit){var target by remember{mutableStateOf("")};var note by remember{mutableStateOf("余额校准")};AlertDialog(onDismissRequest=onDismiss,title={Text("校准${account.name}余额")},text={Column{Text("输入现在实际余额，差额会单独记录，不计入收支。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);OutlinedTextField(target,{target=it.filter{x->x.isDigit()||x=='.'||x=='-'}},label={Text("实际余额")},prefix={Text("¥ ")},modifier=Modifier.fillMaxWidth().padding(top=10.dp));OutlinedTextField(note,{note=it.take(30)},label={Text("备注")},modifier=Modifier.fillMaxWidth().padding(top=8.dp))}},confirmButton={TextButton({vm.calibrateAccount(account,target,note,onDismiss)},enabled=target.toBigDecimalOrNull()!=null){Text("保存校准")}},dismissButton={TextButton(onDismiss){Text("取消")}})}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferEditor(vm:LedgerViewModel,onDismiss:()->Unit) {
    val accounts by vm.accounts.collectAsState();var from by remember(accounts){mutableLongStateOf(accounts.firstOrNull()?.id?:0)};var to by remember(accounts,from){mutableLongStateOf(accounts.firstOrNull{it.id!=from}?.id?:0)};var amount by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var date by remember{mutableStateOf(LocalDate.now())};var datePicker by remember{mutableStateOf(false)}
    val valid=accounts.size>=2&&from>0&&to>0&&from!=to&&amount.toBigDecimalOrNull()?.let{it>java.math.BigDecimal.ZERO}==true
    ModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=24.dp)){
        Text("账户转账",style=MaterialTheme.typography.headlineSmall);Text("只改变账户余额，不计入收入或支出。",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp,bottom=14.dp))
        OutlinedTextField(amount,{amount=it.filter{x->x.isDigit()||x=='.'}.take(12)},label={Text("金额")},prefix={Text("¥ ")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("从账户",Modifier.padding(top=14.dp));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){accounts.forEach{a->FilterChip(from==a.id,{from=a.id;if(to==a.id)to=accounts.firstOrNull{it.id!=a.id}?.id?:0},{Text(a.name)})}}
        Text("到账户",Modifier.padding(top=12.dp));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){accounts.filter{it.id!=from}.forEach{a->FilterChip(to==a.id,{to=a.id},{Text(a.name)})}}
        OutlinedTextField(note,{note=it.take(80)},label={Text("备注（可选）")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=12.dp));OutlinedButton({datePicker=true},Modifier.fillMaxWidth().padding(top=12.dp)){Text("日期：$date")};Button({vm.saveTransfer(fromAccountId=from,toAccountId=to,amount=amount,date=date,note=note,after=onDismiss)},enabled=valid,modifier=Modifier.fillMaxWidth().padding(top=16.dp).height(52.dp)){Text("确认转账")}
    }}
    if(datePicker){val state=rememberDatePickerState(initialSelectedDateMillis=date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli());DatePickerDialog(onDismissRequest={datePicker=false},confirmButton={TextButton({state.selectedDateMillis?.let{date=Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()};datePicker=false}){Text("确定")}},dismissButton={TextButton({datePicker=false}){Text("取消")}}){DatePicker(state)}}
}
