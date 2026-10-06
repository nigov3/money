package ru.zenflow.finance.core.parser

import ru.zenflow.finance.core.model.CurrencyCode
import ru.zenflow.finance.core.model.ParsedTransaction
import ru.zenflow.finance.core.model.RawMessage
import ru.zenflow.finance.core.model.TransactionType
import ru.zenflow.finance.core.util.MessageNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * БАЗОВАЯ РЕАЛИЗАЦИЯ парсера на Regex-шаблонах.
 *
 * Алгоритм:
 *  1. Отбираем подходящие шаблоны по пакету/отправителю (fallback — generic).
 *  2. Первое правило шаблона (main) обязано найти сумму — иначе шаблон не подошёл.
 *  3. Вторые/третьи правила определяют тип операции (income/transfer).
 *  4. Нормализация сумм/дат/мерчантов вынесена в MessageNormalizer.
 *
 * Класс не трогает Android API → полностью unit-тестируется на JVM.
 * @Singleton — переиспользуем один экземпляр из обоих сервисов-перехватчиков.
 */
@Singleton
class RegexTransactionParser @Inject constructor(
    private val registry: List<BankTemplate> = BankTemplateRegistry.templates,
) : TransactionParser {

    override fun parse(message: RawMessage): ParsedTransaction? {
        // Полное текстовое «полотно»: заголовок + тело (у нотификаций смысл часто в title)
        val fullText = buildString {
            message.title?.let { append(it).append('\n') }
            append(message.body)
        }

        val candidates = selectTemplates(message)

        for (template in candidates) {
            val mainRule = template.rules.firstOrNull() ?: continue
            val mainMatch = mainRule.pattern.find(fullText) ?: continue

            val amount = mainMatch.groups["amount"]?.value
                ?.let(MessageNormalizer::normalizeAmount)
                ?: continue // сумма не распознана — пробуем следующий шаблон

            val type = detectType(template, fullText)
            val currency = detectCurrency(mainMatch, fullText)
            val merchant = MessageNormalizer.cleanMerchant(
                mainMatch.groups["merchant"]?.value ?: mainMatch.groups["m2"]?.value
            )
            val occurredAt = MessageNormalizer.extractOccurredAt(fullText, message.timestampMillis)
            val dedupKey = MessageNormalizer.dedupKey(
                source = message.sourcePackage ?: message.title,
                amount = amount,
                occurredAt = occurredAt,
            )

            return ParsedTransaction(
                amount = amount,
                type = type,
                currency = currency,
                merchant = merchant,
                categoryHint = merchant, // на этапе автокатегоризации обогатим словарём MCC/слов
                occurredAt = occurredAt,
                dedupKey = dedupKey,
                rawText = fullText,
                parserId = template.id,
            )
        }
        return null // ни один шаблон не совпал — сообщение игнорируем
    }

    override fun activeTemplates(): List<String> = registry.map { it.id }

    // ------------------------------------------------------------------ //
    // internals
    // ------------------------------------------------------------------ //

    /** Точечные шаблоны банка первыми; generic-фолбэк всегда последний. */
    private fun selectTemplates(message: RawMessage): List<BankTemplate> {
        val matched = registry.filter { t ->
            when (message.channel) {
                ru.zenflow.finance.core.model.CaptureChannel.NOTIFICATION ->
                    t.packageMatcher != null &&
                        message.sourcePackage?.let { t.packageMatcher.matches(it) } == true
                ru.zenflow.finance.core.model.CaptureChannel.SMS ->
                    t.senderMatcher != null &&
                        message.sourcePackage?.let { t.senderMatcher.matches(it) } == true
            }
        }
        val fallback = registry.filter { it.packageMatcher == null && it.senderMatcher == null }
        return matched + fallback
    }

    private fun detectType(template: BankTemplate, text: String): TransactionType {
        // Правила[1..] — маркеры типа. Классифицируем их по сигнатуре паттерна:
        // правило с группой (?<transfer>) определяет перевод, с (?<income>) — приход.
        val rules = template.rules.drop(1)

        val transferHit = rules.any { "transfer>" in it.pattern.pattern } &&
            rules.firstOrNull { "transfer>" in it.pattern.pattern }!!
                .pattern.containsMatchIn(text)
        if (transferHit) return TransactionType.TRANSFER

        val incomeHit = rules.any { "income>" in it.pattern.pattern } &&
            rules.firstOrNull { "income>" in it.pattern.pattern }!!
                .pattern.containsMatchIn(text)
        if (incomeHit) return TransactionType.INCOME

        return TransactionType.EXPENSE
    }

    private fun detectCurrency(mainMatch: MatchResult, text: String): CurrencyCode {
        mainMatch.groups["cur"]?.value
            ?.let(CurrencyCode::fromSymbolOrCode)
            ?.let { return it }

        // Валюту ищем по всему тексту (в title может быть "USD", а символ — только там)
        return CURRENCY_SCAN.find(text)?.groups?.get("c")?.value
            ?.let(CurrencyCode::fromSymbolOrCode)
            ?: CurrencyCode.RUB // дефолт для банков РФ
    }

    private companion object {
        val CURRENCY_SCAN = Regex("""(?i)(?<c>RUB|₽|USD|\$|EUR|€|CNY|¥)""")
    }
}
