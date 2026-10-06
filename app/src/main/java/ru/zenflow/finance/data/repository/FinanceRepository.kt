package ru.zenflow.finance.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.zenflow.finance.core.budget.BudgetPeriodCalculator
import ru.zenflow.finance.core.model.TransactionType
import ru.zenflow.finance.data.local.FinanceDatabase
import ru.zenflow.finance.data.local.dao.AccountDao
import ru.zenflow.finance.data.local.dao.BudgetDao
import ru.zenflow.finance.data.local.dao.CategoryDao
import ru.zenflow.finance.data.local.dao.TransactionDao
import ru.zenflow.finance.data.local.entity.AccountEntity
import ru.zenflow.finance.data.local.entity.BudgetEntity
import ru.zenflow.finance.data.local.entity.CategoryEntity
import ru.zenflow.finance.data.local.entity.Period
import ru.zenflow.finance.data.local.entity.TransactionEntity
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
    private val budgetDao: BudgetDao,
    private val db: FinanceDatabase,
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

    // ==================== Этап 3: правка / удаление / ручное добавление ==================== //

    /** Полная карточка транзакции для экрана «Детали/Правка» (одноразовый снимок). */
    suspend fun getTransactionFull(id: Long) = transactionDao.getFullById(id)

    /**
     * Ручная вставка транзакции (FAB «Добавить»). source == null — это MANUAL,
     * dedup_key генерируем с префиксом "manual:" — он никогда не столкнётся с
     * хешем банковского сообщения.
     */
    suspend fun addManualTransaction(
        accountId: Long,
        categoryId: Long?,
        amountMinor: Long,
        type: TransactionType,
        merchant: String?,
        occurredAt: Long,
        note: String?,
        currency: String = "RUB",
    ): Long = db.withTransaction {
        val id = transactionDao.insertIfNew(
            TransactionEntity(
                accountId = accountId,
                categoryId = categoryId,
                amountMinor = amountMinor.coerceAtLeast(1L),
                currency = currency,
                type = type,
                merchant = merchant?.takeIf { it.isNotBlank() },
                occurredAt = occurredAt,
                source = null, // null-источник = ручной ввод; см. CaptureChannel
                dedupKey = "manual:${System.currentTimeMillis()}:${amountMinor.hashCode()}",
                note = note?.takeIf { it.isNotBlank() },
            )
        )
        if (id != -1L) accountDao.applyBalanceDelta(accountId, amountMinor, type.name)
        id
    }

    /**
     * Сохранение правки. Баланс пересчитываем НЕ инкрементально, а прямым
     * UPDATE по формуле initial + Σ(sign·amount) — так правка суммы/типа
     * автоматически чинит кэш без сложных дельт «old vs new».
     */
    suspend fun editTransaction(
        id: Long,
        categoryId: Long?,
        amountMinor: Long,
        type: TransactionType,
        merchant: String?,
        occurredAt: Long,
        note: String?,
    ) = db.withTransaction {
        val full = transactionDao.getFullById(id) ?: return@withTransaction
        transactionDao.updateEditableFields(
            id = id,
            categoryId = categoryId,
            amountMinor = amountMinor.coerceAtLeast(1L),
            type = type.name,
            merchant = merchant?.takeIf { it.isNotBlank() },
            occurredAt = occurredAt,
            note = note?.takeIf { it.isNotBlank() },
        )
        recomputeAccountBalance(full.accountId)
    }

    /** Soft-delete + пересчёт баланса счёта (в одной транзакции — нет «мигающего» баланса). */
    suspend fun deleteTransaction(id: Long) = db.withTransaction {
        val full = transactionDao.getFullById(id) ?: return@withTransaction
        transactionDao.softDelete(id)
        recomputeAccountBalance(full.accountId)
    }

    private suspend fun recomputeAccountBalance(accountId: Long) {
        val acc = accountDao.getById(accountId) ?: return
        val live = transactionDao.getAllLiveForAccount(accountId)
        val sum = live.sumOf { t ->
            when (t.type) {
                TransactionType.EXPENSE -> -t.amountMinor
                else -> t.amountMinor // INCOME и приходные ноги TRANSFER плюсуем
            }
        }
        accountDao.setBalance(accountId, acc.initialBalanceMinor + sum)
    }

    // ==================== Категории ==================== //

    suspend fun createCategory(name: String, colorArgb: Int, icon: String, matchPattern: String?): Long =
        categoryDao.insert(
            CategoryEntity(
                name = name.trim(),
                color = colorArgb,
                icon = icon,
                system = false,
                matchPattern = matchPattern?.takeIf { it.isNotBlank() },
            )
        )

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    // ==================== Бюджеты ==================== //

    /** upsert лимита: UNIQUE(category_id, period) гарантирует один бюджет на связку. */
    suspend fun saveBudget(categoryId: Long?, limitMinor: Long, period: Period): Long =
        budgetDao.upsert(BudgetEntity(categoryId = categoryId, limitMinor = limitMinor, period = period))

    suspend fun deleteBudget(id: Long) = budgetDao.deleteById(id)

    fun observeBudgets(): Flow<List<BudgetEntity>> = budgetDao.observeActive()

    /**
     * Живой статус всех бюджетов: spent из SQL-агрегации за текущий цикл +
     * spent за предыдущий (для подсказки «средний расход»). Чистая функция
     * сборки статуса — BudgetPeriodCalculator тестируется отдельно.
     */
    fun observeBudgetStatuses(nowMillis: Long): Flow<List<BudgetStatus>> {
        val monthWin = BudgetPeriodCalculator.windowsFor(BudgetPeriodCalculator.BudgetPeriod.MONTH, nowMillis)
        val weekWin = BudgetPeriodCalculator.windowsFor(BudgetPeriodCalculator.BudgetPeriod.WEEK, nowMillis)
        return combine4(
            budgetDao.observeActive(),
            categoryDao.observeAll(),
            transactionDao.observeSpentByCategoryInRange(monthWin.currentFrom, monthWin.currentTo),
            transactionDao.observeTotalSpentInRange(monthWin.currentFrom, monthWin.currentTo),
        ) { budgets, categories, monthSpent, monthTotal ->
            val byCat = monthSpent.associate { it.categoryId to it.spentMinor }
            budgets.map { b ->
                val win = if (b.period == Period.WEEK) weekWin else monthWin
                val spent = if (b.categoryId == null) monthTotal.spentMinor
                            else byCat[b.categoryId] ?: 0L
                BudgetStatus(
                    budget = b,
                    categoryName = categories.firstOrNull { it.id == b.categoryId }?.name
                        ?: "Общий бюджет",
                    spentMinor = spent,
                    progress = BudgetPeriodCalculator.progress(spent, b.limitMinor),
                    cycleFrom = win.currentFrom,
                    cycleTo = win.currentTo,
                )
            }.sortedByDescending { it.progress }
        }
    }

    /** Строка UI-экрана «Бюджеты»: всё готово к отрисовке progress-бара. */
    data class BudgetStatus(
        val budget: BudgetEntity,
        val categoryName: String,
        val spentMinor: Long,
        val progress: Float,          // >1 => превышение, UI красит в expense-цвет
        val cycleFrom: Long,
        val cycleTo: Long,
    )

    /** combine для 4 потоков (stdlib-перегрузка даёт vararg-деструктуризацию). */
    private fun <A, B, C, D, R> combine4(
        a: Flow<A>, b: Flow<B>, c: Flow<C>, d: Flow<D>,
        transform: suspend (A, B, C, D) -> R,
    ): Flow<R> = kotlinx.coroutines.flow.combine(a, b, c, d) { array ->
        @Suppress("UNCHECKED_CAST") // типы гарантированы аргументами a..d
        val (x, y, z, w) = array as Array<Any?>
        transform(x as A, y as B, z as C, w as D)
    }

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
