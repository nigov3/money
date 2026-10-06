package ru.zenflow.finance.core.util

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Нормализация «грязных» строковых значений, вытащенных regex-группами.
 * Вынесен в util, чтобы парсер оставался декларативным (только шаблоны).
 */
object MessageNormalizer {

    /**
     * "1 234,56" / "1234.56" / "89.90RUB" -> BigDecimal("1234.56")
     * @return null, если в строке нет ни одной цифры.
     */
    fun normalizeAmount(raw: String): BigDecimal? {
        // Убираем всё, кроме цифр и десятичных разделителей
        val cleaned = raw.filter { it.isDigit() || it == ',' || it == '.' }
        if (cleaned.isEmpty()) return null

        // Если есть и ',' и '.', последний разделитель считаем десятичным
        val normalized = when {
            cleaned.contains(',') && cleaned.contains('.') -> {
                if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.'))
                    cleaned.replace(".", "").replace(',', '.')
                else cleaned.replace(",", ".")
            }
            cleaned.contains(',') -> {
                // Одна запятая — скорее всего десятичная; но "1,234,567" = grouping
                if (Regex("""\d{1,3}(,\d{3})+""").matches(cleaned))
                    cleaned.replace(",", "")
                else cleaned.replace(',', '.')
            }
            else -> {
                // Точки: "1.234,56" уже обработано выше; "1234.56" ок; "1.234" ambiguous
                if (Regex("""\d{1,3}(\.\d{3})+$""").matches(cleaned))
                    cleaned.replace(".", "")
                else cleaned
            }
        }
        return normalized.toBigDecimalOrNull()?.takeIf { it.signum() != 0 }
    }

    /**
     * Извлечение даты из текста ("12.10.2026", "12 окт", "today 14:32").
     * Если даты в тексте нет — берём время сообщения (timestampMillis).
     */
    fun extractOccurredAt(
        text: String,
        fallbackMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val dateMatch = Regex("""(?<date>\d{1,2}[./]\d{1,2}[./]\d{2,4})""").find(text)
        val timeMatch = Regex("""(?<![\d:])(?<h>\d{1,2}):(?<m>\d{2})(?![\d:])""").find(text)

        val parsedDate: LocalDate? = dateMatch
            ?.groups?.get("date")?.value
            ?.let { d ->
                runCatching {
                    LocalDate.parse(
                        if (d.matches(Regex("""\d{1,2}[./]\d{1,2}\.\d{2}"""))) "$d.20${d.takeLast(2)}" else d,
                        DateTimeFormatter.ofPattern("d.M.yyyy"),
                    )
                }.getOrNull()
            }

        return if (parsedDate != null) {
            val time = timeMatch?.let {
                LocalTime.of(
                    it.groups["h"]!!.value.toInt().coerceIn(0, 23),
                    it.groups["m"]!!.value.toInt().coerceIn(0, 59),
                )
            } ?: LocalTime.NOON
            LocalDateTime.of(parsedDate, time).atZone(zone).toInstant().toEpochMilli()
        } else {
            fallbackMillis
        }
    }

    /** "ПЯТЁРОЧКА. RU" -> "Пятёрочка"; обрезает хвосты вида "g Moskva", балансы и т.п. */
    fun cleanMerchant(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
            .removeSuffix(".").trim()
            .replace(Regex("""(?i)\s+(ru|com|net)$"""), "")   // доменные хвосты
            .replace(Regex("""(?i)\s+(g|г\.?)\s+\w+$"""), "") // "g Moskva"
        if (trimmed.length < 2) return null
        // ALL CAPS от банков приводим к "Каждому Слову"
        return if (trimmed == trimmed.uppercase() && !trimmed.any { it.isDigit() }) {
            trimmed.lowercase().replaceFirstChar { it.uppercase() }
        } else trimmed
    }

    /** Быстрый стабильный ключ дедупликации (MD5 короткой склейки полей). */
    fun dedupKey(source: String?, amount: BigDecimal, occurredAt: Long): String {
        val minuteBucket = occurredAt / 60_000L // ±минута допустима между push и SMS
        val md = java.security.MessageDigest.getInstance("MD5")
        return md.digest("$source|$amount|$minuteBucket".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
