package com.syncro.desktop

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

object Platform {
    private val os = System.getProperty("os.name").lowercase()
    val isWindows = os.contains("win")
    val isMac = os.contains("mac")

    fun openFile(file: File) {
        runCatching {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file) else fallbackOpen(file.absolutePath)
        }.onFailure { fallbackOpen(file.absolutePath) }
    }

    fun openFolder(dir: File) {
        dir.mkdirs()
        openFile(dir)
    }

    fun revealFile(file: File) {
        runCatching {
            when {
                isWindows -> ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                isMac -> ProcessBuilder("open", "-R", file.absolutePath).start()
                else -> openFolder(file.parentFile)
            }
        }
    }

    fun openUrl(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url.trim())) }
    }

    fun copy(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    fun clipboardText(): String? = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
    }.getOrNull()

    fun hasBattery(): Boolean = runCatching {
        when {
            isWindows -> {
                val process = ProcessBuilder("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_Battery | Measure-Object).Count")
                    .redirectErrorStream(true).start()
                val out = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                (out.lines().lastOrNull()?.trim()?.toIntOrNull() ?: 0) > 0
            }
            isMac -> File("/Applications").exists() && ProcessBuilder("pmset", "-g", "batt").start().inputStream.bufferedReader().readText().contains("InternalBattery")
            else -> File("/sys/class/power_supply").listFiles()?.any { it.name.startsWith("BAT") } == true
        }
    }.getOrDefault(false)

    private fun fallbackOpen(path: String) {
        runCatching {
            when {
                isWindows -> ProcessBuilder("cmd", "/c", "start", "", path).start()
                isMac -> ProcessBuilder("open", path).start()
                else -> ProcessBuilder("xdg-open", path).start()
            }
        }
    }
}
