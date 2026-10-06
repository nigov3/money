package ru.zenflow.finance.presentation.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.zenflow.finance.data.repository.AnalyticsPeriod
import ru.zenflow.finance.data.repository.CategorySlice
import ru.zenflow.finance.data.repository.DayBar
import ru.zenflow.finance.presentation.common.SkeletonLine
import ru.zenflow.finance.presentation.common.dayAxisLabel
import ru.zenflow.finance.presentation.common.formatMinor
import ru.zenflow.finance.presentation.theme.ChartColors
import ru.zenflow.finance.presentation.theme.FinanceTheme

/**
 * Экран аналитики: pie chart по категориям + bar chart по дням + топ-список.
 *
 * NB: графики рисуются на Canvas (детерминированно, без внешних зависимостей).
 * Vico подключён в build.gradle — на следующем этапе заменим эти самописные
 * чарты на VicoPieChart/VicoColumnChart с анимациями и осями из коробки;
 * API репозитория (slices/bars) при этом не изменится.
 */
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PeriodFilter(period, viewModel::setPeriod) }

        if (state.isLoading) {
            items(4) { SkeletonLine(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }
        } else {
            item {
                Card(
                    title = "Расходы за период",
                    subtitle = "${formatMinor(state.totalMinor)} ₽ · среднее ${formatMinor(state.averageDailyMinor)} ₽/день",
                ) {
                    PieAndLegend(state.slices)
                }
            }
            item {
                Card(title = "По дням", subtitle = "${state.dailyBars.size} активных дней") {
                    DailyBarsChart(state.dailyBars)
                }
            }
            items(state.slices.size.coerceAtMost(10)) { i ->
                val slice = state.slices[i]
                CategoryLegendRow(slice, index = i)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------------- Фильтр периодов ---------------- //

private val PERIOD_LABELS = mapOf(
    AnalyticsPeriod.TODAY to "День",
    AnalyticsPeriod.WEEK to "Неделя",
    AnalyticsPeriod.MONTH to "Месяц",
    AnalyticsPeriod.QUARTER to "Квартал",
    AnalyticsPeriod.YEAR to "Год",
)

@Composable
private fun PeriodFilter(selected: AnalyticsPeriod, onSelect: (AnalyticsPeriod) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PERIOD_LABELS.forEach { (p, label) ->
            FilterChip(
                selected = p == selected,
                onClick = { onSelect(p) },
                label = { Text(label) },
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}

// ---------------- Карточка-контейнер ---------------- //

@Composable
private fun Card(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = FinanceTheme.colors.muted)
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

// ---------------- Pie chart (Canvas donut) ---------------- //

@Composable
private fun PieAndLegend(slices: List<CategorySlice>) {
    val dark = isDarkThemeLocal()
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp)) {
            drawDonut(slices, dark)
        }
        // Итог в «дырке» бублика
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Всего", style = MaterialTheme.typography.labelSmall, color = FinanceTheme.colors.muted)
            Text(
                slices.sumOf { it.totalMinor }.let { "${formatMinor(it)} ₽" },
                style = FinanceTheme.colors.money,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    slices.take(5).forEachIndexed { i, s -> CategoryLegendRow(s, i, compact = true) }
}

private fun DrawScope.drawDonut(slices: List<CategorySlice>, dark: Boolean) {
    if (slices.isEmpty()) return
    val total = slices.sumOf { it.totalMinor }.coerceAtLeast(1L)
    val stroke = 28f
    val area = Size(size.width - stroke, size.height - stroke)
    var startAngle = -90f
    for (s in slices) {
        val sweep = 360f * s.totalMinor / total
        drawArc(
            color = ChartColors.sliceColor(s.categoryId, dark),
            startAngle = startAngle,
            sweepAngle = sweep - 1.5f, // маленький зазор между сегментами — фирменный стиль
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
            size = area,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        startAngle += sweep
    }
}

@Composable
private fun CategoryLegendRow(slice: CategorySlice, index: Int, compact: Boolean = false) {
    val dark = isDarkThemeLocal()
    val color = ChartColors.sliceColor(slice.categoryId, dark)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 0.dp else 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = slice.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${formatMinor(slice.totalMinor)} ₽ · ${(slice.share * 100).toInt()}%",
            style = FinanceTheme.colors.money.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
            color = FinanceTheme.colors.muted,
        )
    }
}

// ---------------- Bar chart по дням ---------------- //

@Composable
private fun DailyBarsChart(bars: List<DayBar>) {
    if (bars.isEmpty()) {
        Text("Нет трат за период", color = FinanceTheme.colors.muted)
        return
    }
    val max = bars.maxOf { it.totalMinor }.coerceAtLeast(1L)
    // Берём последние 14 дней, чтобы бары не сливались на узких экранах
    val shown = bars.takeLast(14)
    Column {
        Canvas(Modifier
            .fillMaxWidth()
            .height(140.dp)) {
            val w = size.width / shown.size
            val barW = w * 0.55f
            shown.forEachIndexed { i, b ->
                val h = size.height * (b.totalMinor.toFloat() / max)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, Color(0xFF3FA9C9)),
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(i * w + (w - barW) / 2, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2.6f, barW / 2.6f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            shown.forEach { b ->
                Text(
                    text = dayAxisLabel(b.dayEpoch).substringBefore(", "),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = FinanceTheme.colors.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Простая локальная проверка темы (для палитры графика). */
@Composable
private fun isDarkThemeLocal(): Boolean =
    androidx.compose.foundation.isSystemInDarkTheme()
