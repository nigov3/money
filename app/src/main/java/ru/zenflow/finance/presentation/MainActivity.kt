package ru.zenflow.finance.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ru.zenflow.finance.presentation.navigation.ZenFlowNavHost
import ru.zenflow.finance.presentation.theme.ZenFlowTheme

/**
 * Единственная Activity: edge-to-edge + тема + NavHost.
 * Системные бары прозрачные, иконки подстраиваются под light/dark автоматически
 * через enableEdgeToEdge (Android 15 поведёт себя так же без доп. кода).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            ZenFlowTheme {
                ZenFlowNavHost()
            }
        }
    }
}
