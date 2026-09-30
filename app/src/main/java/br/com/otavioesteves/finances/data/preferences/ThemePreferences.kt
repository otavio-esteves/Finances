package br.com.otavioesteves.finances.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val _isDark = MutableStateFlow(preferences.getBoolean("dark_theme", true))
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        if (_isDark.value == enabled) return
        _isDark.value = enabled
        preferences.edit().putBoolean("dark_theme", enabled).apply()
    }
}
