package com.liujii.ledger.reminder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.app.PendingIntent
import com.liujii.ledger.MainActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.liujii.ledger.R
import com.liujii.ledger.data.LedgerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT>=33 && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch {
        try {
        // 通知前读取当前规则，旧广播不能为已暂停、已删除或已入账的规则发提醒。
        val rule=LedgerDatabase.get(context).dao().recurringById(intent.getLongExtra("id",0))?:return@launch
        if(!rule.active || (rule.endAt!=null&&rule.nextAt>rule.endAt) || (intent.hasExtra("nextAt")&&intent.getLongExtra("nextAt",0)!=rule.nextAt))return@launch
        val title = rule.title
        val amount = java.math.BigDecimal.valueOf(rule.amountCents,2).toPlainString()
        val type = if (rule.type == com.liujii.ledger.data.MoneyType.INCOME) "收入" else "支出"
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setContentIntent(PendingIntent.getActivity(context,intent.getLongExtra("id",0).toInt(),Intent(context,MainActivity::class.java).putExtra(MainActivity.EXTRA_SHORTCUT_ACTION,MainActivity.SHORTCUT_DETAILS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("待记录$type ¥$amount")
            .setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(intent.getLongExtra("id", 0).toInt(), notification)
        } catch(error:Exception) {
            android.util.Log.w("LiuJiReminder","未能显示通知，账单仍保留在应用内",error)
        } finally {pending.finish()}
        }
    }
}
