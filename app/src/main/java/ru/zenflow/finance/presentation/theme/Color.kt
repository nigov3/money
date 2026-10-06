package ru.zenflow.finance.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Палитра ZenFlow. Сознательно НЕ дефолтный Material-фиолетовый:
 * фирменный цвет — глубокий «mint-teal» на тёмно-графитовом фоне,
 * акценты расходов — коралловый, пополнений — зелёный (как в Дзен-Мани,
 * но кастомные оттенки).
 */

// ---------- Brand ramp ----------
val Mint40 = Color(0xFF2E7D68)          // primary в light
val Mint80 = Color(0xFF7FD8C3)          // primary в dark
val MintSubtleLight = Color(0xFFDCF2EB) // container в light
val MintSubtleDark = Color(0xFF123B31)  // container в dark

val CoralExpense = Color(0xFFE5654B)    // суммы расходов (light)
val CoralExpenseDark = Color(0xFFFF8A72)
val GreenIncome = Color(0xFF3FA96B)     // суммы пополнений (light)
val GreenIncomeDark = Color(0xFF6BD49A)

// ---------- Light surfaces ----------
val LightBackground = Color(0xFFF6F7F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEDF0F3)
val LightOutline = Color(0xFFD3D8DE)
val LightOnBackground = Color(0xFF16191D)
val LightOnMuted = Color(0xFF6C7480)

// ---------- Dark surfaces (не чистый #000 — softer graphite) ----------
val DarkBackground = Color(0xFF101316)
val DarkSurface = Color(0xFF171B20)
val DarkSurfaceVariant = Color(0xFF22272E)
val DarkOutline = Color(0xFF3A414A)
val DarkOnBackground = Color(0xFFE8EAED)
val DarkOnMuted = Color(0xFF9AA3AE)

// ---------- Категория-палитра для pie chart ----------
// Фиксированный набор: один и тот же id категории всегда даёт тот же цвет
// в обеих темах (вариант яркости подбираем по isSystemInDarkTheme в VM/mapper).
data class ChartPalette(
    val slicesLight: List<Color>,
    val slicesDark: List<Color>,
)

object ChartColors {
    private val baseHues = listOf(
        0xFFE5654B, // coral
        0xFFF2A93B, // amber
        0xFF3FA96B, // green
        0xFF2E9BD6, // sky
        0xFF7C6CF0, // violet
        0xFFE0559B, // pink
        0xFF14B8A6, // teal
        0xFF9AAA20, // lime
        0xFFF97316, // orange
        0xFF6366F1, // indigo
    )

    val palette: ChartPalette = ChartPalette(
        slicesLight = baseHues.map { Color(it) },
        // В тёмной теме те же оттенки чуть осветляем, чтобы читались на графите
        slicesDark = baseHues.map { c -> Color((c and 0x00FFFFFFL) or 0x26000000L) },
    )

    /** Стабильный цвет среза по id категории (без случайного мерцания при рекомпозиции). */
    fun sliceColor(categoryId: Long?, dark: Boolean): Color {
        val list = if (dark) palette.slicesDark else palette.slicesLight
        val idx = ((categoryId ?: -1L).toInt() % list.size + list.size) % list.size
        return list[idx]
    }
}
