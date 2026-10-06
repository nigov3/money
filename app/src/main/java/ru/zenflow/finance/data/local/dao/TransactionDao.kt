package ru.zenflow.finance.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.zenflow.finance.core.model.TransactionType
import ru.zenflow.finance.data.local.converters.FinanceConverters
import ru.zenflow.finance.data.local.entity.AccountEntity
import ru.zenflow.finance.data.local.entity.CategoryEntity
import ru.zenflow.finance.data.local.entity.TransactionEntity

/**
 * DAO транзакций. Все read-запросы возвращают [Flow] — UI и аналитика
 * обновляются автоматически при любой вставке из сервисов-перехватчиков.
 */
@Dao
@TypeConverters(FinanceConverters::class)
interface TransactionDao {

    /**
     * Вставка кандидата из парсера. Конфликт по UNIQUE(dedup_key) игнорируется:
     * push и SMS об одном событии не создадут дубль.
     * @return -1, если это дубликат (INSERT OR IGNORE).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    /** Soft-delete: строка остаётся для целостности истории, но уходит из выборок */
    @Query("UPDATE transactions SET deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    // ---------------- Лента главного экрана ---------------- //

    /** Последние N непустых транзакций с именами категории и счёта (JOIN вместо N+1). */
    @Transaction
    @Query(
        """
        SELECT t.* FROM transactions t
        WHERE t.deleted = 0
        ORDER BY t.occurred_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getRecentPage(limit: Int, offset: Int): Flow<List<TransactionWithRefs>>

    // ---------------- Сводка за месяц (главный экран) ---------------- //

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_minor ELSE 0 END), 0)
        FROM transactions
        WHERE deleted = 0 AND occurred_at BETWEEN :from AND :to
        """
    )
    fun observeTotalExpensesMinor(from: Long, to: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_minor ELSE 0 END), 0)
        FROM transactions
        WHERE deleted = 0 AND occurred_at BETWEEN :from AND :to
        """
    )
    fun observeTotalIncomeMinor(from: Long, to: Long): Flow<Long>

    // ---------------- Агрегация для аналитики ---------------- //

    /** Pie chart: сумма по категориям за период. NULL-категория -> "Без категории". */
    @Query(
        """
        SELECT c.id            AS categoryId,
               COALESCE(c.name, 'Без категории') AS categoryName,
               COALESCE(c.color, -526345)        AS color,
               SUM(t.amount_minor)               AS totalMinor
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.deleted = 0
          AND t.type = 'EXPENSE'
          AND t.occurred_at BETWEEN :from AND :to
        GROUP BY t.category_id
        ORDER BY totalMinor DESC
        """
    )
    fun observeSpendByCategory(from: Long, to: Long): Flow<List<CategorySpendAgg>>

    /**
     * Bar chart: траты по дням периода. Группировка по дню календаря — на SQL
     * (strftime + localized_start_of_day), чтобы не тянуть все строки в память.
     * :tzOffsetMillis — смещение локали (VM считает через ZoneRules, без android API).
     */
    @Query(
        """
        SELECT (occurred_at + :tzOffsetMillis) / 86400000 AS dayEpoch,
               SUM(amount_minor)                          AS totalMinor
        FROM transactions
        WHERE deleted = 0 AND type = 'EXPENSE'
          AND occurred_at BETWEEN :from AND :to
        GROUP BY dayEpoch
        ORDER BY dayEpoch ASC
        """
    )
    fun observeDailySpend(from: Long, to: Long, tzOffsetMillis: Long): Flow<List<DailySpendAgg>>

    /** Агрегация по месяцам (для тренда «расходы по месяцам» и фильтра «месяц»). */
    @Query(
        """
        SELECT strftime('%Y-%m', (:tzOffsetMillis + occurred_at) / 1000, 'unixepoch') AS monthKey,
               SUM(amount_minor) AS totalMinor
        FROM transactions
        WHERE deleted = 0 AND type = 'EXPENSE'
          AND occurred_at BETWEEN :from AND :to
        GROUP BY monthKey
        ORDER BY monthKey ASC
        """
    )
    fun observeMonthlySpend(from: Long, to: Long, tzOffsetMillis: Long): Flow<List<MonthSpendAgg>>

    /** Проверка существования дубля перед вставкой (быстрый путь без исключения). */
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE dedup_key = :key)")
    suspend fun existsByDedupKey(key: String): Boolean

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0")
    fun observeCount(): Flow<Int>
}

// ---------------- Projection-классы для агрегирующих запросов ---------------- //

data class CategorySpendAgg(
    val categoryId: Long?,
    val categoryName: String,
    val color: Int,
    val totalMinor: Long,
)

/** Одна точка гистограммы: день (epoch day в локальной зоне) + сумма расходов. */
data class DailySpendAgg(
    val dayEpoch: Long,
    val totalMinor: Long,
)

/** Сумма расходов за календарный месяц ("2026-10"). */
data class MonthSpendAgg(
    val monthKey: String,
    val totalMinor: Long,
)

/** Транзакция + связанные сущности за один запрос (Room resolved relations). */
data class TransactionWithRefs(
    @Embedded val transaction: TransactionEntity,

    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: CategoryEntity?,

    @Relation(parentColumn = "account_id", entityColumn = "id")
    val account: AccountEntity?,
)
