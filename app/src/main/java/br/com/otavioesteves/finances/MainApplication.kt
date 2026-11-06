package br.com.otavioesteves.finances

import android.app.Application
import android.content.ComponentCallbacks2
import br.com.otavioesteves.finances.di.AppContainer
import br.com.otavioesteves.finances.di.DefaultAppContainer
import br.com.otavioesteves.finances.data.preferences.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())
    val themePreferences by lazy { ThemePreferences(this) }
    val invoiceReminders by lazy { br.com.otavioesteves.finances.data.notifications.InvoiceReminders(this) }

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this, applicationScope)
        invoiceReminders.schedule()
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            applicationScope.launch { container.localAiEngine.release() }
        }
    }
}
