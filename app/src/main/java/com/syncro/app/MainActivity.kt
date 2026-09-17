package com.syncro.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.syncro.app.net.NearbyTransport
import com.syncro.app.ui.AppViewModel
import com.syncro.app.ui.SyncroRoot

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SyncroRoot(vm, onRequestPermissions = ::requestPermissions)
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onPermissionsChanged()
    }

    private fun requestPermissions() {
        val permissions = buildList {
            addAll(NearbyTransport.requiredPermissions())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_TRANSFER_ID)?.let {
            vm.openTransfer(it)
            return
        }
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                when {
                    stream != null -> vm.addUris(listOf(stream))
                    !text.isNullOrBlank() -> vm.addText(text)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streams = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                vm.addUris(streams)
            }
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { it.startsWith("syncro://") }?.let(vm::onQrScanned)
        }
    }

    companion object {
        const val EXTRA_TRANSFER_ID = "transfer_id"
    }
}
