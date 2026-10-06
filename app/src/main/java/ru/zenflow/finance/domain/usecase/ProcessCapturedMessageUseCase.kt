package ru.zenflow.finance.domain.usecase

import kotlinx.coroutines.flow.first
import ru.zenflow.finance.core.model.ParsedTransaction
import ru.zenflow.finance.core.model.RawMessage
import ru.zenflow.finance.core.parser.TransactionParser
import ru.zenflow.finance.data.local.dao.AccountDao
import ru.zenflow.finance.data.local.dao.CategoryDao
import ru.zenflow.finance.data.local.dao.TransactionDao
import ru.zenflow.finance.data.local.entity.TransactionEntity
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ЕДИНАЯ ТОЧКА ВХОДА для обоих перехватчиков (уведомления и SMS).
 *
 * Сервисы делают только «сбор → RawMessage → этот use-case». Вся логика
 * дедупликации, привязки к счёту, автокатегоризации и записи баланса — здесь.
 * Это позволяет тестировать сквозной путь без Android-инфраструктуры.
 */
@Singleton
class ProcessCapturedMessageUseCase @Inject constructor(
    private val parser: TransactionParser,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
) {

    /** @return true, если транзакция распознана И сохранена (не дубль). */
    suspend operator fun invoke(message: RawMessage): Boolean {
        // 1. Парсинг (чистый CPU, синхронно — regex быстрый)
        val parsed = parser.parse(message) ?: return false

        // 2. Эвристики отбрасывания мусора ДО записи в БД
        if (!isPlausible(parsed)) return false

        // 3. Поиск аккаунта: сначала по пакету банка, иначе — первый активный (дефолтный счёт)
        val account = accountDao.findBySourcePackage(message.sourcePackage.orEmpty())
            ?: accountDao.observeActiveAccounts().first().firstOrNull()
            ?: return false // нет ни одного счёта — копить некуда; UI покажет онбординг

        // 4. Автокатегоризация по merchant-словарю
        val categoryId = autoCategorize(parsed.merchant ?: parsed.categoryHint)

        // 5. Запись (INSERT OR IGNORE по dedup_key решает гонку push-vs-SMS атомарно)
        val entity = TransactionEntity(
            accountId = account.id,
            categoryId = categoryId,
            amountMinor = toMinorUnits(parsed),
            currency = parsed.currency.code,
            type = parsed.type,
            merchant = parsed.merchant,
            occurredAt = parsed.occurredAt,
            source = message.channel,
            dedupKey = parsed.dedupKey,
            rawText = parsed.rawText,
            parserId = parsed.parserId,
        )
        val rowId = transactionDao.insertIfNew(entity)
        if (rowId == -1L) return false // дубликат

        // 6. Инкремент кэша баланса — одним SQL UPDATE (без read-modify-write)
        accountDao.applyBalanceDelta(account.id, entity.amountMinor, entity.type.name)
        return true
    }

    // ------------------------------------------------------------------ //

    /**
     * Sanity-check результата парсинга: отбрасываем абсурдные суммы и
     * «бонусные» сообщения (кэшбек баллами — частая ложная находка шаблонов).
     */
    private fun isPlausible(p: ParsedTransaction): Boolean {
        if (p.amount.signum() <= 0) return false
        if (p.amount > MAX_PLAUSIBLE) return false
        val pointsContext = listOf("балл", "бонус", "mpp").any { p.rawText.contains(it, true) }
        val hasMoneyMarker = p.rawText.any { it == '₽' || it == '$' || it == '€' } ||
            p.rawText.contains(Regex("""(?i)(руб|usd|eur|ruble)"""))
        return !pointsContext || hasMoneyMarker
    }

    /** RUB: копейки. Валюты с 2 знаками — универсально ×100. */
    private fun toMinorUnits(p: ParsedTransaction): Long =
        p.amount.movePointRight(2).toLong()

    /**
     * Простейший словарь категорий: берём категории с match_pattern и ищем
     * совпадение по названию магазина (регистронезависимо).
     * MCC-база и ML-классификатор подключим на этапе «Автокатегоризация v2».
     */
    private suspend fun autoCategorize(hint: String?): Long? {
        if (hint.isNullOrBlank()) return null
        val patterned = categoryDao.getPatternedCategories()
        return patterned.firstOrNull { cat ->
            runCatching { Regex(cat.matchPattern!!, RegexOption.IGNORE_CASE).containsMatchIn(hint) }
                .getOrDefault(false)
        }?.id
    }

    private companion object {
        val MAX_PLAUSIBLE = BigDecimal("10000000") // 10 млн — потолок разовой траты
    }
}
