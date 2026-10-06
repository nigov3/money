package ru.zenflow.finance.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.zenflow.finance.service.notification.BankNotificationListenerService

/**
 * После перезагрузки устройства система сама перепривяжет NotificationListenerService,
 * если доступ был выдан. Здесь лишь логируем состояние для диагностики и (в будущем)
 * ставим в очередь WorkManager-задачу "пересчитать кэши балансов", т.к. транзакции
 * могли копиться в буфере, пока телефон был выключен.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val granted = BankNotificationListenerService.isAccessGranted(context)
        android.util.Log.i("ZenFlowBoot", "boot done; notification access=$granted")
        // TODO(этап аналитики): WorkManager.enqueue(RecalculateBalancesWorker)
    }
}
