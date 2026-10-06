package ru.zenflow.finance.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import ru.zenflow.finance.data.repository.AnalyticsPeriod
import ru.zenflow.finance.data.repository.CategorySlice
import ru.zenflow.finance.data.repository.DayBar
import ru.zenflow.finance.data.repository.FinanceRepository
import javax.inject.Inject

data class AnalyticsUiState(
    val slices: List<CategorySlice> = emptyList(),   // pie chart
    val dailyBars: List<DayBar> = emptyList(),       // bar chart
    val totalMinor: Long = 0,
    val averageDailyMinor: Long = 0,
    val isLoading: Boolean = true,
)

/**
 * VM аналитики. Ключевой паттерн: flatMapLatest по выбранному периоду —
 * смена фильтра отменяет предыдущую подписку Room Flow без гонок.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repo: FinanceRepository,
) : ViewModel() {

    private val _period = MutableStateFlow(AnalyticsPeriod.MONTH)
    val period: StateFlow<AnalyticsPeriod> = _period.asStateFlow()

    fun setPeriod(p: AnalyticsPeriod) { _period.value = p }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AnalyticsUiState> = _period
        .flatMapLatest { selected ->
            val now = System.currentTimeMillis()
            val (from, to) = FinanceRepository.periodRange(selected, now)
            val tz = FinanceRepository.tzOffsetMillis(now)
            combine(
                repo.observeCategorySlices(from, to),
                repo.observeDailySpend(from, to, tz),
            ) { slices, bars ->
                val total = slices.sumOf { it.totalMinor }
                val days = if (bars.isEmpty()) 1 else bars.size
                AnalyticsUiState(
                    slices = slices,
                    dailyBars = bars,
                    totalMinor = total,
                    averageDailyMinor = total / days,
                    isLoading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())
}
