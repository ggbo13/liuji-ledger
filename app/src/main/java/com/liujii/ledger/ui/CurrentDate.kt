package com.liujii.ledger.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

internal fun currentDateAt(now: Instant, zone: ZoneId): LocalDate = now.atZone(zone).toLocalDate()

internal fun millisUntilNextLocalMidnight(now: Instant, zone: ZoneId): Long {
    val midnight = currentDateAt(now, zone).plusDays(1).atStartOfDay(zone).toInstant()
    // 按当地下一天的起点计算，兼容夏令时及午夜发生的时区偏移。
    return Duration.between(now, midnight).toMillis().coerceAtLeast(1L)
}

private val systemDateClock: Clock = Clock.systemUTC()
private data class CurrentDateSnapshot(val date: LocalDate, val zone: ZoneId)

@Composable
internal fun rememberCurrentDate(): LocalDate = rememberCurrentDate(systemDateClock) { ZoneId.systemDefault() }

@Composable
internal fun rememberCurrentDate(clock: Clock, zoneProvider: () -> ZoneId): LocalDate {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestZoneProvider by rememberUpdatedState(zoneProvider)
    var snapshot by remember(clock) {
        val zone = zoneProvider()
        mutableStateOf(CurrentDateSnapshot(currentDateAt(clock.instant(), zone), zone))
    }
    LaunchedEffect(context, lifecycleOwner, clock) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val changes = Channel<Unit>(Channel.CONFLATED)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    changes.trySend(Unit)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            try {
                while (isActive) {
                    val now = clock.instant()
                    val zone = latestZoneProvider()
                    // 同一天切换时区也触发重组，使汇总重新采用当地日界线。
                    snapshot = CurrentDateSnapshot(currentDateAt(now, zone), zone)
                    withTimeoutOrNull(millisUntilNextLocalMidnight(now, zone)) { changes.receive() }
                }
            } finally {
                context.unregisterReceiver(receiver)
                changes.close()
            }
        }
    }
    return snapshot.date
}
