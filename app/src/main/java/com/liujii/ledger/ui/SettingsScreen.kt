package com.liujii.ledger.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.biometric.BiometricManager
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.liujii.ledger.LedgerViewModel
import com.liujii.ledger.BuildConfig
import com.liujii.ledger.data.*
import java.time.LocalDate
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(vm: LedgerViewModel, focusBudget:Boolean=false, onBudgetFocused:()->Unit={},onManageAccounts:()->Unit={}){
    val categories by vm.categories.collectAsState();val accounts by vm.accounts.collectAsState();val budgets by vm.budgets.collectAsState();val recurring by vm.recurring.collectAsState();val context=LocalContext.current
    var categoryDialog by remember{mutableStateOf<CategoryEntity?>(null)};var showNewCategory by remember{mutableStateOf(false)};var budgetDialog by remember{mutableStateOf(false)};var recurringDialog by remember{mutableStateOf<RecurringRuleEntity?>(null)};var showNewRecurring by remember{mutableStateOf(false)};var deletingRecurring by remember{mutableStateOf<RecurringRuleEntity?>(null)};var message by remember{mutableStateOf<String?>(null)}
    val budgetRequester=remember{BringIntoViewRequester()}
    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var deletingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var actionError by remember{mutableStateOf<String?>(null)}
    var importError by remember{mutableStateOf<String?>(null)}
    val today=rememberCurrentDate()
    val fileBusy=vm.fileOperationInProgress.value
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(section!=null) { section=null }
    LaunchedEffect(focusBudget){if(focusBudget){section="预算与周期账单";onBudgetFocused()}}
    val requestNotifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->vm.endExternalActivity();if(!granted)message="未开启通知权限，仍可在首页查看到期账单。"}
    var explainNotifications by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val createBackup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->try{uri?.let{vm.exportJsonToUri(it){failure->message=failure?:"备份已导出"}}}finally{vm.endExternalActivity()}}
    val importBackup=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->try{importError=null;pendingImport=uri}finally{vm.endExternalActivity()}}
    val createCsv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->try{uri?.let{vm.exportFilteredCsvToUri(it,CsvTransactionFilter()){failure,count->message=failure?:"CSV 已导出，共 $count 笔收支"}}}finally{vm.endExternalActivity()}}
    val pickImage=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->try{uri?.let{vm.selectBackgroundImage(it){failure->message=failure}}}finally{vm.endExternalActivity()}}
    fun launchExternal(action:()->Unit){
        if(fileBusy){message="另一个文件操作正在进行，请稍候";return}
        vm.beginExternalActivity()
        runCatching(action).onFailure{vm.endExternalActivity();message="无法打开系统选择器：${it.message?:"请稍后重试"}"}
    }
    ScreenColumn(if(section==null)"设置" else section!!,if(section==null)"把流迹调成你习惯的样子" else null){
        if(section!=null) TextButton(onClick={section=null}){Icon(Icons.Default.ChevronLeft,null);Text("返回设置")}
        if(section==null) {
            GlassCard(Modifier.fillMaxWidth()) {
                SettingAction(Icons.Default.Palette,"外观"){section="外观"}
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
                SettingAction(Icons.Default.Book,"账本与收支方式"){section="账本与收支方式"}
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
                SettingAction(Icons.Default.Category,"分类"){section="分类"}
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
                SettingAction(Icons.Default.EventRepeat,"预算与周期账单"){section="预算与周期账单"}
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
                SettingAction(Icons.Default.Security,"隐私与数据"){section="隐私与数据"}
            }
        }
        if(section=="外观") {
        SectionHeader("外观");GlassCard(Modifier.fillMaxWidth()){
            Text("显示模式");ThemeChoices(vm)
            Text("背景",modifier=Modifier.padding(top=8.dp));Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({vm.updateAppearance{it.copy(backgroundType="gradient",colorStart=0xFFEAF1F7,colorEnd=0xFFD9F1EB)}},enabled=!fileBusy,contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp)){Text("清透渐变",maxLines=1,softWrap=false)};OutlinedButton({launchExternal{pickImage.launch(arrayOf("image/*"))}},enabled=!fileBusy,contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp)){Icon(Icons.Default.Image,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("本地图片",maxLines=1,softWrap=false)}}
            Text("背景遮罩 ${(vm.appearance.value.overlayAlpha*100).toInt()}%",color=MaterialTheme.colorScheme.onSurfaceVariant);Slider(vm.appearance.value.overlayAlpha,{a->vm.updateAppearance{it.copy(overlayAlpha=a)}},valueRange=.45f..1f)
        }
        }
        if(section=="账本与收支方式") {
        Spacer(Modifier.height(20.dp));SectionHeader("账本","管理",onManageAccounts);GlassCard(Modifier.fillMaxWidth()){
            SettingAction(Icons.Default.Book,"${accounts.size} 个启用账本",onClick=onManageAccounts)
            Text("按日常、工作或特定项目整理账目；收支方式在记账时选择。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PaymentMethodsSettings(vm)
        }
        if(section=="分类") {
        Spacer(Modifier.height(20.dp));SectionHeader("分类","新增"){showNewCategory=true}
        Text("收入与支出独立排序。点分类可编辑，按住右侧手柄上下拖动；停用后仍保留历史账目。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=10.dp))
        CategorySortCard("支出分类",MoneyType.EXPENSE,categories,vm,{categoryDialog=it})
        Spacer(Modifier.height(12.dp))
        CategorySortCard("收入分类",MoneyType.INCOME,categories,vm,{categoryDialog=it})
        }
        if(section=="预算与周期账单") {
        Spacer(Modifier.height(20.dp));Column(Modifier.bringIntoViewRequester(budgetRequester)){SectionHeader("本月预算","添加"){budgetDialog=true};GlassCard(Modifier.fillMaxWidth()){
            val current=budgets.filter{it.yearMonth==java.time.YearMonth.from(today).toString()};if(current.isEmpty())Text("还没有预算",color=MaterialTheme.colorScheme.onSurfaceVariant)
            current.forEach { b ->
                Column(Modifier.fillMaxWidth().clickable{editingBudget=b}.padding(vertical=8.dp)) {
                    Text(categories.find{it.id==b.categoryId}?.name?:"总预算",style=MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(money(b.limitCents),Modifier.weight(1f))
                        TextButton({editingBudget=b}){Text("修改")}
                        IconButton({actionError=null;deletingBudget=b}){Icon(Icons.Default.Delete,"删除预算")}
                    }
                }
            }
        }}
        Spacer(Modifier.height(20.dp));SectionHeader("周期账单","新增"){showNewRecurring=true;if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)explainNotifications=true};GlassCard(Modifier.fillMaxWidth()){
            if(recurring.isEmpty())Text("尚未添加周期账单",color=MaterialTheme.colorScheme.onSurfaceVariant)
            recurring.forEach{r->val processing=vm.isProcessing("recurring-${r.id}");Row(Modifier.fillMaxWidth().clickable(enabled=!processing){recurringDialog=r}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(r.title);Text("${if(r.repeatType==RepeatType.MONTHLY)"每月" else "每周"} · ${dateText(r.nextAt)}${if(!r.active)" · 已暂停" else ""}${if(processing)" · 处理中…"else""}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(r.active,{vm.setRecurringActive(r,it){failure->message=failure}},enabled=!processing);IconButton({actionError=null;deletingRecurring=r},enabled=!processing){Icon(Icons.Default.Delete,"删除周期账单")}}}
        }
        }
        if(section=="隐私与数据") {
        Spacer(Modifier.height(20.dp));SectionHeader("隐私与数据");GlassCard(Modifier.fillMaxWidth()){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("应用锁");Text("使用指纹、人脸或系统凭据",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(vm.appearance.value.appLockEnabled,{v->if(v&&!context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure)message="请先在手机设置中配置系统锁屏或生物识别。" else vm.updateAppearance{it.copy(appLockEnabled=v)}})}
            HorizontalDivider(Modifier.padding(vertical=10.dp));SettingAction(Icons.Default.Backup,"导出完整备份",enabled=!fileBusy){launchExternal{createBackup.launch("流迹备份-$today.json")}};SettingAction(Icons.Default.Restore,"恢复完整备份",enabled=!fileBusy){launchExternal{importBackup.launch(arrayOf("application/json","text/plain"))}};SettingAction(Icons.Default.TableView,"导出全部收支 CSV",enabled=!fileBusy){launchExternal{createCsv.launch("流迹账目-$today.csv")}}
            if(fileBusy)Text("正在处理文件，请稍候…",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val autoBackup=context.filesDir.resolve("恢复前自动备份.json")
        if(autoBackup.exists())TextButton(onClick={importError=null;pendingImport=android.net.Uri.fromFile(autoBackup)},enabled=!fileBusy){Text("恢复上次导入前的自动备份")}
        }
        Spacer(Modifier.height(18.dp));Text("流迹 ${BuildConfig.VERSION_NAME} · 数据仅保存在本机",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
    }
    if(showNewCategory)CategoryDialog(vm,null){showNewCategory=false};categoryDialog?.let{editing->CategoryDialog(vm,editing){categoryDialog=null}};if(budgetDialog)BudgetDialog(vm,categories){budgetDialog=false};if(showNewRecurring)RecurringDialog(vm,categories,null){showNewRecurring=false};recurringDialog?.let{rule->RecurringDialog(vm,categories,rule){recurringDialog=null}}
    message?.let{AlertDialog(onDismissRequest={message=null},confirmButton={TextButton({message=null}){Text("知道了")}},title={Text("提示")},text={Text(it)})}
    editingBudget?.let { BudgetDialog(vm,categories,it){editingBudget=null} }
    deletingBudget?.let { b -> val processing=vm.isProcessing("budget-delete-${b.id}");AlertDialog(onDismissRequest={if(!processing)deletingBudget=null},title={Text("删除预算？")},text={Column{Text("仅删除这项预算，已记录的收支不会改变。");actionError?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton({vm.deleteBudget(b.id){failure->actionError=failure;if(failure==null)deletingBudget=null}},enabled=!processing){Text(if(processing)"处理中…"else"删除")}},dismissButton={TextButton({deletingBudget=null},enabled=!processing){Text("取消")}}) }
    deletingRecurring?.let { rule -> val processing=vm.isProcessing("recurring-${rule.id}");AlertDialog(onDismissRequest={if(!processing)deletingRecurring=null},title={Text("删除周期账单？")},text={Column{Text("将删除“${rule.title}”的后续提醒，已入账的历史不受影响。");actionError?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton({vm.deleteRecurring(rule){failure->actionError=failure;if(failure==null)deletingRecurring=null}},enabled=!processing){Text(if(processing)"处理中…"else"删除",color=Coral)}},dismissButton={TextButton({deletingRecurring=null},enabled=!processing){Text("取消")}}) }
    pendingImport?.let { uri -> AlertDialog(onDismissRequest={if(!fileBusy)pendingImport=null},title={Text("恢复备份？")},text={Column{Text("将用选定备份替换当前账目。恢复前会自动保留当前数据，失败时当前数据不变。");importError?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={importError=null;vm.restoreBackup(uri){outcome->if(outcome.restored){pendingImport=null;message=outcome.message?:"备份恢复成功"}else importError=outcome.message}},enabled=!fileBusy){Text(if(fileBusy)"恢复中…"else"确认恢复")}},dismissButton={TextButton(onClick={pendingImport=null},enabled=!fileBusy){Text("取消")}}) }
    if(explainNotifications)AlertDialog(onDismissRequest={explainNotifications=false},title={Text("开启账单提醒")},text={Text("通知用于提醒周期账单到期。不开启也可以在首页查看账单。")},confirmButton={TextButton(onClick={explainNotifications=false;if(Build.VERSION.SDK_INT>=33)launchExternal{requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)}}){Text("开启通知")}},dismissButton={TextButton(onClick={explainNotifications=false}){Text("暂不开启")}})
}

@Composable
private fun RowScope.ThemeModeButton(label:String,selected:Boolean,weight:Float,onClick:()->Unit){
    Surface(
        onClick=onClick,
        modifier=Modifier.weight(weight).heightIn(min=48.dp),
        shape=RoundedCornerShape(16.dp),
        color=if(selected)MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor=MaterialTheme.colorScheme.onSurface,
        border=BorderStroke(1.dp,if(selected)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outlineVariant),
    ){
        Box(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=12.dp),contentAlignment=Alignment.Center){Text(label,maxLines=1,softWrap=false,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)}
    }
}

@Composable
private fun ThemeChoices(vm:LedgerViewModel) {
    val scale=LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
        val stacked=maxWidth.value < 96*scale+64*scale+80
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ThemeModeButton("跟随系统",vm.appearance.value.themeMode=="system",1.4f){vm.updateAppearance{it.copy(themeMode="system")}}
                if(!stacked){ThemeModeButton("白天",vm.appearance.value.themeMode=="light",1f){vm.updateAppearance{it.copy(themeMode="light")}};ThemeModeButton("黑夜",vm.appearance.value.themeMode=="dark",1f){vm.updateAppearance{it.copy(themeMode="dark")}}}
            }
            if(stacked)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){ThemeModeButton("白天",vm.appearance.value.themeMode=="light",1f){vm.updateAppearance{it.copy(themeMode="light")}};ThemeModeButton("黑夜",vm.appearance.value.themeMode=="dark",1f){vm.updateAppearance{it.copy(themeMode="dark")}}}
        }
    }
}

@Composable private fun SettingAction(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,enabled:Boolean=true,onClick:()->Unit){Row(Modifier.fillMaxWidth().clickable(enabled=enabled,onClick=onClick).padding(vertical=12.dp).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null);Text(label,Modifier.padding(start=14.dp).weight(1f));Icon(Icons.Default.ChevronRight,null)}}

@Composable
private fun CategorySortCard(
    title: String,
    type: MoneyType,
    categories: List<CategoryEntity>,
    vm: LedgerViewModel,
    onEdit: (CategoryEntity) -> Unit,
) {
    val activeCategories = categories
        .filter { it.active && it.type == type }
        .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
    val orderedCategories = remember(type, activeCategories) { activeCategories.toMutableStateList() }
    var draggedCategoryId by remember(type) { mutableStateOf<Long?>(null) }
    var dragDistance by remember(type) { mutableFloatStateOf(0f) }
    var pendingDisable by remember { mutableStateOf<CategoryEntity?>(null) }
    var showDisabled by rememberSaveable(type) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val disabledCategories = categories.filter { !it.active && it.type == type }
        .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
    val ordering = vm.isProcessing("category-order-$type")
    val moveThreshold = with(LocalDensity.current) { 34.dp.toPx() }
    val accent = if (type == MoneyType.EXPENSE) Coral else Mint

    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(accent, CircleShape))
            Text(title, Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleSmall)
            Text("${orderedCategories.size} 个", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))

        orderedCategories.forEachIndexed { index, category ->
            key(category.id) {
                val isDragging = draggedCategoryId == category.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDragging) accent.copy(alpha = .13f) else Color.Transparent)
                        .clickable(enabled = !ordering && !vm.isProcessing("category-active-${category.id}")) { onEdit(category) }
                        .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).background(Color(category.color).copy(alpha = .15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(12.dp).background(Color(category.color),CircleShape)) }
                    Text(category.name, Modifier.padding(start = 12.dp).weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    TextButton(onClick = { error=null;pendingDisable=category }, enabled = !ordering && !vm.isProcessing("category-active-${category.id}")) {
                        Text("停用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        Modifier
                            .size(48.dp)
                            .pointerInput(category.id, orderedCategories.size, ordering) {
                                if (ordering) return@pointerInput
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedCategoryId = category.id
                                        dragDistance = 0f
                                    },
                                    onDragCancel = {
                                        draggedCategoryId = null
                                        dragDistance = 0f
                                    },
                                    onDragEnd = {
                                        vm.reorderCategories(type, orderedCategories.map { it.id }) { failure ->
                                            if (failure != null) {
                                                orderedCategories.sortWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
                                                error = failure
                                            }
                                        }
                                        draggedCategoryId = null
                                        dragDistance = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDistance += amount.y
                                        var currentIndex = orderedCategories.indexOfFirst { it.id == category.id }
                                        while (dragDistance > moveThreshold && currentIndex < orderedCategories.lastIndex) {
                                            orderedCategories.add(currentIndex + 1, orderedCategories.removeAt(currentIndex))
                                            dragDistance -= moveThreshold
                                            currentIndex++
                                        }
                                        while (dragDistance < -moveThreshold && currentIndex > 0) {
                                            orderedCategories.add(currentIndex - 1, orderedCategories.removeAt(currentIndex))
                                            dragDistance += moveThreshold
                                            currentIndex--
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "按住拖动${category.name}调整顺序",
                            tint = if (isDragging) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (index < orderedCategories.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                }
            }
        }
        if (ordering) Text("正在保存顺序…", style=MaterialTheme.typography.bodySmall)
        if (disabledCategories.isNotEmpty()) {
            TextButton(onClick={showDisabled=!showDisabled}, modifier=Modifier.heightIn(min=48.dp)) {
                Text("已停用（${disabledCategories.size}）${if(showDisabled)" · 收起"else" · 展开"}")
            }
            if (showDisabled) disabledCategories.forEach { category ->
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                    Text(category.name, Modifier.weight(1f), maxLines=1, overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    val processing=vm.isProcessing("category-active-${category.id}")
                    TextButton(onClick={vm.setCategoryActive(category.id,true){failure->error=failure}}, enabled=!processing&&!ordering) {
                        Text(if(processing)"处理中…"else"启用", maxLines=1)
                    }
                }
            }
        }
        if (pendingDisable == null) error?.let { Text(it, color=MaterialTheme.colorScheme.error) }
    }
    pendingDisable?.let { category ->
        val processing=vm.isProcessing("category-active-${category.id}")
        AlertDialog(onDismissRequest={if(!processing)pendingDisable=null},title={Text("停用${category.name}？")},text={Column{Text("历史账目仍会保留。停用后，新账目不再提供此分类。");error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={error=null;vm.deactivateCategory(category){failure->error=failure;if(failure==null)pendingDisable=null}},enabled=!processing){Text(if(processing)"处理中…"else"停用",color=Coral)}},dismissButton={TextButton(onClick={pendingDisable=null},enabled=!processing){Text("取消")}})
    }
}

@Composable
private fun CategoryDialog(vm:LedgerViewModel,existing:CategoryEntity?,onDismiss:()->Unit) {
    var name by remember{mutableStateOf(existing?.name?:"")};var type by remember{mutableStateOf(existing?.type?:MoneyType.EXPENSE)};var color by remember{mutableLongStateOf(canonicalMarkerColor(existing?.color?:0xFFE76F64))}
    var saving by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    val rows by vm.transactions.collectAsState();val recurring by vm.recurring.collectAsState();val budgets by vm.budgets.collectAsState()
    val used=existing!=null&&(rows.any{it.categoryId==existing.id}||recurring.any{it.categoryId==existing.id}||budgets.any{it.categoryId==existing.id})
    var confirmExit by remember{mutableStateOf(false)}
    val dirty=name!=(existing?.name?:"")||type!=(existing?.type?:MoneyType.EXPENSE)||color!=canonicalMarkerColor(existing?.color?:0xFFE76F64)
    val requestClose={if(!saving){if(dirty)confirmExit=true else onDismiss()}}
    val save={if(!saving){saving=true;error=null;vm.saveCategory(existing?.id?:0,name,type,name.take(1),color,existing?.sortOrder?:99,existing?.active?:true){failure->saving=false;if(failure==null)onDismiss()else error=failure}}}
    AlertDialog(onDismissRequest=requestClose,title={Text(if(existing==null)"新增分类" else "编辑分类")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        SingleChoiceSegmentedButtonRow{SegmentedButton(type==MoneyType.EXPENSE,{type=MoneyType.EXPENSE},SegmentedButtonDefaults.itemShape(0,2),enabled=!used&&!saving){Text("支出")};SegmentedButton(type==MoneyType.INCOME,{type=MoneyType.INCOME},SegmentedButtonDefaults.itemShape(1,2),enabled=!used&&!saving){Text("收入")}}
        if(used)Text("已有记录的分类保留收支类型，名称与颜色可修改。",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(name,{name=it.take(8)},enabled=!saving,label={Text("分类名称")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=12.dp));Text("标识颜色",Modifier.padding(top=12.dp,bottom=8.dp));MarkerColorPicker(color){if(!saving)color=it}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick=save,enabled=name.isNotBlank()&&!saving){Text(if(saving)"保存中…"else"保存")}},dismissButton={TextButton(onClick=requestClose,enabled=!saving){Text("取消")}})
    UnsavedChangesDialog(confirmExit,name.isNotBlank()&&!saving,{confirmExit=false;save()},{confirmExit=false;onDismiss()},{confirmExit=false})
}

@Composable
private fun BudgetDialog(vm:LedgerViewModel,categories:List<CategoryEntity>,existing:BudgetEntity?=null,onDismiss:()->Unit) {
    var amount by remember { mutableStateOf(existing?.let { java.math.BigDecimal.valueOf(it.limitCents,2).toPlainString() } ?: "") }
    var category by remember { mutableStateOf(existing?.categoryId) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val valid = (parseMoneyCents(amount) ?: 0L) > 0
    var confirmExit by remember{mutableStateOf(false)}
    val dirty=if(existing==null)amount.isNotBlank()||category!=null else parseMoneyCents(amount)!=existing.limitCents||category!=existing.categoryId
    val requestClose={if(!saving){if(dirty)confirmExit=true else onDismiss()}}
    val save={if(!saving){saving=true;error=null;vm.saveBudget(amount,category,existing){failure->saving=false;if(failure==null)onDismiss()else error=failure}}}
    AlertDialog(onDismissRequest=requestClose,title={Text(if(existing==null)"添加本月预算" else "修改预算")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(amount,{amount=sanitizeMoneyInput(it);error=null},enabled=!saving,label={Text("预算金额")},prefix={Text("¥")},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),isError=amount.isNotBlank()&&!valid,
                supportingText={Text("请输入大于 0 的金额，最多两位小数")})
            Text("范围",Modifier.padding(top=12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(category==null,{category=null},{Text("总预算")},enabled=!saving)
                categories.filter{(it.active || it.id==existing?.categoryId)&&it.type==MoneyType.EXPENSE}.forEach{c->FilterChip(category==c.id,{category=c.id},{Text(c.name)},enabled=!saving)}
            }
            if(existing==null)Text("同月同一范围已有预算时，保存会更新该预算。",style=MaterialTheme.typography.bodySmall)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(save,enabled=valid&&!saving){Text(if(saving)"保存中" else "保存")}},dismissButton={TextButton(requestClose,enabled=!saving){Text("取消")}})
    UnsavedChangesDialog(confirmExit,valid&&!saving,{confirmExit=false;save()},{confirmExit=false;onDismiss()},{confirmExit=false})
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RecurringDialog(vm:LedgerViewModel,categories:List<CategoryEntity>,existing:RecurringRuleEntity?,onDismiss:()->Unit){
    val allAccounts by vm.managedAccounts.collectAsState();val allMethods by vm.managedPaymentMethods.collectAsState()
    val accounts=allAccounts.filter{it.active||it.id==existing?.accountId}
    val methods=allMethods.filter{it.active||it.id==existing?.paymentMethodId}
    var title by remember{mutableStateOf(existing?.title?:"")};var amount by remember{mutableStateOf(existing?.let{java.math.BigDecimal.valueOf(it.amountCents,2).toPlainString()}?:"")};var type by remember{mutableStateOf(existing?.type?:MoneyType.EXPENSE)};var repeat by remember{mutableStateOf(existing?.repeatType?:RepeatType.MONTHLY)}
    var category by remember{mutableLongStateOf(existing?.categoryId?:0)};var account by remember{mutableLongStateOf(existing?.accountId?:0)};var method by remember{mutableLongStateOf(existing?.paymentMethodId?:0)};var days by remember{mutableIntStateOf(existing?.remindDaysBefore?:1)};var nextDate by remember{mutableStateOf(existing?.let{dateOf(it.nextAt)}?:LocalDate.now().plusDays(1))}
    LaunchedEffect(type,categories,accounts,methods){
        if(categories.isNotEmpty())category=fallbackCategoryId(categories,type,category,existing?.categoryId)
        if(accounts.isNotEmpty()&&accounts.none{it.id==account})account=accounts.firstOrNull{it.active}?.id?:0
        if(methods.isNotEmpty()&&methods.none{it.id==method})method=methods.firstOrNull{it.active}?.id?:0
    }
    var endDate by remember{mutableStateOf(existing?.endAt?.let{dateOf(it)})}
    var hour by remember{mutableIntStateOf(existing?.remindHour?:9)};var minute by remember{mutableIntStateOf(existing?.remindMinute?:0)}
    var dateTarget by remember{mutableStateOf<String?>(null)};var showTime by remember{mutableStateOf(false)};var saving by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    val valid=title.isNotBlank()&&(parseMoneyCents(amount)?:0)>0&&
        orderedCategoryChoices(categories,type,existing?.categoryId).any{it.id==category}&&
        accounts.any{it.id==account}&&methods.any{it.id==method}&&(endDate==null||endDate!!>=nextDate)
    var confirmExit by remember{mutableStateOf(false)}
    val dirty=if(existing==null)title.isNotBlank()||amount.isNotBlank()||endDate!=null else title!=existing.title||parseMoneyCents(amount)!=existing.amountCents||type!=existing.type||repeat!=existing.repeatType||category!=existing.categoryId||account!=existing.accountId||method!=existing.paymentMethodId||days!=existing.remindDaysBefore||nextDate!=dateOf(existing.nextAt)||endDate!=existing.endAt?.let{dateOf(it)}||hour!=existing.remindHour||minute!=existing.remindMinute
    val requestClose={if(!saving){if(dirty)confirmExit=true else onDismiss()}}
    val save={if(!saving){saving=true;error=null;vm.saveRecurring(id=existing?.id?:0,title=title,type=type,amount=amount,categoryId=category,accountId=account,paymentMethodId=method,repeat=repeat,nextDate=nextDate,days=days,hour=hour,minute=minute,endDate=endDate,active=existing?.active?:true,after=onDismiss,onError={saving=false;error=it})}}
    AlertDialog(onDismissRequest=requestClose,title={Text(if(existing==null)"新增周期账单" else "编辑周期账单")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        SingleChoiceSegmentedButtonRow{SegmentedButton(type==MoneyType.EXPENSE,{type=MoneyType.EXPENSE},SegmentedButtonDefaults.itemShape(0,2),enabled=!saving){Text("支出")};SegmentedButton(type==MoneyType.INCOME,{type=MoneyType.INCOME},SegmentedButtonDefaults.itemShape(1,2),enabled=!saving){Text("收入")}}
        OutlinedTextField(title,{title=it.take(30)},enabled=!saving,label={Text("名称")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=10.dp));OutlinedTextField(amount,{amount=sanitizeMoneyInput(it)},enabled=!saving,label={Text("金额")},prefix={Text("¥")},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),isError=amount.isNotBlank()&&(parseMoneyCents(amount)?:0)<=0,supportingText={Text("金额大于 0，最多两位小数")},modifier=Modifier.fillMaxWidth().padding(top=8.dp))
        Text("分类",Modifier.padding(top=10.dp));CategoryChoices(categories,type,category,existing?.categoryId,enabled=!saving){category=it}
        Text("账本",Modifier.padding(top=10.dp));Row(Modifier.horizontalScroll(rememberScrollState())){accounts.forEach{a->FilterChip(account==a.id,{account=a.id},{Text(a.name+if(!a.active)"（已停用）"else"",maxLines=1,softWrap=false)},enabled=!saving,modifier=Modifier.padding(end=6.dp))}}
        Text("收支方式",Modifier.padding(top=10.dp));Row(Modifier.horizontalScroll(rememberScrollState())){methods.forEach{m->FilterChip(method==m.id,{method=m.id},{Text(m.name+if(!m.active)"（已停用）"else"",maxLines=1,softWrap=false)},enabled=!saving,modifier=Modifier.padding(end=6.dp))}}
        Text("频率与提醒",Modifier.padding(top=10.dp));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(repeat==RepeatType.MONTHLY,{repeat=RepeatType.MONTHLY},{Text("每月")},enabled=!saving);FilterChip(repeat==RepeatType.WEEKLY,{repeat=RepeatType.WEEKLY},{Text("每周")},enabled=!saving);FilterChip(days==0,{days=0},{Text("当天提醒")},enabled=!saving);FilterChip(days==1,{days=1},{Text("提前 1 天")},enabled=!saving)}
        OutlinedButton(onClick={dateTarget="next"},enabled=!saving,modifier=Modifier.fillMaxWidth()){Text("${if(existing==null)"开始" else "下次入账"}日期：$nextDate")}
        OutlinedButton(onClick={dateTarget="end"},enabled=!saving,modifier=Modifier.fillMaxWidth()){Text("结束日期：${endDate?:"不限"}")}
        if(endDate!=null)TextButton(onClick={endDate=null},enabled=!saving){Text("取消结束日期")}
        OutlinedTextField(days.toString(),{raw->raw.toIntOrNull()?.takeIf{it in 0..30}?.let{days=it}},enabled=!saving,label={Text("提前提醒天数（0—30）")},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
        OutlinedButton(onClick={showTime=true},enabled=!saving,modifier=Modifier.fillMaxWidth()){Text("提醒时间：%02d:%02d".format(hour,minute))}
        if(endDate!=null&&endDate!!<nextDate)Text("结束日期不能早于入账日期",color=MaterialTheme.colorScheme.error)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(save,enabled=valid&&!saving){Text(if(saving)"保存中…"else"保存")}},dismissButton={TextButton(onClick=requestClose,enabled=!saving){Text("取消")}})
    UnsavedChangesDialog(confirmExit,valid&&!saving,{confirmExit=false;save()},{confirmExit=false;onDismiss()},{confirmExit=false})
    dateTarget?.let { target -> DateChoiceDialog(if(target=="next")nextDate else endDate?:nextDate,onDismiss={dateTarget=null},onSelected={if(target=="next")nextDate=it else endDate=it;dateTarget=null}) }
    if(showTime){
        val time=rememberTimePickerState(initialHour=hour,initialMinute=minute,is24Hour=true)
        AlertDialog(onDismissRequest={showTime=false},title={Text("提醒时间")},text={TimeInput(time)},confirmButton={TextButton(onClick={hour=time.hour;minute=time.minute;showTime=false}){Text("确定")}},dismissButton={TextButton(onClick={showTime=false}){Text("取消")}})
    }
}
