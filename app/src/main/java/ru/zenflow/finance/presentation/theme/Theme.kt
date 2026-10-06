package ru.zenflow.finance.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Кастомная тема ZenFlow: «Material 3, но не из коробки».
 * - Скейпинг крупнее дефолтного (24dp карточки — «воздух» как в Дзен-Мани)
 * - Типографика: жирные заголовки + tabular numerals для сумм («tnum»: цифры
 *   одной ширины, список сумм не «прыгает» по разрядам при обновлении)
 * - Плоский дизайн: карточки разделяются поверхностями, а не elevation-тенями
 */

private val LightColors = lightColorScheme(
    primary = Mint40,
    onPrimary = Color.White,
    primaryContainer = MintSubtleLight,
    onPrimaryContainer = Color(0xFF0B2E22),
    secondary = Color(0xFF4C615A),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnMuted,
    outline = LightOutline,
    error = CoralExpense,
)

private val DarkColors = darkColorScheme(
    primary = Mint80,
    onPrimary = Color(0xFF07271D),
    primaryContainer = MintSubtleDark,
    onPrimaryContainer = Color(0xFFC9EFE2),
    secondary = Color(0xFFB3C8BF),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnMuted,
    outline = DarkOutline,
    error = CoralExpenseDark,
)

/** Стиль денежных сумм: bold + моноширинные цифры. */
val MoneyStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 17.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum",
)

object FinanceTypography {
    val display = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.8).sp)
    val h1 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.4).sp)
    val h2 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
    val body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val caption = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
}

private val AppTypography = Typography(
    displayLarge = FinanceTypography.display,
    headlineMedium = FinanceTypography.h1,
    titleLarge = FinanceTypography.h2,
    bodyMedium = FinanceTypography.body,
    labelSmall = FinanceTypography.caption,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),   // чипы фильтров / мини-карточки
    large = RoundedCornerShape(24.dp),    // основные карточки (сводка, график)
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Семантические цвета вне Material-палитры (расход/приход/muted) прокидываем
 * через CompositionLocal — чтобы композаблы не тянули тему напрямую и были
 * тестируемыми в превью с любой раскладкой.
 */
data class FinanceColors(
    val expense: Color,
    val income: Color,
    val transfer: Color,
    val muted: Color,
    val money: TextStyle,
)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceColors(
        expense = CoralExpense,
        income = GreenIncome,
        transfer = Color(0xFF2E9BD6),
        muted = LightOnMuted,
        money = MoneyStyle,
    )
}

@Composable
fun ZenFlowTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (dark) DarkColors else LightColors
    val finance = FinanceColors(
        expense = if (dark) CoralExpenseDark else CoralExpense,
        income = if (dark) GreenIncomeDark else GreenIncome,
        transfer = if (dark) Color(0xFF6BC0EE) else Color(0xFF2E9BD6),
        muted = if (dark) DarkOnMuted else LightOnMuted,
        money = MoneyStyle,
    )
    CompositionLocalProvider(LocalFinanceColors provides finance) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Хелпер для композаблов: MaterialTheme + локальные finance-цвета одной строкой. */
object FinanceTheme {
    val colors: FinanceColors
        @Composable get() = LocalFinanceColors.current
}
