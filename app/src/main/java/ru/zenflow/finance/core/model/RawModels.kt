package ru.zenflow.finance.core.model

import java.math.BigDecimal

/**
 * Доменная модель «сырого» сообщения, из которого мы будем парсить транзакцию.
 * Приводим к нему и уведомления, и SMS — чтобы парсер и репозиторий не зависели
 * от Android-классов (Notification / Bundle). Это упрощает unit-тестирование.
 */
data class RawMessage(
    /** Пакет приложения-источника (для уведомлений) или адрес отправителя (для SMS) */
    val sourcePackage: String?,
    /** Заголовок уведомления (у SMS — null) */
    val title: String?,
    /** Основной текст: bigText/text из extras либо тело SMS */
    val body: String,
    /** Метка времени: postTime у нотификации или Timestamp у SMS */
    val timestampMillis: Long,
    /** Канал захвата — влияет на приоритет доверия к данным */
    val channel: CaptureChannel,
)

enum class CaptureChannel { NOTIFICATION, SMS }

/**
 * Результат успешного парсинга — «кандидат в транзакции».
 * Пока не сохранён в БД: на этапе дедупликации и ручных правок он проходит
 * через use-case [ProcessCapturedMessageUseCase].
 */
data class ParsedTransaction(
    val amount: BigDecimal,               // всегда положительная magnitude
    val type: TransactionType,            // расход/приход/перевод между своими
    val currency: CurrencyCode,
    val merchant: String?,                // "Пятёрочка", "TINKOFF.RU", ...
    val categoryHint: String?,            // сырая подсказка категории из текста
    val occurredAt: Long,                 // epoch millis
    /** Стабильный ключ дедупликации: источник + сумма + время + заголовок.
     *  Одно и то же событие банк может прислать и push, и SMS — склеим по хешу. */
    val dedupKey: String,
    val rawText: String,                  // сохраняем для отладки/ручных правок
    val parserId: String,                 // какой шаблон сработал (для аналитики качества парсинга)
)

enum class TransactionType { EXPENSE, INCOME, TRANSFER }

/** Поддерживаемые валюты; расширяется без миграции БД (хранится как TEXT). */
enum class CurrencyCode(val code: String, val symbol: String) {
    RUB("RUB", "₽"), USD("USD", "$"), EUR("EUR", "€"), CNY("CNY", "¥");

    companion object {
        fun fromSymbolOrCode(raw: String): CurrencyCode? {
            val norm = raw.trim().uppercase()
            return entries.firstOrNull { it.code == norm || it.symbol == raw.trim() }
        }
    }
}
