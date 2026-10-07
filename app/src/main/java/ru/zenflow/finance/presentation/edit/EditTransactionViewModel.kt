package ru.zenflow.finance.presentation.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.zenflow.finance.core.model.TransactionType
import ru.zenflow.finance.data.local.dao.TransactionFull
import ru.zenflow.finance.data.repository.FinanceRepository
import javax.inject.Inject

/**
 * ViewModel экрана «Детали/Правка» и ручного ввода.
 *
 * Один VM на два режима: id == null → добавление (FAB), id != null → правка.
 * Это осознанно: форма идентична, а дублирование VM — классический source of bugs.
 *
 * Подводный камень SavedStateHandle: NavArgs приходят как String; парсим
 * безопасно, при мусоре считаем режимом добавления (не падаем).
 */
@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    private val repo: FinanceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** null = ручное добавление; >0 = редактирование существующей. */
    private val txId: Long? = savedStateHandle.get<String>("txId")?.toLongOrNull()?.takeIf { it > 0 }

    private val _loaded = MutableStateFlow<TransactionFull?>(null)
    val loaded: StateFlow<TransactionFull?> = _loaded.asStateFlow()

    /** Форма (immutable snapshot полей ввода). */
    data class FormState(
        val amountText: String = "",
        val type: TransactionType = TransactionType.EXPENSE,
        val categoryId: Long? = null,
        val merchant: String = "",
        val note: String = "",
        val occurredAt: Long = System.currentTimeMillis(),
        val isSaving: Boolean = false,
    ) {
        val amountMinor: Long?
            get() = parseAmountToMinor(amountText)
        val canSave: Boolean
            get() = !isSaving && (amountMinor ?: 0L) > 0L
    }

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    /** Одноразовые события (закрыть экран после сохранения). */
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    sealed interface UiEvent { object Saved : UiEvent; object Deleted : UiEvent }

    init {
        if (txId != null) viewModelScope.launch {
            repo.getTransactionFull(txId)?.let { full ->
                _loaded.value = full
                _form.value = FormState(
                    amountText = minorToInputText(full.amountMinor),
                    type = runCatching { TransactionType.valueOf(full.type) }.getOrDefault(TransactionType.EXPENSE),
                    categoryId = full.categoryId,
                    merchant = full.merchant.orEmpty(),
                    note = full.note.orEmpty(),
                    occurredAt = full.occurredAt,
                )
            }
        }
    }

    fun onAmountChange(v: String) = update { copy(amountText = v.filter { it.isDigit() || it in ",." }.take(9)) }
    fun onTypeChange(v: TransactionType) = update { copy(type = v) }
    fun onCategoryChange(v: Long?) = update { copy(categoryId = v) }
    fun onMerchantChange(v: String) = update { copy(merchant = v.take(80)) }
    fun onNoteChange(v: String) = update { copy(note = v.take(200)) }
    fun onDateChange(v: Long) = update { copy(occurredAt = v) }

    fun save(accountIdFallback: Long?) {
        val f = form.value
        val amount = f.amountMinor ?: return
        if (!f.canSave) return
        viewModelScope.launch {
            _form.value = f.copy(isSaving = true)
            runCatching {
                if (txId != null) {
                    repo.editTransaction(txId, f.categoryId, amount, f.type, f.merchant, f.occurredAt, f.note)
                } else {
                    val accId = accountIdFallback ?: return@runCatching // нет счёта — UI покажет онбординг-подсказку
                    repo.addManualTransaction(accId, f.categoryId, amount, f.type, f.merchant, f.occurredAt, f.note)
                }
            }.onSuccess { _events.send(UiEvent.Saved) }
             .onFailure { android.util.Log.e("EditTxVM", "save failed", it) }
            _form.value = _form.value.copy(isSaving = false)
        }
    }

    fun delete() {
        val id = txId ?: return
        viewModelScope.launch {
            runCatching { repo.deleteTransaction(id) }
                .onSuccess { _events.send(UiEvent.Deleted) }
        }
    }

    private inline fun update(block: FormState.() -> FormState) {
        _form.value = _form.value.block()
    }

    companion object {
        /**
         * Парсинг ввода пользователя в копейки. Принимаем "1234", "1234,56",
         * "1 234.56". Возвращаем null при невалидном вводе (кнопка Save гаснет).
         */
        fun parseAmountToMinor(text: String): Long? {
            val cleaned = text.trim().replace(" ", "").replace("\u00A0", "").replace(",", ".")
            if (cleaned.isEmpty() || cleaned.any { !it.isDigit() && it != '.' }) return null
            val parts = cleaned.split(".")
            if (parts.size > 2) return null
            val whole = parts[0].toLongOrNull() ?: return null
            val frac = when (parts.size) {
                1 -> 0L
                2 -> parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: return null
                else -> 0L
            }
            val minor = whole * 100 + frac
            return if (minor > 0) minor else null
        }

        /** Обратное преобразование для подстановки в поле. */
        fun minorToInputText(minor: Long): String {
            val whole = minor / 100
            val frac = (minor % 100).toInt()
            return if (frac == 0) whole.toString() else "$whole,${frac.toString().padStart(2, '0')}"
        }
    }
}

/** VM списка категорий для чипов выбора (общий для edit/budgets). */
@HiltViewModel
class CategoriesViewModel @Inject constructor(repo: FinanceRepository) : ViewModel() {
    val categories = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
