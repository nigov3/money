package ru.zenflow.finance.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.zenflow.finance.core.model.TransactionType
import ru.zenflow.finance.data.local.dao.AccountDao
import ru.zenflow.finance.data.local.dao.CategoryDao
import ru.zenflow.finance.data.local.dao.TransactionDao
import ru.zenflow.finance.data.local.entity.AccountEntity
import ru.zenflow.finance.data.local.entity.CategoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/** UI-модель строки транзакции: всё, что нужно ленте, без android.* и Room-аннотаций. */
data class TxRow(
    val id: Long,
    val title: String,          // merchant ?: category ?: «Без названия»
    val subtitle: String,       // категория · счёт
    val amountMinor: Long,      // magnitude > 0
    val type: TransactionType,
    val currency: String,
    val occurredAt: Long,
    val categoryId: Long?,
    val categoryName: String?,
)

/** Срез категории для pie-chart / списка «топ категорий». */
data class CategorySlice(
    val categoryId: Long?,
    val name: String,
    val totalMinor: Long,
    val share: Float,           // 0..1 от итога периода
)

/** Точка гистограммы по дням. dayEpoch — номер дня от epoch в локальной зоне. */
data class DayBar(val dayEpoch: Long, val totalMinor: Long)

/** Период аналитики (границы считаем в JVM-классах — ViewModel тестируется без Robolectric). */
enum class AnalyticsPeriod { TODAY, WEEK, MONTH, QUARTER, YEAR }

/**
 * Репозиторий — единственная точка доступа к финансам для presentation-слоя.
 * Domain-слой не знает про Room; сервисы пишут через use-case, UI читает здесь.
 */
@Singleton
class FinanceRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
) {

    fun observeRecent(limit: Int = 200): Flow<List<TxRow>> =
        transactionDao.getRecentPage(limit, 0).map { page ->
            page.map { it.toRow() }
        }

    fun observeTotalExpenses(from: Long, to: Long): Flow<Long> =
        transactionDao.observeTotalExpensesMinor(from, to)

    fun observeTotalIncome(from: Long, to: Long): Flow<Long> =
        transactionDao.observeTotalIncomeMinor(from, to)

    /** Pie chart: топ-категории расходов за период с долями. */
    fun observeCategorySlices(from: Long, to: Long): Flow<List<CategorySlice>> =
        transactionDao.observeSpendByCategory(from, to).map { rows ->
            val total = rows.sumOf { it.totalMinor }.coerceAtLeast(1L)
            rows.map { CategorySlice(it.categoryId, it.categoryName, it.totalMinor, it.totalMinor.toFloat() / total) }
        }

    /** Bar chart: расходы по дням. tzOffsetMillis — смещение локали (ZoneId.of(...)). */
    fun observeDailySpend(from: Long, to: Long, tzOffsetMillis: Long): Flow<List<DayBar>> =
        transactionDao.observeDailySpend(from, to, tzOffsetMillis)
            .map { it.map { r -> DayBar(r.dayEpoch, r.totalMinor) } }

    fun observeMonthlySpend(from: Long, to: Long, tzOffsetMillis: Long) =
        transactionDao.observeMonthlySpend(from, to, tzOffsetMillis)

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()
    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeActiveAccounts()

    suspend fun softDeleteTransaction(id: Long) = transactionDao.softDelete(id)

    private fun ru.zenflow.finance.data.local.dao.TransactionWithRefs.toRow(): TxRow {
        val t = transaction
        return TxRow(
            id = t.id,
            title = t.merchant?.takeIf { it.isNotBlank() }
                ?: category?.name
                ?: "Без названия",
            subtitle = listOfNotNull(
                category?.name,
                account?.name?.let { "· $it" },
            ).joinToString(" "),
            amountMinor = t.amountMinor,
            type = t.type,
            currency = t.currency,
            occurredAt = t.occurredAt,
            categoryId = t.categoryId,
            categoryName = category?.name,
        )
    }

    companion object {
        /** Границы периода [from..to] включительно, epoch millis в системной зоне. */
        fun periodRange(p: AnalyticsPeriod, nowMillis: Long): Pair<Long, Long> {
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
            val start: java.time.LocalDate = when (p) {
                AnalyticsPeriod.TODAY -> today
                AnalyticsPeriod.WEEK -> today.minusDays(6)
                AnalyticsPeriod.MONTH -> today.withDayOfMonth(1)
                AnalyticsPeriod.QUARTER -> today.minusMonths(3).withDayOfMonth(1).plusMonths(1) // скользящий квартал
                AnalyticsPeriod.YEAR -> today.minusMonths(11).withDayOfMonth(1)
            }
            val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
            val toEnd = if (p == AnalyticsPeriod.TODAY) today.plusDays(1) else today.plusDays(1)
            val to = toEnd.atStartOfDay(zone).toInstant().toEpochMilli() - 1
            return from to to
        }

        fun tzOffsetMillis(nowMillis: Long): Long =
            java.time.ZoneId.systemDefault().rules.getOffset(java.time.Instant.ofEpochMilli(nowMillis)).totalSeconds * 1000L
    }
}
