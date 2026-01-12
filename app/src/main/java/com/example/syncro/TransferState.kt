package com.example.syncro

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// simple data class for a device
data class DiscoveredDevice(val ip: String, val name: String)

object TransferState {

    // Existing State
    private val _progress = MutableStateFlow(0)
    val progress = _progress.asStateFlow()

    private val _receivedFileUri = MutableStateFlow<Uri?>(null)
    val receivedFileUri = _receivedFileUri.asStateFlow()

    private val _completed = MutableStateFlow(false)
    val completed = _completed.asStateFlow()

    private val _role = MutableStateFlow<TransferRole?>(null)
    val role = _role.asStateFlow()

    // 🆕 NEW: List of found devices
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    // 🆕 NEW: The file waiting to be sent (we hold it while choosing a device)
    var pendingFile: Pair<String, ByteArray>? = null

    fun start(role: TransferRole) {
        _role.value = role
        _progress.value = 0
        _completed.value = false
        // Clear devices when starting a new session
        if (role == TransferRole.SENDER) {
            _discoveredDevices.value = emptyList()
        }
    }

    fun addDevice(device: DiscoveredDevice) {
        // Avoid duplicates
        val currentList = _discoveredDevices.value.toMutableList()
        if (currentList.none { it.ip == device.ip }) {
            currentList.add(device)
            _discoveredDevices.value = currentList
        }
    }

    fun updateProgress(value: Int) {
        _progress.value = value.coerceIn(0, 100)
    }

    fun markCompleted() {
        _progress.value = 100
        _completed.value = true
    }

    fun setReceivedFile(uri: Uri) {
        _receivedFileUri.value = uri
    }

    fun reset() {
        _role.value = null
        _progress.value = 0
        _completed.value = false
        _receivedFileUri.value = null
        _discoveredDevices.value = emptyList()
        pendingFile = null
    }
}