package ru.zenflow.finance.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.zenflow.finance.service.notification.BankNotificationListenerService
import ru.zenflow.finance.service.sms.BankSmsReceiver

/**
 * @EntryPoint описывает, какие инъекции нужны «внешним» компонентам системы
 * (сервисам уведомлений и broadcast-приёмникам). SingletonComponent — т.к.
 * все зависимости там же (@Singleton use-case, Room DAO).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceEntryPoint {

    fun inject(service: BankNotificationListenerService)

    fun inject(receiver: BankSmsReceiver)
}
