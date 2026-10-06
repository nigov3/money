package ru.zenflow.finance.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * КОШЕЛЁК / СЧЁТ (карта, наличные, накопительный счёт).
 * amount хранится как amountMinor — целое в копейках/центах (×100),
 * чтобы избежать ошибок float при суммировании. См. CurrencyConverters.
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["is_archived"], name = "idx_accounts_active")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                          // "Т-Банк Дебетовая ***4529"
    val currency: String,                      // "RUB"
    @ColumnInfo(name = "initial_balance_minor") val initialBalanceMinor: Long = 0,
    /** Текущий баланс считаем производным (initial + транзакции), но кэшируем здесь */
    @ColumnInfo(name = "cached_balance_minor") val cachedBalanceMinor: Long = 0,
    @ColumnInfo(name = "source_package") val sourcePackage: String? = null, // привязка к банку-источнику
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/**
 * КАТЕГОРИЯ ТРАТ (деревдо 2 уровня: parentId).
 * system=true — нельзя удалять (базовый набор: Продукты, Транспорт...).
 */
@Entity(
    tableName = "categories",
    indices = [
        Index("parent_id"),
        Index(value = ["name"], unique = true),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    /** ARGB-int — цвет на pie-chart; не требует миграции при смене палитры */
    val color: Int,
    /** Иконка из Material Icons Extended по имени ("ShoppingCart") */
    val icon: String,
    val system: Boolean = false,
    /** Regex-словарь автокатегоризации: "пятёр|магнит|лента" -> Продукты */
    @ColumnInfo(name = "match_pattern") val matchPattern: String? = null,
)

/**
 * ТРАНЗАКЦИЯ — центральная сущность.
 *
 * Индексы спроектированы под три горячих запроса аналитики:
 *  1) лента последних транзакций          -> idx_tx_occurred_at
 *  2) агрегация по категориям за период   -> idx_tx_category_occurred
 *  3) дедупликация push/SMS-дублей        -> UNIQUE(dedup_key) + REPLACE strategy
 *  4) выборка по счетчику/счёту           -> idx_tx_account
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,       // удалили счёт — транзакции с ним тоже
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,      // удалили категорию — траты останутся «без категории»
        ),
    ],
    indices = [
        Index("account_id"),
        Index("category_id"),
        Index(value = ["occurred_at"], name = "idx_tx_occurred_at"),
        Index(value = ["category_id", "occurred_at"], name = "idx_tx_category_occurred"),
        Index(value = ["type", "occurred_at"], name = "idx_tx_type_occurred"),
        Index(value = ["dedup_key"], unique = true, name = "idx_tx_dedup"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,

    /** magnitude всегда > 0; знак выводится из type (EXPENSE — минус в UI и балансе) */
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,                       // "RUB"

    /** EXPENSE | INCOME | TRANSFER (enum TypeConverter) */
    val type: ru.zenflow.finance.core.model.TransactionType,

    val merchant: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),

    /** NOTIFICATION | SMS | MANUAL — источник данных */
    val source: ru.zenflow.finance.core.model.CaptureChannel?,

    @ColumnInfo(name = "dedup_key") val dedupKey: String,
    @ColumnInfo(name = "raw_text") val rawText: String? = null,
    @ColumnInfo(name = "parser_id") val parserId: String? = null,

    /** Для переводов между своими: ссылка на парную транзакцию (second account leg) */
    @ColumnInfo(name = "transfer_peer_id") val transferPeerId: Long? = null,

    val note: String? = null,
    val deleted: Boolean = false,               // soft-delete: аналитика не должна «терять» историю
)

/**
 * БЮДЖЕТ на категорию за период.
 * period: MONTH | WEEK — гранулярность сброса лимита.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("category_id"), Index(value = ["category_id", "period"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "category_id") val categoryId: Long?, // null = общий бюджет на всё
    @ColumnInfo(name = "limit_minor") val limitMinor: Long,
    val period: Period,
    val currency: String = "RUB",
    val isActive: Boolean = true,
)

enum class Period { WEEK, MONTH }
