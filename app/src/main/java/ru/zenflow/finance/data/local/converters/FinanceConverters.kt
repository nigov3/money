package ru.zenflow.finance.data.local.converters

import androidx.room.TypeConverter
import ru.zenflow.finance.core.model.CaptureChannel
import ru.zenflow.finance.core.model.TransactionType
import ru.zenflow.finance.data.local.entity.Period

/**
 * TypeConverters для enum-полей. Храним ИМЯ enum как TEXT (а не ordinal Int):
 * при добавлении новых значений в середину перечисления старые записи не «поедут».
 */
class FinanceConverters {

    @TypeConverter
    fun fromTransactionType(value: TransactionType?): String? = value?.name

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType? =
        value?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromCaptureChannel(value: CaptureChannel?): String? = value?.name

    @TypeConverter
    fun toCaptureChannel(value: String?): CaptureChannel? =
        value?.let { runCatching { CaptureChannel.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromPeriod(value: Period?): String? = value?.name

    @TypeConverter
    fun toPeriod(value: String?): Period? =
        value?.let { runCatching { Period.valueOf(it) }.getOrNull() }
}
