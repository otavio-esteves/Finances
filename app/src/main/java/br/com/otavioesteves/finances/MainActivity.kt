package br.com.otavioesteves.finances

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import br.com.otavioesteves.finances.domain.model.ThemeMode
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import br.com.otavioesteves.finances.ui.navigation.FinancesNavHost
import br.com.otavioesteves.finances.ui.theme.FinancesTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> (application as MainApplication).invoiceReminders.setEnabled(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val preferences = (application as MainApplication).themePreferences
        fun applyScreenshotPreference(allowed: Boolean) {
            if (allowed) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        applyScreenshotPreference(preferences.allowScreenshots.value)
        // Keep window security in the Activity lifecycle, independent of Compose frame scheduling.
        lifecycleScope.launch {
            preferences.allowScreenshots.collect { applyScreenshotPreference(it) }
        }
        val reminders = (application as MainApplication).invoiceReminders
        if (Build.VERSION.SDK_INT >= 33 && !reminders.permissionAsked && reminders.enabled.value) {
            reminders.markPermissionAsked()
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val animateLaunch = savedInstanceState == null

        enableEdgeToEdge()
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val mode by preferences.mode.collectAsStateWithLifecycle()
            val palette by preferences.palette.collectAsStateWithLifecycle()
            var entered by remember { mutableStateOf(!animateLaunch) }
            LaunchedEffect(Unit) { entered = true }
            val entrance by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = tween(420),
                label = "appEntrance"
            )
            val isDark = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
            FinancesTheme(darkTheme = isDark, palette = palette) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FinancesNavHost(Modifier.graphicsLayer {
                        alpha = entrance
                        scaleX = 0.985f + entrance * 0.015f
                        scaleY = scaleX
                        translationY = (1f - entrance) * 12.dp.toPx()
                    })
                }
            }
        }
    }
}
