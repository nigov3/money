package ru.zenflow.finance.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.zenflow.finance.core.model.CaptureChannel
import ru.zenflow.finance.core.model.CurrencyCode
import ru.zenflow.finance.core.model.RawMessage
import ru.zenflow.finance.core.model.TransactionType

/**
 * Проверка «золотых» сообщений банков на реальных форматах.
 * Тесты падают сразу при изменении любого шаблона — это safety-net реестра.
 */
class RegexTransactionParserTest {

    private val parser = RegexTransactionParser()

    private fun notif(pkg: String, title: String?, body: String) = RawMessage(
        sourcePackage = pkg, title = title, body = body,
        timestampMillis = 1_760_000_000_000L, channel = CaptureChannel.NOTIFICATION,
    )

    @Test
    fun `sber expense with merchant and rubles`() {
        val p = parser.parse(
            notif("ru.sberbankmobile", "Сбербанк | Онлайн-банк",
                "Покупка ПЯТЁРОЧКА 1 234,56 ₽. Баланс карты 45 000,00 ₽")
        )
        assertNotNull("должен распознать", p)
        assertEquals(TransactionType.EXPENSE, p!!.type)
        assertEquals(CurrencyCode.RUB, p.currency)
        assertEquals(0, p.amount.compareTo(java.math.BigDecimal("1234.56")))
    }

    @Test
    fun `tinkoff income top-up`() {
        val p = parser.parse(
            notif("ru.tinkoff.mobile", "T‑Bank",
                "Пополнение Зарплата 120 000.00 ₽")
        )
        assertNotNull(p)
        assertEquals(TransactionType.INCOME, p!!.type)
    }

    @Test
    fun `alfabank sms english translit`() {
        val raw = RawMessage(
            sourcePackage = "ALFA-BANK", title = null,
            body = "Pokupka MAGNIT g Moskva 899.50 RUB Dostupno 12345.67 RUB",
            timestampMillis = 1_760_000_000_000L, channel = CaptureChannel.SMS,
        )
        val p = parser.parse(raw)
        assertNotNull(p)
        assertEquals(0, p!!.amount.compareTo(java.math.BigDecimal("899.50")))
    }

    @Test
    fun `non-financial message returns null`() {
        // Нет ни суммы с валютным маркером, ни денег вообще -> игнор
        assertNull(parser.parse(notif("com.whatsapp", "Новое сообщение", "Привет, как дела?")))
    }
}
