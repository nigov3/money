package ru.zenflow.finance.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.zenflow.finance.data.repository.AnalyticsPeriod
import ru.zenflow.finance.data.repository.FinanceRepository
import ru.zenflow.finance.data.repository.TxRow
import javax.inject.Inject

/** Иммутабельное состояние главного экрана (UiState — единственный источник истины UI). */
data class HomeUiState(
    val transactions: List<TxRow> = emptyList(),
    val monthExpensesMinor: Long = 0,
    val monthIncomeMinor: Long = 0,
    val totalBalanceMinor: Long = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: FinanceRepository,
) : ViewModel() {

    /**
     * Границы «текущего месяца» фиксируем один раз при создании VM.
     * Подводный камень: если приложение открыто сутками и наступит новое число —
     * сводка не «переедет» автоматически; решается пересозданием VM при onResume
     * (или отдельным ticker'ом — оставил TODO, чтобы не усложнять этап).
     */
    private val now = System.currentTimeMillis()
    private val (monthFrom, monthTo) = FinanceRepository.periodRange(AnalyticsPeriod.MONTH, now)

    val uiState: StateFlow<HomeUiState> = combine(
        repo.observeRecent(limit = 100),
        repo.observeTotalExpenses(monthFrom, monthTo),
        repo.observeTotalIncome(monthFrom, monthTo),
        repo.observeAccounts(),
    ) { txs, expenses, income, accounts ->
        HomeUiState(
            transactions = txs,
            monthExpensesMinor = expenses,
            monthIncomeMinor = income,
            // Баланс = сумма кэшированных балансов активных счетов (кэш обновляет use-case атомарно)
            totalBalanceMinor = accounts.sumOf { it.cachedBalanceMinor },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        // WhileSubscribed(5000): Flow остаётся активным 5 сек после ухода с экрана —
        // возврат из навигации без «мигания» скелетонов
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /** TODO(stage-4): пересчёт monthFrom/monthTo при смене даты / foreground. */
}
