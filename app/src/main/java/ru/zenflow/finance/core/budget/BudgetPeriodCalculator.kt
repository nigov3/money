package ru.zenflow.finance.core.budget

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * ЧИСТЫЙ (не-android) календарный движок для бюджетов.
 *
 * Бюджет привязан к периоду (WEEK/MONTH); чтобы показать «spent vs limit»,
 * нужно вычислить интервал [from..to] текущего цикла и интервал предыдущего
 * цикла (для подсказки «в прошлом месяце потратили X»). Всё на java.time —
 * тестируется без Robolectric, ViewModel-и остаются JVM-тестируемыми.
 */
object BudgetPeriodCalculator {

    /** Интервалы текущего и предыдущего цикла бюджета (epoch millis, включительно). */
    data class Windows(
        val currentFrom: Long,
        val currentTo: Long,
        val previousFrom: Long,
        val previousTo: Long,
    )

    /**
     * @param period   гранулярность сброса лимита.
     * @param nowMillis любая метка внутри текущего цикла.
     * @param weekStartsMonday ISO-неделя (понедельник — начало), как принято в РФ.
     */
    fun windowsFor(
        period: BudgetPeriod,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Windows {
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val start: LocalDate
        val end: LocalDate
        when (period) {
            BudgetPeriod.WEEK -> {
                // dayOfWeek.value: Mon=1..Sun=7 -> сдвигаем к понедельнику
                val dow = today.dayOfWeek.value
                start = today.minusDays((dow - 1).toLong())
                end = start.plusDays(6)
            }
            BudgetPeriod.MONTH -> {
                start = today.withDayOfMonth(1)
                end = today.withDayOfMonth(today.lengthOfMonth())
            }
        }
        val prevStart: LocalDate
        val prevEnd: LocalDate
        when (period) {
            BudgetPeriod.WEEK -> { prevStart = start.minusWeeks(1); prevEnd = end.minusWeeks(1) }
            BudgetPeriod.MONTH -> {
                val prevMonth = start.minusMonths(1)
                prevStart = prevMonth.withDayOfMonth(1)
                prevEnd = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth())
            }
        }
        return Windows(
            currentFrom = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            // конец дня = следующий день минус 1мс (SQL BETWEEN — включителен)
            currentTo = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
            previousFrom = prevStart.atStartOfDay(zone).toInstant().toEpochMilli(),
            previousTo = prevEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
        )
    }

    /** Прогресс spent/limit. limit<=0 -> 0f (защита от деления; UI сам красит >1). */
    fun progress(spentMinor: Long, limitMinor: Long): Float =
        if (limitMinor <= 0L) 0f else spentMinor.toFloat() / limitMinor.toFloat()

    /** Зеркалит data.local.entity.Period — маппинг один раз в репозитории. */
    enum class BudgetPeriod { WEEK, MONTH }
}
