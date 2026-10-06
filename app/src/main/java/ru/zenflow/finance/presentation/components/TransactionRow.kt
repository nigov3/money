package ru.zenflow.finance.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.zenflow.finance.core.model.TransactionType
import ru.zenflow.finance.data.repository.TxRow
import ru.zenflow.finance.presentation.common.formatMinor
import ru.zenflow.finance.presentation.common.formatTxTimestamp
import ru.zenflow.finance.presentation.theme.FinanceTheme

/** Цвет категории из БД (ARGB int, легитимны и отрицательные — two's complement). */
fun categoryColor(argb: Int): Color = Color(argb)

/** Тинт аватара категории: фон 14% от цвета. */
fun categoryAvatarTint(argb: Int): Color = Color(argb).copy(alpha = 0.14f)

/**
 * Строка ленты транзакций. Плоский стиль: иконка-аватар категории слева,
 * сумма с фиксированным знаком/цветом справа.
 */
@Composable
fun TransactionRow(
    tx: TxRow,
    onClick: () -> Unit = {},
) {
    val finance = FinanceTheme.colors
    val symbol = currencySymbol(tx.currency)
    val (sign, color) = when (tx.type) {
        TransactionType.EXPENSE -> "-" to finance.expense
        TransactionType.INCOME -> "+" to finance.income
        TransactionType.TRANSFER -> "±" to finance.transfer
    }

    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Аватар категории: первая буква на цветном круге (стабильный цвет по categoryId)
            CategoryAvatar(name = tx.categoryName ?: tx.title, tint = color.copy(alpha = 0.14f), fg = color)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = tx.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(tx.subtitle, formatTxTimestamp(tx.occurredAt))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = finance.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = "$sign${formatMinor(tx.amountMinor)} $symbol",
                style = finance.money,
                color = color,
            )
        }
    }
}

@Composable
private fun CategoryAvatar(name: String, tint: Color, fg: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(tint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = fg,
        )
    }
}

/** Мини-карточка «расходы/доходы» в сводке месяца. */
@Composable
fun SummaryChip(
    label: String,
    amountMinor: Long,
    currency: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val finance = FinanceTheme.colors
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = finance.muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatMinor(amountMinor),
                style = finance.money,
                color = accent,
            )
            Spacer(Modifier.width(4.dp))
            Text(currencySymbol(currency), style = MaterialTheme.typography.labelSmall, color = finance.muted)
        }
    }
}

private fun currencySymbol(code: String): String = when (code.uppercase()) {
    "RUB" -> "₽"
    "USD" -> "$"
    "EUR" -> "€"
    "CNY" -> "¥"
    else -> code
}

/** Разделитель секций списка. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = FinanceTheme.colors.muted,
    )
}
