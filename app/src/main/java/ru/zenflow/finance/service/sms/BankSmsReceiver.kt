package ru.zenflow.finance.service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.zenflow.finance.core.model.CaptureChannel
import ru.zenflow.finance.core.model.RawMessage
import ru.zenflow.finance.di.HiltEntryPointHolder
import ru.zenflow.finance.domain.usecase.ProcessCapturedMessageUseCase
import javax.inject.Inject

/**
 * ПЕРЕХВАТ SMS ОТ БАНКОВ (резервный канал, когда push не дошёл).
 *
 * ПОДВОДНЫЕ КАМНИ:
 *  1. Android 10+ (API 29): SMS_RECEIVED_DELIVERED приходит только default-SMS
 *     приложению. Мы подписываемся на ACTION_SMS_RECEIVED — он доставляется ВСЕМ
 *     приложениям с RECEIVE_SMS, но последним и без права перехватывать показ.
 *  2. Google Play требует обоснование использования SMS-разрешений; для личного
 *     использования / sideload работает как есть.
 *  3. BroadcastReceiver живёт ~10 секунд => goAsync() + корутина в отдельном
 *     scope, иначе процесс убьют раньше, чем Room успеет записать строку.
 *  4. Многочастичные SMS (длинные отчёты) склеиваем по Telephony.Sms.Intents
 *     getMessagesFromIntent — каждая часть сама по себе бессмысленна.
 */
class BankSmsReceiver : BroadcastReceiver() {

    @Inject lateinit var processMessage: Lazy<ProcessCapturedMessageUseCase>

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Инъекция через EntryPoint: broadcast-приёмники Hilt тоже не поддерживает напрямую
        HiltEntryPointHolder.entryPoint().inject(this)

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Склеиваем части одного сообщения + берём адресата/время из первой части
        val sender = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        if (body.isBlank()) return

        // Быстрый pre-filter по отправителю: шлём в парсер только банковские короткие
        // номера/альфа-имена. Полный список — в SmsSenders (расширяется конфигом позже).
        if (!SmsSenders.isBankSender(sender)) return

        val raw = RawMessage(
            sourcePackage = sender,          // для SMS роль «источника» играет адресант
            title = null,
            body = body,
            timestampMillis = timestamp,
            channel = CaptureChannel.SMS,
        )

        // goAsync: держим broadcast «живым», пока корутина не допишет в Room
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                runCatching { processMessage.get()(raw) }
                    .onFailure { android.util.Log.e(TAG, "sms processing failed", it) }
            } finally {
                pendingResult.finish() // ОБЯЗАТЕЛЬНО вызвать, иначе система зависнет на этом приёмнике
            }
        }
    }

    private companion object { const val TAG = "BankSmsReceiver" }
}

/** Реестр банковских SMS-отправителей РФ (short codes и альфа-номера). */
object SmsSenders {
    private val KNOWN = setOf(
        "900", "SBERBANK", "SBER",           // Сбер
        "TINKOFF", "8559", "T-BANK",         // Тинькофф
        "ALFA-BANK", "ALFABANK", "2265",     // Альфа
        "VTB", "2626",                       // ВТБ
        "MTC-BANK", "MTSBANK",               // МТС
        "GAZPROMBANK", "GPBBANK",            // Газпромбанк
        "RENAISSANCE", "RENESANS",           // Ренессанс
        "SOYUZTELEBANK", "YC",               // Совкомбанк/Яндекс
    )

    fun isBankSender(sender: String): Boolean =
        KNOWN.any { it.equals(sender, ignoreCase = true) ||
                   sender.contains(it, ignoreCase = true) }
}
