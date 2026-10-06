package ru.zenflow.finance.service.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.zenflow.finance.core.model.CaptureChannel
import ru.zenflow.finance.core.model.RawMessage
import ru.zenflow.finance.di.HiltEntryPointHolder
import ru.zenflow.finance.domain.usecase.ProcessCapturedMessageUseCase
import javax.inject.Inject

/**
 * ПЕРЕХВАТ PUSH-УВЕДОМЛЕНИЙ БАНКОВ.
 *
 * Как включается: пользователь выдаёт доступ в
 * Настройки → Приложения → Специальный доступ → Доступ к уведомлениям.
 * (Открыть эти настройки одной кнопкой — задача экрана онбординга, см. этап UI.)
 *
 * ПОДВОДНЫЕ КАМНИ и решения:
 *  1. onNotificationPosted вызывается в main-потоке сервиса => тяжёлую работу
 *     (regex + Room) уводим в отдельный CoroutineScope(IO). Никогда не блокируем колбэк.
 *  2. Фильтр по списку пакетов банков: не парсим уведомления Telegram/почты —
 *     меньше ложных срабатываний generic-шаблона и меньше приватных данных в памяти.
 *     Расширение фильтра = одна строка в [BANK_PACKAGES].
 *  3. Сервис может быть убит системой при low-memory — Android сам перепривяжет
 *     его; stateless-логика (всё в Room через dedup_key) переживёт перезапуск.
 *  4. Для отладки без реальных банков: `adb shell am listen` или отправка тестового
 *     нотификана из нашего же приложения (см. NotificationSender для self-test позже).
 *
 * @Inject через Hilt (@AndroidEntryPoint на сервисе). Lazy — чтобы не поднимать
 * всю графу зависимостей до первого уведомления.
 */
/**
 * ВНИМАНИЕ: NotificationListenerService НЕ поддерживает @AndroidEntryPoint напрямую
 * (Hilt не генерирует для него базовый класс) — поэтому используется manual
 * EntryPoint через HiltEntryPointHolder. Это единственный надёжный способ
 * получить DI в listener-сервисе без костылей.
 */
class BankNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var processMessage: Lazy<ProcessCapturedMessageUseCase>

    /** Отдельный scope на жизнь сервиса; отменяем в onDestroy, чтобы не утекали корутины. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Manual Hilt injection (см. комментарий класса): entry point -> component -> memberInjector
        HiltEntryPointHolder.entryPoint().inject(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName

        // --- Фильтр источников: только банки + SMS-приложение (двойной захват) ---
        if (pkg !in BANK_PACKAGES && !isSmsPackage(pkg)) return

        val extras = sbn.notification?.extras ?: return

        // Извлекаем текст: bigText > text > title. Банки кладут сумму чаще всего в bigText.
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return // пустое уведомление (например, "промо") — игнор

        val raw = RawMessage(
            sourcePackage = pkg,
            title = title,
            body = body,
            timestampMillis = sbn.postTime,
            channel = CaptureChannel.NOTIFICATION,
        )

        serviceScope.launch {
            runCatching { processMessage.get()(raw) }
                // Парсинг не должен ронять сервис: логируем и живём дальше
                .onFailure { android.util.Log.e(TAG, "process failed for $pkg", it) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Пользователь смахнул уведомление — транзакция уже в БД, ничего делаем.
        // (Здесь позже можно реализовать "undo" если удаление произошло < N секунд после поста.)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun isSmsPackage(pkg: String): Boolean =
        pkg == "com.android.mms" || pkg == "com.google.android.apps.messaging"

    companion object {
        private const val TAG = "BankNotifListener"

        /** Пакеты приложений-банков РФ. Добавляй новые — фильтр расширится. */
        val BANK_PACKAGES: Set<String> = setOf(
            "ru.sberbankmobile",                 // Сбербанк Online
            "ru.investica.sberbank",             // Сбер (инвест)
            "com.sberbank.sbercomments",         // СберКомменты
            "ru.tinkoff.mobile",                 // Т-Банк (Тинькофф)
            "ru.tinkoff.investments",
            "eu.alphacard.alfa",                 // Альфа-Мобайл
            "ru.alfamobile.outcash",
            "ru.vtb24.mobilebanking.android",    // ВТБ
            "ru.openbank.mobile",                // Открытие
            "com.gazprommob.onlinetradeprod",    // Газпромбанк
            "ru.mts.mymts",                      // МТС Банк
            "com.yandex.bank",                   // Яндекс Банк
        )

        /** Проверка «разрешён ли нам доступ» — для онбординга/индикатора в UI. */
        fun isAccessGranted(context: android.content.Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            )
            return enabled?.contains(context.packageName) == true
        }
    }
}
