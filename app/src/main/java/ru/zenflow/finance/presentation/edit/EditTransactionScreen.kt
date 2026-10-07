package ru.zenflow.finance.presentation.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.zenflow.finance.R
import ru.zenflow.finance.core.model.TransactionType
import ru.zenflow.finance.presentation.components.CategoryAvatar
import ru.zenflow.finance.presentation.theme.FinanceTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Экран правки/добавления транзакции — bottom sheet-стиль на весь экран:
 * крупное поле суммы (главная задача пользователя), сегменты типа, чипы категорий.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    onClose: () -> Unit,
    viewModel: EditTransactionViewModel = hiltViewModel(),
    categoriesVm: CategoriesViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val categories by categoriesVm.categories.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                EditTransactionViewModel.UiEvent.Saved,
                EditTransactionViewModel.UiEvent.Deleted -> onClose()
            }
        }
    }

    // Seed гарантирует хотя бы один счёт («Кошелёк»); fallback на первый при ручном добавлении
    val fallbackAccountId = loaded?.accountId

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (loaded != null) stringResource(R.string.edit_title) else "Новая операция") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    if (loaded != null) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.DeleteOutline, stringResource(R.string.edit_delete),
                                tint = FinanceTheme.colors.expense)
                        }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------- Сумма: самое крупное поле экрана ---------- //
            OutlinedTextField(
                value = form.amountText,
                onValueChange = viewModel::onAmountChange,
                label = { Text(stringResource(R.string.edit_amount)) },
                prefix = { Text("₽", fontWeight = FontWeight.Bold) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.displayLarge.copy(
                    color = when (form.type) {
                        TransactionType.EXPENSE -> FinanceTheme.colors.expense
                        TransactionType.INCOME -> FinanceTheme.colors.income
                        TransactionType.TRANSFER -> FinanceTheme.colors.transfer
                    }
                ),
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            // ---------- Тип операции ---------- //
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip(TransactionType.EXPENSE, stringResource(R.string.edit_type_expense), form.type) {
                    viewModel.onTypeChange(it)
                }
                TypeChip(TransactionType.INCOME, stringResource(R.string.edit_type_income), form.type) {
                    viewModel.onTypeChange(it)
                }
                TypeChip(TransactionType.TRANSFER, stringResource(R.string.edit_type_transfer), form.type) {
                    viewModel.onTypeChange(it)
                }
            }

            // ---------- Категория: горизонтальные чипы с аватарами ---------- //
            Text(stringResource(R.string.edit_category), style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = form.categoryId == null,
                    onClick = { viewModel.onCategoryChange(null) },
                    label = { Text("Без категории") },
                    shape = MaterialTheme.shapes.medium,
                )
            }
            // Чипы в две «строки» через FlowRow (Compose foundation, стабильный API)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = form.categoryId == cat.id,
                        onClick = { viewModel.onCategoryChange(cat.id) },
                        leadingIcon = { CategoryAvatar(name = cat.name, tint = categoryTint(cat.color), fg = categoryFg(cat.color)) },
                        label = { Text(cat.name) },
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }

            OutlinedTextField(
                value = form.merchant,
                onValueChange = viewModel::onMerchantChange,
                label = { Text(stringResource(R.string.edit_merchant)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---------- Дата ---------- //
            TextButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm")
                        .format(LocalDate.ofInstant(Instant.ofEpochMilli(form.occurredAt), ZoneId.systemDefault()))
                        ?: "",
                )
            }

            OutlinedTextField(
                value = form.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text(stringResource(R.string.edit_note)) },
                minLines = 2,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { viewModel.save(fallbackAccountId) },
                enabled = form.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.edit_save), style = MaterialTheme.typography.titleLarge) }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = form.occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // DatePicker отдаёт UTC-midnight выбранного дня; сохраняем день, время — текущее
                        val local = LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneId.utcOffset(ZoneId.systemDefault().rules.getOffset(Instant.now())))
                        val merged = local.atTime(java.time.LocalTime.ofInstant(Instant.ofEpochMilli(form.occurredAt), ZoneId.systemDefault()))
                        viewModel.onDateChange(merged.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    }
                    showDatePicker = false
                }) { Text("ОК") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun TypeChip(type: TransactionType, label: String, current: TransactionType, onClick: (TransactionType) -> Unit) {
    val finance = FinanceTheme.colors
    val accent = when (type) {
        TransactionType.EXPENSE -> finance.expense
        TransactionType.INCOME -> finance.income
        TransactionType.TRANSFER -> finance.transfer
    }
    FilterChip(
        selected = current == type,
        onClick = { onClick(type) },
        label = { Text(label) },
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.16f),
            selectedLabelColor = accent,
        ),
    )
}

/** ARGB-int из БД -> Compose Color. Отрицательные значения — легитимный int (two's complement). */
private fun categoryTint(argb: Int): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.Color(argb).copy(alpha = 0.14f)

private fun categoryFg(argb: Int): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.Color(argb)
