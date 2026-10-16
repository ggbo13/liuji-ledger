package com.liujii.ledger.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun moneyType(value: String): MoneyType = MoneyType.valueOf(value)
    @TypeConverter fun moneyType(value: MoneyType): String = value.name
    @TypeConverter fun repeatType(value: String): RepeatType = RepeatType.valueOf(value)
    @TypeConverter fun repeatType(value: RepeatType): String = value.name
    @TypeConverter fun accountType(value: String): AccountType = AccountType.valueOf(value)
    @TypeConverter fun accountType(value: AccountType): String = value.name
}
