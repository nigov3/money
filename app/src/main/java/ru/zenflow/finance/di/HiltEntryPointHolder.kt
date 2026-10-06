package ru.zenflow.finance.di

import dagger.hilt.android.EntryPointAccessors
import ru.zenflow.finance.ZenFlowApp

/**
 * Мост между Hilt и классами, которые Hilt не умеет внедрять «из коробки»
 * (NotificationListenerService — не Activity/Service с @AndroidEntryPoint-поддержкой).
 *
 * Использование: `HiltEntryPointHolder.entryPoint().inject(this)` в onCreate сервиса.
 */
object HiltEntryPointHolder {

    fun entryPoint(): ServiceEntryPoint = EntryPointAccessors.fromApplication(
        ZenFlowApp.instance,
        ServiceEntryPoint::class.java,
    )
}
