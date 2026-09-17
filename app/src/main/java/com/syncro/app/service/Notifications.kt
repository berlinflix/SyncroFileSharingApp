package com.syncro.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.syncro.app.MainActivity
import com.syncro.app.R
import com.syncro.app.data.AndroidReceiveStorage
import com.syncro.core.transfer.Direction
import com.syncro.core.transfer.TransferInfo
import com.syncro.core.transfer.TransferPhase
import com.syncro.core.util.Format

class Notifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val system = context.getSystemService(NotificationManager::class.java)
            system.createNotificationChannels(
                listOf(
                    NotificationChannel(CHANNEL_STATUS, "Sharing status", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shown while Syncro is visible or transferring"
                        setShowBadge(false)
                    },
                    NotificationChannel(CHANNEL_REQUESTS, "Incoming shares", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Someone nearby wants to send you something"
                    },
                    NotificationChannel(CHANNEL_DONE, "Completed transfers", NotificationManager.IMPORTANCE_DEFAULT),
                ),
            )
        }
    }

    fun openAppIntent(transferId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (transferId != null) putExtra(MainActivity.EXTRA_TRANSFER_ID, transferId)
        }
        return PendingIntent.getActivity(
            context,
            transferId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun statusNotification(transfers: List<TransferInfo>, visible: Boolean, deviceName: String): Notification {
        val active = transfers.filter { it.isActive && it.phase != TransferPhase.AWAITING_DECISION }
        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent(active.singleOrNull()?.id))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (active.isEmpty()) {
            builder.setContentTitle(if (visible) "Ready to receive" else "Syncro")
                .setContentText(if (visible) "Visible to nearby devices as $deviceName" else "Finishing up…")
            if (visible) {
                builder.addAction(0, "Hide", NotificationActionReceiver.intent(context, NotificationActionReceiver.ACTION_HIDE, null))
            }
            return builder.build()
        }

        val transfer = active.first()
        val verb = if (transfer.direction == Direction.SEND) "Sending to" else "Receiving from"
        val title = if (active.size > 1) "${active.size} transfers in progress" else "$verb ${transfer.peerName}"
        val detail = when (transfer.phase) {
            TransferPhase.CONNECTING -> "Connecting…"
            TransferPhase.WAITING_FOR_ACCEPT -> "Waiting for ${transfer.peerName} to accept · PIN ${transfer.pin}"
            else -> "${(transfer.fraction * 100).toInt()}% · ${Format.speed(transfer.bytesPerSecond)}"
        }
        builder.setContentTitle(title).setContentText(detail)
        if (transfer.phase == TransferPhase.TRANSFERRING) {
            builder.setProgress(1000, (transfer.fraction * 1000).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        builder.addAction(0, "Cancel", NotificationActionReceiver.intent(context, NotificationActionReceiver.ACTION_CANCEL, transfer.id))
        return builder.build()
    }

    fun showIncomingRequest(transfer: TransferInfo) {
        val count = transfer.items.size
        val summary = if (transfer.items.all { it.isText }) "Text" else "$count ${if (count == 1) "item" else "items"} · ${Format.bytes(transfer.totalBytes)}"
        val notification = NotificationCompat.Builder(context, CHANNEL_REQUESTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${transfer.peerName} wants to share")
            .setContentText("$summary · PIN ${transfer.pin}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setTimeoutAfter(120_000)
            .setContentIntent(openAppIntent(transfer.id))
            .addAction(0, "Decline", NotificationActionReceiver.intent(context, NotificationActionReceiver.ACTION_DECLINE, transfer.id))
            .addAction(0, "Accept", NotificationActionReceiver.intent(context, NotificationActionReceiver.ACTION_ACCEPT, transfer.id))
            .build()
        notify(transfer.id.hashCode(), notification)
    }

    fun cancelIncomingRequest(transferId: String) = manager.cancel(transferId.hashCode())

    fun showFinished(transfer: TransferInfo) {
        val received = transfer.direction == Direction.RECEIVE
        val count = transfer.items.size
        val noun = if (count == 1) transfer.items.first().name else "$count items"
        val (title, text) = when (transfer.phase) {
            TransferPhase.COMPLETED ->
                if (received) "Received $noun" to "From ${transfer.peerName} · saved to Downloads/Syncro"
                else "Sent $noun" to "To ${transfer.peerName}"
            TransferPhase.REJECTED -> "${transfer.peerName} declined" to (transfer.message ?: "Declined")
            TransferPhase.CANCELLED -> "Transfer cancelled" to (transfer.message ?: "Cancelled")
            else -> "Transfer failed" to (transfer.message ?: "Something went wrong")
        }
        val singleFile = transfer.received.singleOrNull()?.location
        val contentIntent = if (received && transfer.phase == TransferPhase.COMPLETED && singleFile != null) {
            val uri = AndroidReceiveStorage.uriFor(context, singleFile)
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, transfer.received.single().mimeType ?: context.contentResolver.getType(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            PendingIntent.getActivity(context, transfer.id.hashCode(), Intent.createChooser(view, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        } else {
            openAppIntent(transfer.id)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        notify(transfer.id.hashCode() + 1, notification)
    }

    private fun notify(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(id, notification)
    }

    companion object {
        const val CHANNEL_STATUS = "status"
        const val CHANNEL_REQUESTS = "requests"
        const val CHANNEL_DONE = "done"
        const val STATUS_ID = 1
    }
}
