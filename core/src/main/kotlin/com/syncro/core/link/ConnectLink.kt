package com.syncro.core.link

import com.syncro.core.DeviceType
import com.syncro.core.Syncro
import com.syncro.core.sanitizeDeviceName
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Contents of a Syncro QR code: where a device listens and which identity to expect there.
 * Scanning pins the device id, so a spoofed device on the network cannot intercept the transfer.
 *
 * `syncro://connect?v=1&id=<device id>&n=<name>&t=<type>&p=<port>&h=<ip>,<ip>`
 */
data class ConnectLink(
    val id: String,
    val name: String,
    val type: DeviceType,
    val hosts: List<String>,
    val port: Int,
) {
    fun toUri(): String = buildString {
        append("syncro://connect?v=1")
        append("&id=").append(id)
        append("&n=").append(URLEncoder.encode(name, "UTF-8"))
        append("&t=").append(type.name)
        append("&p=").append(port)
        append("&h=").append(hosts.joinToString(","))
    }

    companion object {
        private val ID_PATTERN = Regex("^[0-9a-f]{16}$")
        private val HOST_PATTERN = Regex("^[0-9A-Za-z.:\\-\\[\\]%]{1,64}$")

        fun parse(text: String): ConnectLink? {
            val trimmed = text.trim()
            if (!trimmed.startsWith("syncro://connect?", ignoreCase = true)) return null
            val params = trimmed.substringAfter('?').split('&').mapNotNull { part ->
                val key = part.substringBefore('=', "")
                if (key.isEmpty()) null else key to URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
            }.toMap()
            val id = params["id"]?.lowercase()?.takeIf { ID_PATTERN.matches(it) } ?: return null
            val port = params["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: Syncro.TRANSFER_PORT
            val hosts = params["h"].orEmpty().split(',').map { it.trim() }.filter { HOST_PATTERN.matches(it) }.take(8)
            if (hosts.isEmpty()) return null
            return ConnectLink(id, sanitizeDeviceName(params["n"]), DeviceType.parse(params["t"]), hosts, port)
        }
    }
}
