package ru.zenflow.finance.presentation.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Флаг «онбординг пройден» живёт в DataStore (не в БД — настройки не финансы). */
private val Context.dataStore by preferencesDataStore(name = "settings")

object OnboardingKeys {
    val COMPLETED = booleanPreferencesKey("onboarding_completed")
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** null = ещё не читали (показываем скелетон, а не онбординг вслепую). */
    val onboardingDone: StateFlow<Boolean?> =
        appContext.dataStore.data.map { it[OnboardingKeys.COMPLETED] }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun completeOnboarding() {
        viewModelScope.launch {
            appContext.dataStore.edit { prefs -> prefs[OnboardingKeys.COMPLETED] = true }
        }
    }

    /** Статусы текущих шагов — перечитываются при каждом onResume экрана. */
    fun notificationAccessGranted(): Boolean =
        PermissionHelper.isNotificationAccessGranted(appContext)

    fun smsPermissionGranted(): Boolean = PermissionHelper.hasSmsPermission(appContext)
}
