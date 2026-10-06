package ru.zenflow.finance.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.zenflow.finance.presentation.common.SkeletonLine
import ru.zenflow.finance.presentation.common.formatMinor
import ru.zenflow.finance.presentation.common.monthTitle
import ru.zenflow.finance.presentation.common.rememberAnimatedMoney
import ru.zenflow.finance.presentation.components.SectionHeader
import ru.zenflow.finance.presentation.components.SummaryChip
import ru.zenflow.finance.presentation.components.TransactionRow
import ru.zenflow.finance.presentation.theme.FinanceTheme

/**
 * Главный экран: сводка месяца + лента последних транзакций + быстрые действия.
 * Данные — StateFlow из Hilt-VM, подписка через collectAsStateWithLifecycle
 * (stop-on-lifecycle: при уходе в background подписка на Room Flow отваливается).
 */
@Composable
fun HomeScreen(
    onOpenAnalytics: () -> Unit = {},
    onOpenManualAdd: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finance = FinanceTheme.colors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenManualAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Добавить") },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { MonthSummaryCard(state) }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SummaryChip(
                        label = "Расходы",
                        amountMinor = state.monthExpensesMinor,
                        currency = "RUB",
                        accent = finance.expense,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryChip(
                        label = "Доходы",
                        amountMinor = state.monthIncomeMinor,
                        currency = "RUB",
                        accent = finance.income,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.transactions.isEmpty() && !state.isLoading) {
                item { EmptyState(onOpenAnalytics) }
            } else {
                item { SectionHeader("Последние операции") }
                if (state.isLoading) {
                    items(6) { SkeletonLine(widthFraction = 0.85f, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
                } else {
                    items(state.transactions, key = { it.id }) { tx ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 4 },
                        ) {
                            TransactionRow(tx = tx)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) } // отступ под FAB
        }
    }
}

/** Верхняя карточка: общий баланс крупно + месяц. Анимированный пересчёт суммы. */
@Composable
private fun MonthSummaryCard(state: HomeUiState) {
    val animatedBalance = rememberAnimatedMoney(state.totalBalanceMinor)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(20.dp),
    ) {
        Text(
            monthTitle(System.currentTimeMillis()),
            style = MaterialTheme.typography.labelSmall,
            color = FinanceTheme.colors.muted,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${formatMinor(animatedBalance)} ₽",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Общий баланс · ${if (state.isLoading) "загрузка…" else "${state.transactions.size} операций"}",
            style = MaterialTheme.typography.labelSmall,
            color = FinanceTheme.colors.muted,
        )
    }
}

@Composable
private fun EmptyState(onOpenAnalytics: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text("Пока пусто", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Разрешите доступ к уведомлениям банков — траты появятся автоматически.",
                style = MaterialTheme.typography.bodyMedium,
                color = FinanceTheme.colors.muted,
            )
        }
    }
}
