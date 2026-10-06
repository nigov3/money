package ru.zenflow.finance.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

/**
 * Единственная Activity (single-activity + Navigation Compose).
 * На этом этапе — заглушка, чтобы манифест компилировался;
 * полноценный UI (тема, home/analytics экраны) — следующий этап.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("ZenFlow Finance — UI на следующем этапе") }
    }
}
