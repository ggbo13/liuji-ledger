package com.liujii.ledger

import android.app.Application
import com.liujii.ledger.data.LedgerDatabase
import com.liujii.ledger.data.LedgerRepository
import com.liujii.ledger.reminder.ReminderScheduler

class LiuJiApplication : Application() {
    lateinit var repository: LedgerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = LedgerRepository(this, LedgerDatabase.get(this).dao())
        ReminderScheduler.createChannel(this)
    }
}
