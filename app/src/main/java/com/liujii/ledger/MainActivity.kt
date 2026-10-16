package com.liujii.ledger

import android.os.Build
import android.app.KeyguardManager
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liujii.ledger.ui.*

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var ledgerViewModel: LedgerViewModel? = null
    private var shortcutAction by mutableStateOf<String?>(null)
    private var lockMessage by mutableStateOf<String?>(null)
    private val confirmCredential=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        ledgerViewModel?.endExternalActivity()
        if(result.resultCode==Activity.RESULT_OK)unlocked=true else {shortcutAction=null;lockMessage="验证已取消，可再次验证进入。"}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shortcutAction = intent.getStringExtra(EXTRA_SHORTCUT_ACTION)
        setContent {
            val vm: LedgerViewModel = viewModel()
            ledgerViewModel = vm
            val appState=rememberSaveableStateHolder()
            LiuJiTheme(vm.appearance.value) {
                if (vm.appearance.value.appLockEnabled && !unlocked) LockedScreen(lockMessage) { authenticate() } else appState.SaveableStateProvider("ledger"){LedgerApp(vm,shortcutAction){shortcutAction=null}}
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutAction = intent.getStringExtra(EXTRA_SHORTCUT_ACTION)
        if ((application as LiuJiApplication).repository.loadAppearance().appLockEnabled) unlocked = false
    }

    override fun onStop() {
        super.onStop()
        if ((application as LiuJiApplication).repository.loadAppearance().appLockEnabled && ledgerViewModel?.externalActivityInProgress != true) unlocked = false
    }

    private fun authenticate() {
        val keyguard=getSystemService(KeyguardManager::class.java)
        if(Build.VERSION.SDK_INT<30&&keyguard.isDeviceSecure){
            ledgerViewModel?.beginExternalActivity()
            @Suppress("DEPRECATION")
            val intent=keyguard.createConfirmDeviceCredentialIntent("解锁流迹","验证后查看你的账目")
            if(intent!=null)confirmCredential.launch(intent)else{ledgerViewModel?.endExternalActivity();lockMessage="请先配置系统锁屏。"}
            return
        }
        val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            lockMessage = "设备尚未配置指纹、人脸或系统锁屏凭据，暂时无法解锁。"
            return
        }
        lockMessage=null
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
            override fun onAuthenticationError(errorCode:Int,errString:CharSequence){shortcutAction=null;lockMessage=errString.toString()}
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("解锁流迹").setSubtitle("验证后查看你的账目").setAllowedAuthenticators(allowed).build())
    }

    companion object {
        const val EXTRA_SHORTCUT_ACTION = "shortcut_action"
        const val SHORTCUT_EXPENSE = "expense"
        const val SHORTCUT_INCOME = "income"
        const val SHORTCUT_DETAILS = "details"
    }
}

@Composable
private fun LockedScreen(message:String?,onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MistBlue, Color(0xFFD9F1EB)))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(Icons.Default.Lock, null, tint = DeepSlate, modifier = Modifier.size(48.dp))
            Text("流迹已锁定", style = MaterialTheme.typography.headlineSmall, color = DeepSlate)
            message?.let { Text(it,Modifier.padding(horizontal=32.dp),color=DeepSlate.copy(alpha=.75f),style=MaterialTheme.typography.bodyMedium) }
            Button(onClick = onUnlock) { Text("验证并进入") }
        }
    }
}

private data class NavItem(val label: String, val icon: ImageVector)

@Composable
private fun LedgerApp(vm: LedgerViewModel, shortcutAction:String?, onShortcutConsumed:()->Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var addingType by rememberSaveable { mutableStateOf<com.liujii.ledger.data.MoneyType?>(null) }
    var addingEditorSession by rememberSaveable { mutableLongStateOf(0) }
    var showAccounts by rememberSaveable { mutableStateOf(false) }
    var focusBudget by rememberSaveable { mutableStateOf(false) }
    val screenState=rememberSaveableStateHolder()
    val accounts by vm.accounts.collectAsState()
    val settings = vm.appearance.value
    val items = listOf(NavItem("首页", Icons.Default.Home), NavItem("明细", Icons.AutoMirrored.Filled.ReceiptLong), NavItem("统计", Icons.AutoMirrored.Filled.ShowChart), NavItem("日历", Icons.Default.CalendarMonth), NavItem("设置", Icons.Default.Tune))
    BackHandler(enabled=showAccounts || page!=0) {
        when {
            showAccounts -> showAccounts=false
            else -> page=0
        }
    }
    LaunchedEffect(shortcutAction,accounts.size) {
        when(shortcutAction) {
            MainActivity.SHORTCUT_EXPENSE -> {addingEditorSession++;addingType=com.liujii.ledger.data.MoneyType.EXPENSE}
            MainActivity.SHORTCUT_INCOME -> {addingEditorSession++;addingType=com.liujii.ledger.data.MoneyType.INCOME}
            MainActivity.SHORTCUT_DETAILS -> page=1
        }
        if(shortcutAction!=null)onShortcutConsumed()
    }
    Background(settings) {
        if(showAccounts) {
            BooksScreen(vm,onBack={showAccounts=false})
        } else {
        screenState.SaveableStateProvider(page) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha=.94f)) {
                    items.forEachIndexed { index, item -> NavigationBarItem(selected=page==index, onClick={page=index}, icon={Icon(item.icon, item.label)}, label={Text(item.label)}) }
                }
            },
            floatingActionButton = { if(page < 4 && page != 1) FloatingActionButton(onClick={addingEditorSession++;addingType=com.liujii.ledger.data.MoneyType.EXPENSE}, containerColor=Mint, contentColor=Color.White) { Icon(Icons.Default.Add, "快速记账") } },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when(page) {
                    0 -> HomeScreen(vm, onSeeAll={page=1}, onManageBudget={focusBudget=true;page=4},onManageAccounts={showAccounts=true})
                    1 -> TransactionsScreen(vm,onAddTransaction={addingEditorSession++;addingType=com.liujii.ledger.data.MoneyType.EXPENSE})
                    2 -> StatsScreen(vm)
                    3 -> CalendarScreen(vm)
                    else -> SettingsScreen(vm, focusBudget=focusBudget, onBudgetFocused={focusBudget=false},onManageAccounts={showAccounts=true})
                }
            }
        }
        }
        }
    }
    addingType?.let { type ->
        val session=addingEditorSession
        key(session){TransactionEditor(vm=vm,initialType=type,onDismiss={if(addingEditorSession==session)addingType=null})}
    }
    vm.operationNotice.value?.let{message->AlertDialog(onDismissRequest={vm.operationNotice.value=null},title={Text("提示")},text={Text(message)},confirmButton={TextButton(onClick={vm.operationNotice.value=null}){Text("知道了")}})}
}
