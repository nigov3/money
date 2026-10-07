package ru.zenflow.finance.presentation.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import ru.zenflow.finance.R
import ru.zenflow.finance.presentation.theme.FinanceTheme

/**
 * Онбординг: два шага автозахвата (уведомления + SMS) и «Пропустить».
 *
 * UX-решения:
 *  - Статус разрешения перечитывается при возврате из системных настроек
 *    (LaunchedEffect с polling 500ms, пока экран виден — проще, чем LifecycleEventObserver).
 *  - Push-доступ первичен: SMS-шаг показываем как «резервный канал» (Play-ограничения).
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var notifGranted by remember { mutableStateOf(viewModel.notificationAccessGranted()) }
    var smsGranted by remember { mutableStateOf(viewModel.smsPermissionGranted()) }

    // Перечитать статусы после возвращения из настроек системы
    LaunchedEffect(Unit) {
        while (!notifGranted || !smsGranted) {
            delay(500)
            notifGranted = viewModel.notificationAccessGranted()
            smsGranted = viewModel.smsPermissionGranted()
        }
    }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> smsGranted = granted }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = FinanceTheme.colors.muted,
            )
            Spacer(Modifier.height(8.dp))

            PermissionStep(
                icon = Icons.Filled.NotificationsActive,
                title = stringResource(R.string.onboarding_notif_title),
                description = stringResource(R.string.onboarding_notif_desc),
                granted = notifGranted,
                onAction = {
                    context.startActivity(PermissionHelper.notificationAccessIntent())
                },
            )

            PermissionStep(
                icon = Icons.Filled.Sms,
                title = stringResource(R.string.onboarding_sms_title),
                description = stringResource(R.string.onboarding_sms_desc),
                granted = smsGranted,
                onAction = { smsLauncher.launch(Manifest.permission.RECEIVE_SMS) },
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.completeOnboarding()
                    onFinished()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (notifGranted || smsGranted)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    if (notifGranted || smsGranted) stringResource(R.string.onboarding_start)
                    else stringResource(R.string.onboarding_skip),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionStep(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onAction: () -> Unit,
) {
    val finance = FinanceTheme.colors
    val accent by animateColorAsState(
        targetValue = if (granted) finance.income else MaterialTheme.colorScheme.primary,
        label = "stepAccent",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (granted) Icons.Filled.CheckCircle else icon, null, tint = accent)
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = finance.muted,
                textAlign = TextAlign.Start,
            )
        }
        Spacer(Modifier.size(8.dp))
        if (!granted) {
            TextButton(onClick = onAction) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = accent)
            }
        }
    }
}
