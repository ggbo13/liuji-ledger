package com.liujii.ledger.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.liujii.ledger.data.LedgerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_TIME_CHANGED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LedgerDatabase.get(context).dao().recurringRules().first().forEach { ReminderScheduler.schedule(context, it) }
            } catch(error:Exception) {
                android.util.Log.w("LiuJiReminder","恢复提醒失败，下次打开应用可查看待入账账单",error)
            } finally { pending.finish() }
        }
    }
}
