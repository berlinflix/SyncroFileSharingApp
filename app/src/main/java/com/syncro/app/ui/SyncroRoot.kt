package com.syncro.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.syncro.app.ui.screens.HistoryScreen
import com.syncro.app.ui.screens.HomeActions
import com.syncro.app.ui.screens.HomeScreen
import com.syncro.app.ui.screens.IncomingRequestDialog
import com.syncro.app.ui.screens.OnboardingScreen
import com.syncro.app.ui.screens.ReceiveQrScreen
import com.syncro.app.ui.screens.SendScreen
import com.syncro.app.ui.screens.SettingsActions
import com.syncro.app.ui.screens.SettingsScreen
import com.syncro.app.ui.screens.SettingsState
import com.syncro.app.ui.screens.TextComposeDialog
import com.syncro.app.ui.screens.TransferScreen
import com.syncro.app.ui.theme.Syncro
import com.syncro.app.ui.theme.SyncroTheme

@Composable
fun SyncroRoot(vm: AppViewModel, onRequestPermissions: () -> Unit) {
    val theme by vm.settings.theme.collectAsStateWithLifecycle()
    SyncroTheme(theme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Syncro.colors.background),
        ) {
            val onboarded by vm.settings.onboarded.collectAsStateWithLifecycle()
            if (!onboarded) {
                val name by vm.settings.deviceName.collectAsStateWithLifecycle()
                OnboardingScreen(initialName = name) { chosen ->
                    vm.settings.setDeviceName(chosen)
                    vm.settings.setOnboarded(true)
                    onRequestPermissions()
                }
            } else {
                MainContent(vm, onRequestPermissions)
            }
        }
    }
}

@Composable
private fun MainContent(vm: AppViewModel, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var composingText by remember { mutableStateOf(false) }

    val screen by vm.screen.collectAsStateWithLifecycle()
    val deviceName by vm.settings.deviceName.collectAsStateWithLifecycle()
    val visibleSetting by vm.settings.visible.collectAsStateWithLifecycle()
    val backgroundVisible by vm.settings.backgroundVisible.collectAsStateWithLifecycle()
    val autoAccept by vm.settings.autoAcceptTrusted.collectAsStateWithLifecycle()
    val theme by vm.settings.theme.collectAsStateWithLifecycle()
    val network by vm.network.collectAsStateWithLifecycle()
    val nearbyAvailable by vm.nearbyAvailable.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val resolving by vm.resolving.collectAsStateWithLifecycle()
    val requests by vm.pendingRequests.collectAsStateWithLifecycle()
    val devices by vm.knownDevices.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val lanPort by vm.lanPort.collectAsStateWithLifecycle()
    val selfType = vm.engine.self.type

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.addUris(uris) }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris -> vm.addUris(uris) }

    val pickFiles = { filePicker.launch(arrayOf("*/*")) }
    val pickMedia = { mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    val scanQr: () -> Unit = {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
        val activity = context as? Activity
        val scanner = if (activity != null) GmsBarcodeScanning.getClient(activity, options) else GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(vm::onQrScanned) }
            .addOnFailureListener { vm.message("QR scanner unavailable: ${it.message}") }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbar.showSnackbar(event.text)
                UiEvent.PickFiles -> pickFiles()
            }
        }
    }

    BackHandler(enabled = screen != Screen.Home) { vm.back() }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn() + slideInHorizontally { it / 12 }) togetherWith fadeOut()
        },
        label = "screens",
        modifier = Modifier.fillMaxSize(),
    ) { current ->
        when (current) {
            Screen.Home -> HomeScreen(
                deviceName = deviceName,
                deviceType = selfType,
                visible = visibleSetting,
                network = network,
                nearbySupported = vm.nearbySupported,
                nearbyAvailable = nearbyAvailable,
                activeTransfers = transfers.filter { it.isActive && it.phase != com.syncro.core.transfer.TransferPhase.AWAITING_DECISION },
                recent = history,
                actions = HomeActions(
                    onPickFiles = pickFiles,
                    onPickMedia = pickMedia,
                    onSendText = { composingText = true },
                    onScanQr = scanQr,
                    onShowQr = { vm.navigate(Screen.ReceiveQr) },
                    onToggleVisible = vm::setVisible,
                    onOpenHistory = { vm.navigate(Screen.History) },
                    onOpenSettings = { vm.navigate(Screen.Settings) },
                    onOpenTransfer = { vm.navigate(Screen.Transfer(it)) },
                    onGrantNearby = onRequestPermissions,
                ),
            )
            Screen.Send -> SendScreen(
                selfType = selfType,
                selection = selection,
                resolving = resolving,
                peers = peers,
                onBack = vm::back,
                onAddMore = pickFiles,
                onRemove = vm::removeItem,
                onSend = vm::sendTo,
                onScanQr = scanQr,
                onSendToAddress = vm::sendToAddress,
                onScanningChanged = vm::setScanning,
            )
            is Screen.Transfer -> {
                val transfer = transfers.firstOrNull { it.id == current.id }
                TransferScreen(
                    transfer = transfer,
                    selfType = selfType,
                    canRetry = vm.canRetry(current.id),
                    onClose = { vm.finishTransfer(transfer) },
                    onCancel = { vm.cancel(current.id) },
                    onRetry = { vm.retry(current.id) },
                )
            }
            Screen.ReceiveQr -> ReceiveQrScreen(
                link = if (lanPort != null && addresses.isNotEmpty()) vm.connectLink() else null,
                visible = visibleSetting,
                fingerprint = vm.identity.displayFingerprint,
                onBack = vm::back,
                onMakeVisible = { vm.setVisible(true) },
            )
            Screen.History -> HistoryScreen(
                entries = history,
                onBack = vm::back,
                onClear = vm::clearHistory,
                onRemove = vm::removeHistory,
            )
            Screen.Settings -> SettingsScreen(
                state = SettingsState(
                    deviceName = deviceName,
                    deviceType = selfType,
                    fingerprint = vm.identity.displayFingerprint,
                    visible = visibleSetting,
                    backgroundVisible = backgroundVisible,
                    autoAcceptTrusted = autoAccept,
                    theme = theme,
                    nearbySupported = vm.nearbySupported,
                    nearbyAvailable = nearbyAvailable,
                    devices = devices,
                ),
                actions = SettingsActions(
                    onBack = vm::back,
                    onRename = vm.settings::setDeviceName,
                    onVisible = vm::setVisible,
                    onBackgroundVisible = vm.settings::setBackgroundVisible,
                    onAutoAccept = vm.settings::setAutoAcceptTrusted,
                    onTheme = vm.settings::setTheme,
                    onGrantNearby = onRequestPermissions,
                    onTrust = vm::setTrusted,
                    onForget = vm::forgetDevice,
                ),
            )
        }
    }

    requests.firstOrNull()?.let { request ->
        IncomingRequestDialog(request) { decision -> vm.respond(request.id, decision) }
    }

    if (composingText) {
        TextComposeDialog(onDismiss = { composingText = false }, onSend = {
            composingText = false
            vm.addText(it)
        })
    }

    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(snackbar) { data ->
            Snackbar(data, containerColor = Syncro.colors.surfaceHigh, contentColor = Syncro.colors.text, actionColor = Syncro.colors.accent)
        }
    }
}
