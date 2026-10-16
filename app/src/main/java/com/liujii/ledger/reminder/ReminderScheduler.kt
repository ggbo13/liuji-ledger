package com.liujii.ledger.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.liujii.ledger.data.RecurringRuleEntity
import java.time.Instant
import java.time.ZoneId

object ReminderScheduler {
    const val CHANNEL_ID = "recurring_bill"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "周期账单", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "周期收入和支出的到期提醒" })
    }

    fun schedule(context: Context, rule: RecurringRuleEntity): Boolean = runCatching {
        cancel(context, rule.id)
        if (!rule.active || (rule.endAt!=null&&rule.nextAt>rule.endAt)) return@runCatching true
        val next = Instant.ofEpochMilli(rule.nextAt).atZone(ZoneId.systemDefault()).toLocalDate()
            .minusDays(rule.remindDaysBefore.toLong()).atTime(rule.remindHour, rule.remindMinute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (next <= System.currentTimeMillis()) return@runCatching true
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("id", rule.id).putExtra("nextAt",rule.nextAt).putExtra("title", rule.title).putExtra("amount", rule.amountCents).putExtra("type", rule.type.name)
        val pending = PendingIntent.getBroadcast(context, rule.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        true
    }.getOrElse {
        // 厂商限制后台调度时，数据仍已保存，首页继续显示待入账账单。
        android.util.Log.w("LiuJiReminder", "系统未接受提醒调度", it)
        false
    }

    fun cancel(context: Context, ruleId: Long): Boolean = runCatching {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, ruleId.toInt(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) {
            context.getSystemService(AlarmManager::class.java).cancel(pending)
            pending.cancel()
        }
        true
    }.getOrElse {
        android.util.Log.w("LiuJiReminder", "系统未接受取消提醒", it)
        false
    }
}
