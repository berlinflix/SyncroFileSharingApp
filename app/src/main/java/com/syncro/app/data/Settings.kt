package com.syncro.app.data

import android.content.Context
import android.os.Build
import android.provider.Settings as SystemSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("syncro", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    private val _deviceName = MutableStateFlow(prefs.getString(KEY_NAME, null) ?: defaultDeviceName())
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _visible = MutableStateFlow(prefs.getBoolean(KEY_VISIBLE, true))
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _backgroundVisible = MutableStateFlow(prefs.getBoolean(KEY_BACKGROUND, true))
    val backgroundVisible: StateFlow<Boolean> = _backgroundVisible.asStateFlow()

    private val _autoAcceptTrusted = MutableStateFlow(prefs.getBoolean(KEY_AUTO_ACCEPT, true))
    val autoAcceptTrusted: StateFlow<Boolean> = _autoAcceptTrusted.asStateFlow()

    private val _onboarded = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    private val _theme = MutableStateFlow(runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null)!!) }.getOrDefault(ThemeMode.SYSTEM))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setDeviceName(name: String) {
        val cleaned = name.trim().take(40).ifEmpty { defaultDeviceName() }
        prefs.edit().putString(KEY_NAME, cleaned).apply()
        _deviceName.value = cleaned
    }

    fun setVisible(value: Boolean) = putBoolean(KEY_VISIBLE, value, _visible)
    fun setBackgroundVisible(value: Boolean) = putBoolean(KEY_BACKGROUND, value, _backgroundVisible)
    fun setAutoAcceptTrusted(value: Boolean) = putBoolean(KEY_AUTO_ACCEPT, value, _autoAcceptTrusted)
    fun setOnboarded(value: Boolean) = putBoolean(KEY_ONBOARDED, value, _onboarded)

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    private fun putBoolean(key: String, value: Boolean, flow: MutableStateFlow<Boolean>) {
        prefs.edit().putBoolean(key, value).apply()
        flow.value = value
    }

    private fun defaultDeviceName(): String {
        val userSet = runCatching { SystemSettings.Global.getString(appContext.contentResolver, SystemSettings.Global.DEVICE_NAME) }.getOrNull()
        if (!userSet.isNullOrBlank()) return userSet.take(40)
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return (if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model").trim().take(40)
    }

    private companion object {
        const val KEY_NAME = "device_name"
        const val KEY_VISIBLE = "visible"
        const val KEY_BACKGROUND = "background_visible"
        const val KEY_AUTO_ACCEPT = "auto_accept_trusted"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_THEME = "theme"
    }
}
