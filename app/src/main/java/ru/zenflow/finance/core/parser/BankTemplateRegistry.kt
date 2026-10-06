package ru.zenflow.finance.core.parser

/**
 * РЕЕСТР ШАБЛОНОВ БАНКОВ.
 *
 * Здесь живут все Regex-паттерны. Чтобы добавить банк (ВТБ, Открытие, Газпром...) —
 * добавь объект в [templates], ничего больше менять не нужно: парсер перебирает
 * реестр сам, сервисы про банки не знают вообще.
 *
 * Соглашения по именованным группам (см. ExtractionRule):
 *  (?<amount>)   — сумма; (?<cur>) — валюта; (?<merchant>) — магазин/контрагент;
 *  (?<income>)   — маркер пополнения; (?<transfer>) — маркер перевода «себе»;
 *  (?<date>) (?<time>) — опциональная дата/время внутри текста.
 *
 * Формат сумм в сообщениях банков РФ очень разный: "1 234,56 ₽", "1234.56 руб.",
 * "- 89,90" — нормализацией занимается AmountNormalizer (util), здесь только захват.
 */
object BankTemplateRegistry {

    // Общий кусок regex для суммы: группы цифр с опц. разделителями и дробями
    private const val AMOUNT = """(?<amount>[\d\s\u00A0]+(?:[.,]\d{1,2})?)"""
    private const val CURRENCY = """(?<cur>₽|руб\.?|usd|\$|eur|€|cny|¥)?"""
    private const val INCOME_MARK = """(?<income>зачисл\w*|пополн\w*|возврат\w*|cashback|кэшбек)"""
    private const val TRANSFER_MARK = """(?<transfer>перевод\w*(?: себе| на свой)|между своими)"""

    /**
     * Шаблон СберБанка (SberBank Online).
     * Типичные уведомления:
     *   "Сбербанк | Онлайн-банк" / "Покупка ПЯТЁРОЧКА 1 234,56 ₽. Баланс ..."
     *   "Зачисление Перевод себе +50 000,00 ₽"
     * SMS: "Pokupka PYATEROCHKA 234.56 RUBLEJ. Ostalos na schete..."
     */
    private val sberbank = BankTemplate(
        id = "sberbank",
        displayName = "Сбербанк",
        packageMatcher = Regex("""(^ru\.sberbank\..*)|(^com\.sberrss?\..*)""", RegexOption.IGNORE_CASE),
        senderMatcher = Regex("""^(900|SBERBANK|SBER)$""", RegexOption.IGNORE_CASE),
        rules = listOf(
            // Полная форма: <маркер типа>? <мерчант> <сумма> <валюта>
            ExtractionRule(Regex(
                """(?i)(?<merchant>покупка|purchase|zachislenie|перевод)?\s*[:\-]?\s*""" +
                    """(?:[^.,;]*?(?<m2>[\w\sЁ-ёА-Яа-я.,'-]{2,40}?))?[:\s]*""" +
                    AMOUNT + """\s*""" + CURRENCY
            )),
            ExtractionRule(Regex("""(?i)$INCOME_MARK""")),
            ExtractionRule(Regex("""(?i)$TRANSFER_MARK""")),
        ),
    )

    /**
     * Тинькофф (T-Bank). Уведомления:
     *   "T‑Bank" / "Pay at PYZHECHKA. RU 543.20 ₽. Available 12 345.67 ₽"
     *   "Пополнение. Зарплата 120 000.00 ₽"
     * SMS: "Pay at LENTA 123.45RUB. Rest: 4562.10RUB"
     */
    private val tinkoff = BankTemplate(
        id = "tinkoff",
        displayName = "Т-Банк (Тинькофф)",
        packageMatcher = Regex("""(^ru\.tinco\..*)|(^com\.tinkoff\..*)""", RegexOption.IGNORE_CASE),
        senderMatcher = Regex("""^(TINKOFF|8559|T-BANK)$""", RegexOption.IGNORE_CASE),
        rules = listOf(
            ExtractionRule(Regex(
                """(?i)(pay at|покупка|purchase|spend)\s+(?<merchant>.{2,40}?)\s+""" +
                    AMOUNT + """\s*""" + CURRENCY
            )),
            ExtractionRule(Regex(
                """(?i)(?<income>top-up|пополнение|зачисление|transfer to)\D*$AMOUNT\s*$CURRENCY"""
            )),
            ExtractionRule(Regex("""(?i)$TRANSFER_MARK""")),
        ),
    )

    /**
     * Альфа-Банк. Уведомления:
     *   "Альфа-Мобайл" / "Карта ***1234. Покупка: МАГНИТ, 899.50 ₽. Остаток: ..."
     * SMS: "Pokupka MAGNIT g Moskva 899.50 RUB Dostupno 12345.67 RUB"
     */
    private val alfabank = BankTemplate(
        id = "alfabank",
        displayName = "Альфа-Банк",
        packageMatcher = Regex("""^eu\.alphacard\..*|^ru\.alfabank\..*""", RegexOption.IGNORE_CASE),
        senderMatcher = Regex("""^(ALFA|AlfaBank|2265)$""", RegexOption.IGNORE_CASE),
        rules = listOf(
            ExtractionRule(Regex(
                """(?i)покупка[:\s]+(?<merchant>.{2,40}?)[,\s]""" + AMOUNT + """\s*""" + CURRENCY
            )),
            ExtractionRule(Regex(
                """(?i)pokupka\s+(?<merchant>.{2,40}?)\s+""" + AMOUNT + """\s*(?<cur>RUB)?"""
            )),
            ExtractionRule(Regex("""(?i)(?<income>popoln\w*|зачисл\w*|postuplen\w*)""")),
        ),
    )

    /**
     * Фолбэк-шаблон «для всех»: ловит generic-форму «... N ₽» из сообщений банков,
     * которых ещё нет в реестре. Работает только если текст содержит явный
     * денежный маркер, иначе вернём null (не тащим мусор из мессенджеров).
     */
    private val genericFallback = BankTemplate(
        id = "generic",
        displayName = "Общий шаблон",
        packageMatcher = null, // применим к любому источнику
        senderMatcher = null,
        rules = listOf(
            ExtractionRule(Regex(
                """(?i)(?:списание|покупка|оплата|purchase|paid|spend|зачисление|пополнение)?""" +
                    """\D{0,40}?$AMOUNT\s*(?:₽|руб\.?|RUB)?\b"""
            )),
            ExtractionRule(Regex("""(?i)$INCOME_MARK""")),
            ExtractionRule(Regex("""(?i)$TRANSFER_MARK""")),
        ),
    )

    /** Порядок важен: сначала точечные банковские шаблоны, потом фолбэк. */
    val templates: List<BankTemplate> = listOf(sberbank, tinkoff, alfabank, genericFallback)
}
