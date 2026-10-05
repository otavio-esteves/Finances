package br.com.otavioesteves.finances.data.preferences

import android.content.Context
import br.com.otavioesteves.finances.domain.model.ThemeMode
import br.com.otavioesteves.finances.domain.model.ThemePalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == preferences.getString("theme_mode", null) }
            ?: if (preferences.getBoolean("dark_theme", true)) ThemeMode.DARK else ThemeMode.LIGHT
    )
    val mode = _mode.asStateFlow()
    private val _palette = MutableStateFlow(
        ThemePalette.entries.firstOrNull { it.name == preferences.getString("theme_palette", null) }
            ?: ThemePalette.RED
    )
    val palette = _palette.asStateFlow()

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        preferences.edit().putString("theme_mode", mode.name).apply()
    }

    fun setPalette(palette: ThemePalette) {
        _palette.value = palette
        preferences.edit().putString("theme_palette", palette.name).apply()
    }
}
