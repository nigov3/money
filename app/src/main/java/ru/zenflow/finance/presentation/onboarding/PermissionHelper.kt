package ru.zenflow.finance.presentation.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Запросы специальных доступов для онбординга.
 *
 * ПОДВОДНЫЕ КАМНИ, которые здесь учтены:
 *  1. «Доступ к уведомлениям» НЕЛЬЗЯ выдать программно — только вручную на
 *     экране настроек. Интент ACTION_NOTIFICATION_LISTENER_SETTINGS открывает
 *     его; на некоторых ROM (MIUI) экран переопределён — fallback на app-настройки.
 *  2. POST_NOTIFICATIONS (API 33+) — runtime-разрешение, запрашивается через
 *     ActivityResultContracts.RequestPermission в Compose; здесь только проверка.
 *  3. SMS (RECEIVE_SMS) — runtime-группа; Play требует декларацию причин.
 *     Проверка через checkSelfPermission, запрос — из UI-обёртки.
 */
object PermissionHelper {

    /** Статус доступа к уведомлениям (тот же механизм, что использует сервис). */
    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        )
        return enabled?.split(":")?.any { it.startsWith(context.packageName) } == true
    }

    /** Постраничник «Настройки → Доступ к уведомлениям». */
    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Fallback для ROM без standard-экрана листенеров (MIUI/OEM-оболочки). */
    fun fallbackSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Разрешение показывать НАШИ уведомления (Android 13+). До 33 — всегда granted. */
    fun areOurNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Экран системных настроек приложения (для «пробросить» запрещённое разрешение). */
    fun appNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Runtime-SMS: проверяем, не спрашиваем (спросит ViewModel через contract). */
    fun hasSmsPermission(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECEIVE_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Итог для шага онбординга: всё ли готово к автозахвату. */
    fun isAutoCaptureReady(context: Context): Boolean =
        isNotificationAccessGranted(context) || hasSmsPermission(context)
}
