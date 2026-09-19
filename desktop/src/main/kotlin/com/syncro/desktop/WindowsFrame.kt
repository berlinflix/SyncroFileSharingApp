package com.syncro.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import java.awt.Window

/**
 * Windows 11 DWM touches for the frameless window: rounded corners and a border tinted to the
 * design's outline colour. Silently does nothing on older Windows or other platforms.
 */
object WindowsFrame {
    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWCP_ROUND = 2

    @Suppress("FunctionName")
    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: HWND, attribute: Int, value: Pointer, size: Int): Int
    }

    private val dwm: DwmApi? by lazy {
        if (!Platform.isWindows) null else runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull()
    }

    fun style(window: Window, borderRgb: Int) {
        val api = dwm ?: return
        runCatching {
            val hwnd = HWND(Native.getWindowPointer(window))
            api.DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, int(DWMWCP_ROUND), 4)
            // COLORREF is 0x00BBGGRR.
            val r = (borderRgb shr 16) and 0xff
            val g = (borderRgb shr 8) and 0xff
            val b = borderRgb and 0xff
            api.DwmSetWindowAttribute(hwnd, DWMWA_BORDER_COLOR, int((b shl 16) or (g shl 8) or r), 4)
        }
    }

    private fun int(value: Int) = Memory(4).apply { setInt(0, value) }
}
