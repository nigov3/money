package ru.zenflow.finance.core.parser

import ru.zenflow.finance.core.model.CurrencyCode
import ru.zenflow.finance.core.model.ParsedTransaction
import ru.zenflow.finance.core.model.RawMessage
import ru.zenflow.finance.core.model.TransactionType

/**
 * ПУБЛИЧНЫЙ КОНТРАКТ модуля парсинга.
 *
 * Реализация [RegexTransactionParser] принимает любой [RawMessage] и пытается
 * извлечь из него транзакцию. Интерфейс нужен, чтобы:
 *  1) подменить парсер в тестах fake-реализацией;
 *  2) в будущем добавить параллельную реализацию (ML/on-device классификатор)
 *     и выбирать между ними через Strategy-слой.
 */
interface TransactionParser {
    /** @return null, если сообщение не похоже на финансовое событие. */
    fun parse(message: RawMessage): ParsedTransaction?

    /** Идентификаторы активных шаблонов — для экрана «Что научился понимать app». */
    fun activeTemplates(): List<String>
}

/**
 * Один Regex-шаблон банка.
 *
 * Принцип расширения: чтобы поддержать новый банк, достаточно добавить ещё
 * один [BankTemplate] в список реестра — код сервисов менять не нужно.
 *
 * @param id            уникальный ключ шаблона ("sberbank", "tinkoff", ...)
 * @param packageMatcher фильтр по пакету источника (null = не фильтровать,
 *                        используется для SMS, где пакета нет)
 * @param senderMatcher  фильтр по отправителю SMS (regex на адрес: "900", "SBERBANK"...)
 * @param rules          упорядоченные правила извлечения полей из текста
 */
data class BankTemplate(
    val id: String,
    val displayName: String,
    val packageMatcher: Regex?,
    val senderMatcher: Regex?,
    val rules: List<ExtractionRule>,
)

/**
 * Правило извлечения одного поля. Имена групп regex обязательны и образуют
 * мини-DSL: amount, cur, merchant, income, transfer, date, time.
 * Например "(?<amount>[\\d ]+\\.?\\d*)" даст доступ к сумме через groups["amount"].
 */
@JvmInline
value class ExtractionRule(val pattern: Regex)
