package com.liujii.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.liujii.ledger.LedgerViewModel
import com.liujii.ledger.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.activity.compose.BackHandler
import java.time.format.DateTimeFormatter

private sealed interface DetailEntry { val at:Long
    data class Transaction(val row:TransactionRow):DetailEntry{override val at=row.occurredAt}
    data class Transfer(val row:TransferRow):DetailEntry{override val at=row.occurredAt}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(vm: LedgerViewModel, onAddTransaction: () -> Unit) {
    val all by vm.transactions.collectAsState()
    val transfers by vm.transfers.collectAsState()
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val paymentMethods by vm.paymentMethods.collectAsState()
    var search by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<MoneyType?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var paymentMethodFilterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dateFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var dateEnd by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var showDateFilter by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<TransactionRow?>(null) }
    var deleting by remember { mutableStateOf<TransactionRow?>(null) }
    var deletingTransfer by remember { mutableStateOf<TransferRow?>(null) }
    var transferError by remember { mutableStateOf<String?>(null) }
    var openSwipeId by remember { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val fileOperationInProgress by vm.fileOperationInProgress
    var choosingCsvFile by rememberSaveable { mutableStateOf(false) }
    var csvFilter by rememberSaveable { mutableStateOf(CsvTransactionFilter()) }
    val today=rememberCurrentDate()
    val zone=ZoneId.systemDefault()
    val listState=rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress){if(listState.isScrollInProgress)openSwipeId=null}
    val rangeStart=dateFilter?.let{LocalDate.parse(it)}
    val rangeEnd=(dateEnd?:dateFilter)?.let{LocalDate.parse(it)}
    fun matchesDate(at:Long):Boolean { val day=dateOf(at);return (rangeStart==null||day>=rangeStart)&&(rangeEnd==null||day<=rangeEnd) }
    val rows = remember(all,search,type,categoryId,accountId,paymentMethodFilterId,rangeStart,rangeEnd,zone) { all.filter {
        (search.isBlank() || it.note.contains(search,true) || it.categoryName.contains(search,true) || it.accountName.contains(search,true) || it.paymentMethodName.contains(search,true)) &&
            (type==null || it.type==type) &&
            (categoryId==null || it.categoryId==categoryId) &&
            (accountId==null || it.accountId==accountId) &&
            (paymentMethodFilterId==null || it.paymentMethodId==paymentMethodFilterId) &&
            matchesDate(it.occurredAt)
    } }
    val transferRows=remember(transfers,type,categoryId,paymentMethodFilterId,search,accountId,rangeStart,rangeEnd,zone){transfers.filter{t->type==null&&categoryId==null&&paymentMethodFilterId==null&&(search.isBlank()||t.note.contains(search,true)||t.fromAccountName.contains(search,true)||t.toAccountName.contains(search,true))&&(accountId==null||t.fromAccountId==accountId||t.toAccountId==accountId)&&matchesDate(t.occurredAt)}}
    val entries=remember(rows,transferRows){(rows.map{DetailEntry.Transaction(it)}+transferRows.map{DetailEntry.Transfer(it)}).sortedByDescending{it.at}}
    val dailyEntries=remember(entries,zone){entries.groupBy{dateOf(it.at)}}
    val net=remember(rows){rows.sumOf{if(it.type==MoneyType.INCOME)it.amountCents else -it.amountCents}}
    val exportCsv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->
        try {
            if(uri!=null) {
                vm.exportFilteredCsvToUri(uri,csvFilter){error,count->scope.launch{
                    snackbarHostState.showSnackbar(error?.ifBlank{"导出失败，请重试"}?:"已导出 $count 笔收支账目")
                }}
            }
        } catch (_:Exception) {
            scope.launch{snackbarHostState.showSnackbar("导出失败，无法写入所选文件")}
        } finally {
            choosingCsvFile=false
            vm.endExternalActivity()
        }
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit){detectTapGestures{openSwipeId=null}}) {
    Column(Modifier.fillMaxSize().padding(top=22.dp)) {
        // 工具栏保留阅读边距，账目列表独立使用整屏宽度。
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("明细", style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f));IconButton(onClick={
            if(!choosingCsvFile&&!vm.fileOperationInProgress.value) {
                choosingCsvFile=true
                csvFilter=CsvTransactionFilter(search,type,categoryId,accountId,paymentMethodFilterId,dateFilter,dateEnd)
                vm.beginExternalActivity()
                try {
                    exportCsv.launch("流迹收支-$today.csv")
                } catch (_:Exception) {
                    choosingCsvFile=false
                    vm.endExternalActivity()
                    scope.launch{snackbarHostState.showSnackbar("无法打开文件保存位置，请重试")}
                }
            }
        },enabled=!fileOperationInProgress&&!choosingCsvFile){Icon(Icons.Default.IosShare,"导出筛选后的收支账目")};FilledIconButton(onClick=onAddTransaction,modifier=Modifier.size(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Mint,contentColor=Color.White)){Icon(Icons.Default.Add,"记一笔")}}
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(search, {search=it}, Modifier.fillMaxWidth(), placeholder={Text("搜索备注、分类或收支方式")}, leadingIcon={Icon(Icons.Default.Search,null)}, singleLine=true, shape=MaterialTheme.shapes.large)
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            val labels=listOfNotNull(type?.let{if(it==MoneyType.INCOME)"收入"else"支出"},dateFilter?.let{if(dateEnd!=null&&dateEnd!=it)"$it 至 $dateEnd" else it},categories.find{it.id==categoryId}?.name,accounts.find{it.id==accountId}?.name,paymentMethods.find{it.id==paymentMethodFilterId}?.name)
            Text(labels.joinToString(" · ").ifBlank{"全部收支"},modifier=Modifier.weight(1f),maxLines=2,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={openSwipeId=null;showFilters=true}){Icon(Icons.Default.FilterList,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text("筛选")}
            if(labels.isNotEmpty())TextButton(onClick={type=null;categoryId=null;accountId=null;paymentMethodFilterId=null;dateFilter=null;dateEnd=null}){Text("重置")}
        }
        Row(Modifier.fillMaxWidth().padding(bottom=6.dp)) { Text("${entries.size} 笔", color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.weight(1f)); Text("净额 ${money(net)}") }
        }
        LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(bottom=116.dp)) {
            if(entries.isEmpty()) item { Box(Modifier.fillParentMaxSize(), contentAlignment=Alignment.Center) { Text("没有符合条件的账目", color=MaterialTheme.colorScheme.onSurfaceVariant) } }
            dailyEntries.forEach { (day, dayEntries) ->
                item(key="day-$day") { DaySummaryHeader(day, dayEntries,today) }
                items(dayEntries, key={when(it){is DetailEntry.Transaction->"t${it.row.id}";is DetailEntry.Transfer->"f${it.row.id}"}}) { entry ->
                    when(entry){
                        is DetailEntry.Transaction->SwipeRevealTransaction(
                            row=entry.row,
                            expanded=openSwipeId==entry.row.id,
                            onExpandedChange={expanded->openSwipeId=if(expanded)entry.row.id else null},
                            onEdit={openSwipeId=null;edit=entry.row},
                            onDelete={openSwipeId=null;deleting=entry.row},
                        )
                        is DetailEntry.Transfer->Box(Modifier.fillMaxWidth().padding(horizontal=20.dp)){TransferCompact(entry.row){IconButton(onClick={transferError=null;deletingTransfer=entry.row}){Icon(Icons.Default.Delete,"删除转账",tint=MaterialTheme.colorScheme.onSurfaceVariant)}}}
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
                }
            }
        }
    }
    SnackbarHost(snackbarHostState,Modifier.align(Alignment.BottomCenter).padding(horizontal=20.dp).padding(bottom=92.dp))
    }
    edit?.let { TransactionEditor(vm, it, onDismiss={edit=null}) }
    deleting?.let { t -> AlertDialog(onDismissRequest={deleting=null}, title={Text("删除这笔账目？")}, text={Text("${t.note.ifBlank{t.categoryName}}\n${if(t.type==MoneyType.INCOME)"收入"else"支出"} ${money(t.amountCents)} · ${dateOf(t.occurredAt)}\n删除后可在短时间内撤销。")}, confirmButton={TextButton(onClick={
        deleting=null
        vm.deleteTransaction(t,after={
        scope.launch {
            val result=snackbarHostState.showSnackbar("已删除账目","撤销",duration=SnackbarDuration.Long)
            if(result==SnackbarResult.ActionPerformed)vm.restoreTransaction(t,onError={message->scope.launch{snackbarHostState.showSnackbar(message)}})
        }
        },onError={message->scope.launch{snackbarHostState.showSnackbar(message)}})
    }){Text("删除", color=Coral)}}, dismissButton={TextButton(onClick={deleting=null}){Text("取消")}}) }
    deletingTransfer?.let { t ->
        val processing=vm.isProcessing("transfer-delete-${t.id}")
        AlertDialog(onDismissRequest={if(!processing)deletingTransfer=null},title={Text("删除这笔转账？")},text={Column{Text("${t.fromAccountName} → ${t.toAccountName} · ${money(t.amountCents)}\n删除后两个账户的余额都会更新。");transferError?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton({transferError=null;vm.deleteTransfer(t.id){failure->transferError=failure;if(failure==null)deletingTransfer=null}},enabled=!processing){Text(if(processing)"处理中…"else"删除",color=Coral)}},dismissButton={TextButton({deletingTransfer=null},enabled=!processing){Text("取消")}})
    }
    if(showFilters)ModalBottomSheet(onDismissRequest={showFilters=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=30.dp)) {
            Text("筛选账目",style=MaterialTheme.typography.headlineSmall)
            Text("类型",Modifier.padding(top=16.dp));FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(type==null,{type=null},{Text("全部")});MoneyType.entries.forEach{entry->FilterChip(type==entry,{type=entry;categoryId=null},{Text(if(entry==MoneyType.INCOME)"收入"else"支出")})}}
            Text("日期",Modifier.padding(top=12.dp));OutlinedButton(onClick={showDateFilter=true}){Text(dateFilter?.let{"$it 至 ${dateEnd?:it}"}?:"选择日期区间")};if(dateFilter!=null)TextButton(onClick={dateFilter=null;dateEnd=null}){Text("不限日期")}
            Text("分类",Modifier.padding(top=12.dp));FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(categoryId==null,{categoryId=null},{Text("全部")});categories.filter{type==null||it.type==type}.sortedWith(compareBy<CategoryEntity>{it.sortOrder}.thenBy{it.id}).forEach{c->FilterChip(categoryId==c.id,{categoryId=c.id},{Text(c.name+if(!c.active)"（停用）"else"")})}}
            Text("账本",Modifier.padding(top=12.dp));FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(accountId==null,{accountId=null},{Text("全部")});(accounts.map{it.id to it.name}+all.map{it.accountId to it.accountName}).distinctBy{it.first}.forEach{(id,name)->FilterChip(accountId==id,{accountId=id},{Text(name)})}}
            Text("收支方式",Modifier.padding(top=12.dp));FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(paymentMethodFilterId==null,{paymentMethodFilterId=null},{Text("全部")});(paymentMethods.map{it.id to it.name}+all.map{it.paymentMethodId to it.paymentMethodName}).distinctBy{it.first}.forEach{(id,name)->FilterChip(paymentMethodFilterId==id,{paymentMethodFilterId=id},{Text(name)})}}
            Button(onClick={showFilters=false},modifier=Modifier.fillMaxWidth().padding(top=16.dp)){Text("查看结果")}
        }
    }
    if(showDateFilter) {
        val initial=(dateFilter?.let{LocalDate.parse(it)}?:LocalDate.now()).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val state=rememberDateRangePickerState(initialSelectedStartDateMillis=initial,initialSelectedEndDateMillis=(dateEnd?:dateFilter)?.let{LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()})
        DatePickerDialog(
            onDismissRequest={showDateFilter=false},
            confirmButton={TextButton(onClick={state.selectedStartDateMillis?.let{dateFilter=Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();dateEnd=Instant.ofEpochMilli(state.selectedEndDateMillis?:it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()};showDateFilter=false},enabled=state.selectedStartDateMillis!=null){Text("确定")}},
            dismissButton={TextButton(onClick={showDateFilter=false}){Text("取消")}},
        ){DateRangePicker(state,Modifier.heightIn(max=540.dp))}
    }
}

@Composable
private fun DaySummaryHeader(day: LocalDate, entries: List<DetailEntry>,today:LocalDate) {
    val rows=remember(entries){entries.mapNotNull { (it as? DetailEntry.Transaction)?.row }}
    val income=remember(rows){rows.filter { it.type==MoneyType.INCOME }.sumOf { it.amountCents }}
    val expense=remember(rows){rows.filter { it.type==MoneyType.EXPENSE }.sumOf { it.amountCents }}
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(top=14.dp,bottom=7.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(day.format(DateTimeFormatter.ofPattern(if(day.year==today.year)"M月d日 E"else"yyyy年M月d日",java.util.Locale.CHINA)),style=MaterialTheme.typography.titleSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,modifier=Modifier.weight(1f))
        Text("结余 ${money(income-expense)}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("收入 ${money(income)}  ·  支出 ${money(expense)}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SwipeRevealTransaction(
    row: TransactionRow,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val actionWidth=with(LocalDensity.current){(72f*fontScale).coerceIn(88f,136f).dp}
    val actionPx=with(LocalDensity.current){actionWidth.toPx()}
    var dragOffset by remember(row.id) { mutableFloatStateOf(0f) }
    var dragging by remember(row.id) { mutableStateOf(false) }
    val target=if(expanded)-actionPx else 0f
    val latestTarget by rememberUpdatedState(target)
    val animated by animateFloatAsState(targetValue=if(dragging)dragOffset else target,animationSpec=if(dragging)snap()else spring(stiffness=520f),label="账目左滑")
    val displayOffset=if(dragging)dragOffset else animated
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).semantics {
        customActions=listOf(
            CustomAccessibilityAction("编辑账目"){onEdit();true},
            CustomAccessibilityAction("删除账目"){onDelete();true},
        )
    }) {
        Box(Modifier.matchParentSize(),contentAlignment=Alignment.CenterEnd) {
            if(displayOffset< -1f)TextButton(onClick=onDelete,enabled=expanded,modifier=Modifier.width(actionWidth).fillMaxHeight().background(Coral),colors=ButtonDefaults.textButtonColors(contentColor=Color.White)){Text("删除")}
        }
        Surface(
            Modifier.fillMaxWidth().graphicsLayer{translationX=displayOffset}.pointerInput(row.id) {
                detectHorizontalDragGestures(
                    onDragStart={dragOffset=latestTarget;dragging=true;onExpandedChange(true)},
                    onHorizontalDrag={change,amount->change.consume();dragOffset=(dragOffset+amount).coerceIn(-actionPx,0f)},
                    onDragEnd={val shouldOpen=dragOffset < -actionPx*.38f;dragging=false;onExpandedChange(shouldOpen)},
                    onDragCancel={dragging=false;onExpandedChange(false)},
                )
            }.clickable { if(expanded)onExpandedChange(false) else onEdit() },
            color=MaterialTheme.colorScheme.surface.copy(alpha=.92f),
        ){
            // 滑动背景铺满宽度，内容留出独立边距，金额不再贴着条形边缘。
            Box(Modifier.fillMaxWidth().padding(horizontal=20.dp)){TransactionCompact(row)}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditor(vm: LedgerViewModel, existing: TransactionRow? = null, initialType: MoneyType? = null, onDismiss: () -> Unit) {
    key(existing?.id,initialType){TransactionEditorForm(vm,existing,initialType,onDismiss)}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionEditorForm(vm: LedgerViewModel, existing: TransactionRow?, initialType: MoneyType?, onDismiss: () -> Unit) {
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val paymentMethods by vm.paymentMethods.collectAsState()
    val draftKey=existing?.let { "edit-${it.id}" } ?: "new-${initialType?:MoneyType.EXPENSE}"
    val storedDraft=remember(draftKey){vm.loadDraft(draftKey)}
    val draft=remember(draftKey){initialTransactionDraft(existing!=null,storedDraft)}
    val submissionId=rememberSaveable(draftKey){java.util.UUID.randomUUID().toString()}
    val draftSession=remember(draftKey,submissionId){vm.beginDraftSession(draftKey,submissionId)}
    var draftRestored by rememberSaveable(draftKey){mutableStateOf(draft!=null)}
    var type by rememberSaveable(draftKey) { mutableStateOf(draft?.type ?: existing?.type ?: initialType ?: MoneyType.EXPENSE) }
    var amount by rememberSaveable(draftKey) { mutableStateOf(draft?.amount ?: existing?.let { java.math.BigDecimal.valueOf(it.amountCents,2).toPlainString() } ?: "") }
    var categoryId by rememberSaveable(draftKey) { mutableLongStateOf(draft?.categoryId ?: existing?.categoryId ?: 0) }
    var accountId by rememberSaveable(draftKey) { mutableLongStateOf(draft?.accountId ?: existing?.accountId ?: vm.preferredAccountId()) }
    var paymentMethodId by rememberSaveable(draftKey) { mutableLongStateOf(draft?.paymentMethodId ?: existing?.paymentMethodId ?: vm.preferredPaymentMethodId()) }
    var note by rememberSaveable(draftKey) { mutableStateOf(draft?.note ?: existing?.note ?: "") }
    var date by rememberSaveable(draftKey) { mutableStateOf(draft?.date?.let{runCatching{LocalDate.parse(it)}.getOrNull()} ?: existing?.let{dateOf(it.occurredAt)} ?: LocalDate.now()) }
    var copyMode by rememberSaveable(draftKey) { mutableStateOf(draft?.isCopy?:false) }
    var confirmClose by remember { mutableStateOf(false) }
    val submission=vm.transactionSubmission(submissionId)
    val saving=submission.processing||submission.saved
    var error by remember { mutableStateOf<String?>(null) }
    val recent by vm.transactions.collectAsState()
    val recentMethodIds=recent.map{it.paymentMethodId}.distinct()
    val historicalCategoryId=existing?.categoryId?.takeUnless{copyMode}
    LaunchedEffect(type,categories,categoryId,historicalCategoryId,accounts,paymentMethods) {
        if(categories.isNotEmpty()) {
            categoryId=fallbackCategoryId(categories,type,categoryId,historicalCategoryId)
        }
        if(accounts.isNotEmpty() && accounts.none{it.id==accountId} && existing?.accountId!=accountId) {
            accountId=accounts.first().id
        }
        if(paymentMethods.isNotEmpty() && paymentMethods.none{it.id==paymentMethodId} && existing?.paymentMethodId!=paymentMethodId) {
            paymentMethodId=paymentMethods.first().id
        }
    }
    var datePicker by remember { mutableStateOf(false) }
    val formScroll = rememberScrollState()
    val amountError = amount.isNotBlank() && ((parseMoneyCents(amount) ?: 0L)<=0)
    val dirty=if(existing==null||copyMode)amount.isNotBlank()||note.isNotBlank() else type!=existing.type||(parseMoneyCents(amount)!=existing.amountCents)||categoryId!=existing.categoryId||accountId!=existing.accountId||paymentMethodId!=existing.paymentMethodId||date!=dateOf(existing.occurredAt)||note!=existing.note
    val currentDraft by rememberUpdatedState(TransactionDraft(type,amount,categoryId,accountId,paymentMethodId,date.toString(),note,copyMode))
    val currentDirty by rememberUpdatedState(dirty)
    val draftToPersist by rememberUpdatedState(if(dirty)currentDraft else if(!draftRestored)storedDraft else null)
    DisposableEffect(draftSession) { onDispose { draftSession.persist(draftToPersist) } }
    LaunchedEffect(type,amount,categoryId,accountId,paymentMethodId,date,note,copyMode,draftRestored){delay(200);draftSession.persist(draftToPersist)}
    val requestClose={if(!saving){if(dirty)confirmClose=true else {draftSession.finish(draftToPersist);onDismiss()}}}
    val valid = !amountError && (parseMoneyCents(amount) ?: 0L)>0 && categoryId>0 &&
        orderedCategoryChoices(categories,type,historicalCategoryId).any{it.id==categoryId} && accountId>0 && paymentMethodId>0
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,confirmValueChange={value->
        when {
            draftSession.isClosed->true
            saving->false
            value==SheetValue.Hidden&&currentDirty->{confirmClose=true;false}
            else->true
        }
    })
    val save={
        if(!saving&&valid) {
            error=null
            vm.submitTransaction(submissionId,if(copyMode)0 else existing?.id?:0,type,amount,categoryId,accountId,paymentMethodId,date,note)
        }
    }
    LaunchedEffect(submission.saved){if(submission.saved)onDismiss()}
    LaunchedEffect(submission.error){submission.error?.let{error=it}}
    ModalBottomSheet(onDismissRequest=requestClose,sheetState=sheetState) {
        BackHandler(!datePicker&&!confirmClose){requestClose()}
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(formScroll)
                .padding(horizontal=22.dp).padding(bottom=24.dp)
        ) {
            Text(if(existing==null||copyMode) "记一笔" else "编辑账目", style=MaterialTheme.typography.headlineSmall)
            if(draftRestored)Text("已恢复暂存草稿",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(existing==null&&storedDraft!=null&&!draftRestored&&!dirty)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text("有一份暂存草稿",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={
                    type=storedDraft.type;amount=storedDraft.amount;categoryId=storedDraft.categoryId
                    accountId=storedDraft.accountId;paymentMethodId=storedDraft.paymentMethodId;note=storedDraft.note
                    date=runCatching{LocalDate.parse(storedDraft.date)}.getOrDefault(LocalDate.now())
                    copyMode=storedDraft.isCopy;draftRestored=true;error=null
                },enabled=!saving,modifier=Modifier.heightIn(min=48.dp)){Text("继续草稿",maxLines=1)}
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical=18.dp)) {
                SegmentedButton(type==MoneyType.EXPENSE,{type=MoneyType.EXPENSE},SegmentedButtonDefaults.itemShape(0,2),enabled=!saving){Text("支出")}
                SegmentedButton(type==MoneyType.INCOME,{type=MoneyType.INCOME},SegmentedButtonDefaults.itemShape(1,2),enabled=!saving){Text("收入")}
            }
            OutlinedTextField(amount,{amount=sanitizeMoneyInput(it)},Modifier.fillMaxWidth(),enabled=!saving,label={Text("金额")},prefix={Text("¥ ")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),textStyle=MaterialTheme.typography.headlineMedium,isError=amountError,supportingText=if(amountError){{Text("请输入大于 0 的金额，最多两位小数")}}else null)
            Text("分类", modifier=Modifier.padding(top=16.dp,bottom=8.dp))
            CategoryChoices(categories,type,categoryId,historicalCategoryId,enabled=!saving){categoryId=it}
            Text("账本", modifier=Modifier.padding(top=14.dp,bottom=8.dp))
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) { accounts.forEach{a->FilterChip(accountId==a.id,{accountId=a.id},{Text(a.name)},enabled=!saving)} }
            if(existing!=null&&accounts.none{it.id==accountId})Text("原账本：${existing.accountName}（已停用）",style=MaterialTheme.typography.bodySmall)
            Text("收支方式", modifier=Modifier.padding(top=14.dp,bottom=8.dp))
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) { paymentMethods.sortedBy{recentMethodIds.indexOf(it.id).takeIf{x->x>=0}?:Int.MAX_VALUE}.forEach{method->FilterChip(paymentMethodId==method.id,{paymentMethodId=method.id},{Text(method.name,maxLines=1,softWrap=false)},enabled=!saving)} }
            if(existing!=null&&paymentMethods.none{it.id==paymentMethodId})Text("原收支方式：${existing.paymentMethodName}（已停用）",style=MaterialTheme.typography.bodySmall)
            TransactionNoteField(note,{note=it.take(80)},Modifier.padding(top=14.dp),enabled=!saving)
            OutlinedButton(onClick={datePicker=true},Modifier.fillMaxWidth().padding(top=14.dp),enabled=!saving){Text("日期：$date")}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=10.dp))}
            Button(onClick=save,enabled=valid&&!saving,modifier=Modifier.fillMaxWidth().padding(top=18.dp).heightIn(min=52.dp)){Text(if(saving)"保存中…" else "保存")}
            if(existing!=null&&!copyMode) OutlinedButton(onClick={
                copyMode=true;amount="";date=LocalDate.now();error=null
                categoryId=fallbackCategoryId(categories,type,categoryId)
                if(accounts.none{it.id==accountId}) {
                    accountId=accounts.firstOrNull()?.id?:0
                }
                if(paymentMethods.none{it.id==paymentMethodId}) {
                    paymentMethodId=paymentMethods.firstOrNull()?.id?:0
                }
            },enabled=!saving,modifier=Modifier.fillMaxWidth().padding(top=8.dp).heightIn(min=48.dp)){Text("再记一笔")}
        }
    }
    if(confirmClose)AlertDialog(onDismissRequest={if(!saving)confirmClose=false},title={Text("账目尚未保存")},text={Text("可以保存账目，或保留草稿稍后继续。")},confirmButton={TextButton(onClick={confirmClose=false;save()},enabled=valid&&!saving){Text("保存")}},dismissButton={Column{TextButton(onClick={if(!saving){draftSession.finish(currentDraft);confirmClose=false;onDismiss()}},enabled=!saving){Text("保留草稿并退出")};TextButton(onClick={if(!saving){draftSession.finish(null);confirmClose=false;onDismiss()}},enabled=!saving){Text("放弃修改",color=Coral)};TextButton(onClick={confirmClose=false},enabled=!saving){Text("继续编辑")}}})
    if(datePicker) {
        val state=rememberDatePickerState(initialSelectedDateMillis=date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={datePicker=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{date=Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()};datePicker=false}){Text("确定")}},dismissButton={TextButton(onClick={datePicker=false}){Text("取消")}}){DatePicker(state)}
    }
}

@Composable
internal fun TransactionNoteField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bringIntoViewRequester=remember{BringIntoViewRequester()}
    var focused by remember{mutableStateOf(false)}
    val imeBottom=WindowInsets.ime.getBottom(LocalDensity.current)
    val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(focused,imeBottom) {
        if(focused) {
            // 等当前帧按实际输入法高度完成布局，再将备注字段本身移入可见区域。
            withFrameNanos { }
            bringIntoViewRequester.bringIntoView()
        }
    }
    OutlinedTextField(
        value=value,
        onValueChange=onValueChange,
        modifier=modifier.fillMaxWidth().bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged{focused=it.isFocused},
        enabled=enabled,
        label={Text("备注（可选）")},
        singleLine=true,
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
        keyboardActions=KeyboardActions(onDone={keyboard?.hide()}),
    )
}
