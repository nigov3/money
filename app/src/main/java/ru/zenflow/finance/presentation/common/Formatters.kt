package ru.zenflow.finance.presentation.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/**
 * Форматирование денег и дат. Всё на java.time (minSdk 26 — desugaring не нужен),
 * без android.* — функции чистые и юнит-тестируемые.
 */
private const val NBSP = '\u202F' // узкий неразрывный пробел как разделитель тысяч

fun formatMinor(minor: Long, compact: Boolean = false): String {
    if (compact) return formatCompact(minor)
    val abs = kotlin.math.abs(minor)
    val whole = abs / 100
    val frac = (abs % 100).toInt()
    val grouped = buildString {
        val s = whole.toString()
        s.forEachIndexed { i, c ->
            if (i > 0 && (s.length - i) % 3 == 0) append(NBSP)
            append(c)
        }
    }
    return if (frac == 0) grouped else "$grouped.${frac.toString().padStart(2, '0')}"
}

/** Компактный формат для осей графиков: 123450 коп -> "1,2 тыс". */
fun formatCompact(minor: Long): String {
    val major = kotlin.math.abs(minor) / 100.0
    val sign = if (minor < 0) "-" else ""
    return when {
        major >= 1_000_000 -> "$sign${trim(major / 1_000_000)} млн"
        major >= 1_000 -> "$sign${trim(major / 1_000)} тыс"
        else -> "$sign${major.toInt()}"
    }
}

private fun trim(v: Double): String {
    val r = (v * 10).roundToLong() / 10.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString().replace('.', ',')
}

private val MONTHS_SHORT = listOf(
    "янв", "фев", "мар", "апр", "мая", "июн",
    "июл", "авг", "сен", "окт", "ноя", "дек",
)

private val MONTHS_FULL = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

/** Дата строки ленты: сегодня — «14:32», этот год — «6 окт», иначе — «6 окт 2025». */
fun formatTxTimestamp(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val zone = java.time.ZoneId.systemDefault()
    val instant = java.time.Instant.ofEpochMilli(epochMillis)
    val d = java.time.LocalDate.ofInstant(instant, zone)
    val today = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
    return when {
        d == today -> {
            val t = java.time.LocalTime.ofNanoOfDay(
                instant.atZone(zone).toLocalTime().toNanoOfDay()
            )
            "%02d:%02d".format(t.hour, t.minute)
        }
        d.year == today.year -> "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]}"
        else -> "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]} ${d.year}"
    }
}

/** Заголовок месяца на главном экране («Октябрь 2026»). */
fun monthTitle(epochMillis: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val date = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zone)
    return "${MONTHS_FULL[date.monthValue - 1]} ${date.year}"
}

/** «Пн, 6 окт» — подписи дней под bar chart. */
fun dayAxisLabel(dayEpoch: Long): String {
    val date = java.time.LocalDate.ofEpochDay(dayEpoch)
    val dow = when (date.dayOfWeek.value) {
        1 -> "пн"; 2 -> "вт"; 3 -> "ср"; 4 -> "чт"; 5 -> "пт"; 6 -> "сб"; else -> "вс"
    }
    return "$dow, ${date.dayOfMonth}"
}

/**
 * Плавный «пересчёт» суммы при обновлении Flow (фирменный приём Дзен-Мани):
 * интерполяция ~400 мс, цифры не телепортируются между эмиссиями.
 */
@Composable
fun rememberAnimatedMoney(targetMinor: Long): Long {
    var displayed by remember { mutableLongStateOf(targetMinor) }
    LaunchedEffect(targetMinor) {
        val from = displayed
        if (from == targetMinor) return@LaunchedEffect
        val diff = targetMinor - from
        val steps = 16
        repeat(steps) { i ->
            delay(24)
            displayed = from + diff * (i + 1) / steps
        }
        displayed = targetMinor
    }
    return displayed
}

/** Скелетон до первой эмиссии данных. */
@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
    )
}
