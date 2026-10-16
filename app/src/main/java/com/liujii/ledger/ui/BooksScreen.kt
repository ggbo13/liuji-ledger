package com.liujii.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liujii.ledger.LedgerViewModel
import com.liujii.ledger.data.AccountEntity

@Composable
fun BooksScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val allBooks by vm.managedAccounts.collectAsState()
    val books=allBooks.filter{it.active}
    val disabledBooks=allBooks.filter{!it.active}
    var showDisabled by rememberSaveable{mutableStateOf(false)}
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val ordered = remember(books) { books.toMutableStateList() }
    val ordering = vm.isProcessing("book-order")
    var dragY by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 42.dp.toPx() }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=32.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp), verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回") }
            Text("账本管理", maxLines=1, overflow=TextOverflow.Ellipsis, style=MaterialTheme.typography.headlineSmall, modifier=Modifier.weight(1f))
            TextButton(onClick={adding=true}) { Text("新建",maxLines=1) }
        }
        Text("用日常、工作或特定项目区分账目", color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(start=48.dp,bottom=18.dp))
        Text("按住右侧手柄拖动排序",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            ordered.forEachIndexed { index, book ->
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Color(book.color).copy(alpha=.16f),CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(12.dp).background(Color(book.color),CircleShape))}
                    Text(book.name,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=12.dp).weight(1f))
                    IconButton(onClick={editing=book}) { Icon(Icons.Default.Edit,"编辑${book.name}") }
                    Box(Modifier.size(48.dp).pointerInput(book.id,ordered.size,ordering){
                        if(ordering)return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart={dragY=0f}, onDragCancel={dragY=0f},
                            onDragEnd={vm.reorderAccounts(ordered.map{it.id}){failure->if(failure!=null){ordered.sortWith(compareBy<AccountEntity>{it.sortOrder}.thenBy{it.id});message=failure}};dragY=0f},
                            onDrag={change,amount->change.consume();dragY+=amount.y;var current=ordered.indexOfFirst{it.id==book.id};while(dragY>threshold&&current<ordered.lastIndex){ordered.add(current+1,ordered.removeAt(current));dragY-=threshold;current++};while(dragY< -threshold&&current>0){ordered.add(current-1,ordered.removeAt(current));dragY+=threshold;current--}}
                        )
                    },contentAlignment=Alignment.Center){Icon(Icons.Default.DragHandle,"拖动${book.name}排序")}
                }
                if(index<ordered.lastIndex)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
            }
        }
        if(ordering)Text("正在保存顺序…",style=MaterialTheme.typography.bodySmall)
        if(disabledBooks.isNotEmpty()) {
            TextButton(onClick={showDisabled=!showDisabled},modifier=Modifier.heightIn(min=48.dp)){Text("已停用（${disabledBooks.size}）${if(showDisabled)" · 收起"else" · 展开"}")}
            if(showDisabled)disabledBooks.forEach{book->
                Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(book.name,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    val processing=vm.isProcessing("book-active-${book.id}")
                    TextButton(onClick={vm.setBookActive(book.id,true){failure->message=failure}},enabled=!processing){Text(if(processing)"处理中…"else"启用",maxLines=1)}
                }
            }
        }
    }
    if(adding) BookDialog(vm,null,onDismiss={adding=false})
    editing?.let { book -> BookDialog(vm,book,{editing=null}) { done -> vm.deactivateBook(book,done) } }
    message?.let { text -> AlertDialog(onDismissRequest={message=null},title={Text("提示")},text={Text(text)},confirmButton={TextButton({message=null}){Text("知道了")}}) }
}

@Composable
private fun BookDialog(vm:LedgerViewModel,existing:AccountEntity?,onDismiss:()->Unit,onDeactivate:(((String?)->Unit)->Unit)?=null){
    var confirmDisable by remember{mutableStateOf(false)}
    var name by remember{mutableStateOf(existing?.name?:"")};var color by remember{mutableLongStateOf(canonicalMarkerColor(existing?.color?:0xFF4D8BB8))}
    var saving by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var confirmExit by remember{mutableStateOf(false)}
    val dirty=name!=(existing?.name?:"")||color!=canonicalMarkerColor(existing?.color?:0xFF4D8BB8)
    val requestClose={if(!saving){if(dirty)confirmExit=true else onDismiss()}}
    val save={if(!saving){saving=true;error=null;vm.saveBook(existing?.id?:0,name,color,onDismiss,onError={saving=false;error=it})}}
    AlertDialog(onDismissRequest=requestClose,title={Text(if(existing==null)"新建账本" else "编辑账本")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        OutlinedTextField(name,{name=it.take(16)},enabled=!saving,label={Text("账本名称")},placeholder={Text("例如：日常、工作、装修")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("标识颜色",Modifier.padding(top=14.dp,bottom=8.dp));MarkerColorPicker(color){if(!saving)color=it}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(onDeactivate!=null)TextButton(onClick={confirmDisable=true;error=null},enabled=!saving,modifier=Modifier.padding(top=8.dp)){Text("停用账本",color=Coral)}
    }},confirmButton={TextButton(save,enabled=name.isNotBlank()&&!saving){Text(if(saving)"保存中…"else"保存")}},dismissButton={TextButton(requestClose,enabled=!saving){Text("取消")}})
    UnsavedChangesDialog(confirmExit,name.isNotBlank()&&!saving,{confirmExit=false;save()},{confirmExit=false;onDismiss()},{confirmExit=false})
    if(confirmDisable)AlertDialog(onDismissRequest={if(!saving)confirmDisable=false},title={Text("停用账本？")},text={Column{Text("已记录的账目仍会保留，新账目不再提供此账本。");error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={if(!saving){saving=true;onDeactivate?.invoke{failure->saving=false;error=failure;if(failure==null){confirmDisable=false;onDismiss()}}}},enabled=!saving){Text(if(saving)"处理中…"else"停用",color=Coral)}},dismissButton={TextButton(onClick={confirmDisable=false},enabled=!saving){Text("取消")}})
}
