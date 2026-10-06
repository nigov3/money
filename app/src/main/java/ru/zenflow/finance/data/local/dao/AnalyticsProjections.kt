package ru.zenflow.finance.data.local.dao

import androidx.room.ColumnInfo

/**
 * Projection-классы для «сырых» запросов этапа 3 (правка транзакций и бюджеты).
 * Отдельный файл, чтобы DAO-интерфейсы оставали читыми; Room мапит колонки
 * по именам алиасов — никаких аннотаций на полях не нужно.
 */

/** Одна строка таблицы transactions + имена связанных сущностей (для экрана правки). */
data class TransactionFull(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "type") val type: String,               // TEXT через TypeConverter
    @ColumnInfo(name = "merchant") val merchant: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "deleted") val deleted: Boolean,
    @ColumnInfo(name = "categoryName") val categoryName: String?,
    @ColumnInfo(name = "accountName") val accountName: String?,
)

/** Траты категории за интервал [from..to] — одна строка на категорию. */
data class CategorySpentInRange(
    @ColumnInfo(name = "categoryId") val categoryId: Long?,
    @ColumnInfo(name = "spentMinor") val spentMinor: Long,
)

/** Траты ВСЕХ категорий за интервал одним числом (для общего бюджета, categoryId = null). */
data class TotalSpentInRange(
    @ColumnInfo(name = "spentMinor") val spentMinor: Long,
)
