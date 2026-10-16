package com.liujii.ledger.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateChoiceDialog(initial:LocalDate,onDismiss:()->Unit,onSelected:(LocalDate)->Unit) {
    val state=rememberDatePickerState(initialSelectedDateMillis=initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(onDismissRequest=onDismiss,confirmButton={TextButton(onClick={state.selectedDateMillis?.let{onSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())}},enabled=state.selectedDateMillis!=null){Text("确定")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}}){DatePicker(state)}
}
