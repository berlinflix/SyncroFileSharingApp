package com.syncro.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SettingsState(
    val deviceName: String,
    val downloadDir: String,
    val visible: Boolean = true,
    val autoAcceptTrusted: Boolean = true,
    val openFolderOnReceive: Boolean = false,
    val closeToTray: Boolean = true,
    val theme: String = "system",
)

class DesktopSettings(private val file: File, defaultName: String) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val _state = MutableStateFlow(load(defaultName))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun update(transform: (SettingsState) -> SettingsState) {
        _state.update(transform)
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.encodeToString(SettingsState.serializer(), _state.value))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }

    private fun load(defaultName: String): SettingsState {
        val fallback = SettingsState(deviceName = defaultName, downloadDir = defaultDownloads().absolutePath)
        if (!file.isFile) return fallback
        return runCatching { json.decodeFromString(SettingsState.serializer(), file.readText()) }.getOrDefault(fallback)
    }

    companion object {
        fun defaultDownloads(): File = File(File(System.getProperty("user.home"), "Downloads"), "Syncro")
    }
}
