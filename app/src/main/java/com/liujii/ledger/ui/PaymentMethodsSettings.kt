package com.liujii.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liujii.ledger.LedgerViewModel
import com.liujii.ledger.data.PaymentMethodEntity

@Composable
fun PaymentMethodsSettings(vm: LedgerViewModel) {
    val allMethods by vm.managedPaymentMethods.collectAsState()
    val methods=allMethods.filter{it.active}
    val disabledMethods=allMethods.filter{!it.active}
    var showDisabled by rememberSaveable{mutableStateOf(false)}
    var editing by remember { mutableStateOf<PaymentMethodEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingDisable by remember { mutableStateOf<PaymentMethodEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Spacer(Modifier.height(24.dp))
    SectionHeader("收支方式","新增"){error=null;adding=true}
    Text("现金、微信和支付宝等付款来源。停用后历史记录仍保留。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    methods.forEach { method ->
        Row(Modifier.fillMaxWidth().clickable(enabled=!vm.isProcessing("method-active-${method.id}")){error=null;editing=method}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(10.dp).background(Color(method.color),CircleShape))
            Text(method.name,Modifier.padding(start=12.dp).weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
            TextButton(onClick={pendingDisable=method;error=null},enabled=!vm.isProcessing("method-active-${method.id}")){Text("停用")}
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
    }
    if(disabledMethods.isNotEmpty()) {
        TextButton(onClick={showDisabled=!showDisabled},modifier=Modifier.heightIn(min=48.dp)){Text("已停用（${disabledMethods.size}）${if(showDisabled)" · 收起"else" · 展开"}")}
        AnimatedGroup(showDisabled){disabledMethods.forEach{method->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text(method.name,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val processing=vm.isProcessing("method-active-${method.id}")
                TextButton(onClick={vm.setPaymentMethodActive(method.id,true){failure->error=failure}},enabled=!processing){Text(if(processing)"处理中…"else"启用",maxLines=1)}
            }
        }}
    }
    if(adding||editing!=null) {
        val current=editing
        var name by remember(current?.id){mutableStateOf(current?.name?:"")}
        var color by remember(current?.id){mutableLongStateOf(canonicalMarkerColor(current?.color?:0xFF4D8BB8))}
        var saving by remember { mutableStateOf(false) }
        var confirmExit by remember { mutableStateOf(false) }
        val dirty=name!=(current?.name?:"")||color!=canonicalMarkerColor(current?.color?:0xFF4D8BB8)
        val requestClose={if(!saving){if(dirty)confirmExit=true else{adding=false;editing=null}}}
        val save:()->Unit={if(!saving){saving=true;error=null;vm.savePaymentMethod(current?.copy(name=name.trim(),color=color)?:PaymentMethodEntity(name=name.trim(),color=color,sortOrder=methods.size)){failure->saving=false;if(failure==null){adding=false;editing=null}else error=failure}}}
        AlertDialog(onDismissRequest=requestClose,title={Text(if(current==null)"新增收支方式"else"编辑收支方式")},text={Column(Modifier.verticalScroll(rememberScrollState())){
            OutlinedTextField(name,{name=it.take(16)},enabled=!saving,label={Text("名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("标识颜色",Modifier.padding(top=12.dp,bottom=8.dp));MarkerColorPicker(color){if(!saving)color=it}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},confirmButton={TextButton(onClick=save,enabled=name.isNotBlank()&&!saving){Text(if(saving)"保存中…" else "保存")}},dismissButton={TextButton(onClick=requestClose,enabled=!saving){Text("取消")}})
        UnsavedChangesDialog(confirmExit,name.isNotBlank()&&!saving,{confirmExit=false;save()},{confirmExit=false;adding=false;editing=null},{confirmExit=false})
    }
    pendingDisable?.let { method ->
        val processing=vm.isProcessing("method-active-${method.id}")
        AlertDialog(onDismissRequest={if(!processing)pendingDisable=null},title={Text("停用${method.name}？")},text={Column{Text("历史账目不会改变，记账时不再提供此方式。");error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={vm.setPaymentMethodActive(method.id,false){failure->error=failure;if(failure==null)pendingDisable=null}},enabled=!processing){Text(if(processing)"处理中…"else"停用",color=Coral)}},dismissButton={TextButton(onClick={pendingDisable=null},enabled=!processing){Text("取消")}})
    }
    if(pendingDisable==null&&!adding&&editing==null)error?.let { AlertDialog(onDismissRequest={error=null},title={Text("未能完成")},text={Text(it)},confirmButton={TextButton(onClick={error=null}){Text("知道了")}}) }
}
