package br.com.otavioesteves.finances

import android.app.Application
import br.com.otavioesteves.finances.di.AppContainer
import br.com.otavioesteves.finances.di.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MainApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this, applicationScope)
    }
}
