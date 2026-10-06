package ru.zenflow.finance

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Корневой Hilt-компонент приложения.
 *
 * + Конфигурирует WorkManager вручную (androidx.startup-инициализатор удалён
 *   в манифесте), чтобы @HiltWorker-задачи получали инъекции.
 * + Держит статический [instance] — его читает HiltEntryPointHolder для
 *   сервисов, куда Hilt не дотягивается аннотациями.
 */
@HiltAndroidApp
class ZenFlowApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        /** Позже появится безопасная инициализация через ProcessLifecycleOwner;
         *  сейчас — простой holder, т.к. Application создаётся первым в процессе. */
        lateinit var instance: ZenFlowApp
            private set
    }
}
