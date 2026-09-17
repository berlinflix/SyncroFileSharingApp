package com.syncro.app.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.syncro.app.graph
import com.syncro.core.transfer.Decision

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.graph
        val transferId = intent.getStringExtra(EXTRA_ID)
        when (intent.action) {
            ACTION_ACCEPT -> transferId?.let {
                graph.engine.respond(it, Decision.ACCEPT)
                graph.notifications.cancelIncomingRequest(it)
            }
            ACTION_DECLINE -> transferId?.let {
                graph.engine.respond(it, Decision.DECLINE)
                graph.notifications.cancelIncomingRequest(it)
            }
            ACTION_CANCEL -> transferId?.let(graph.engine::cancel)
            ACTION_HIDE -> graph.settings.setVisible(false)
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.syncro.app.ACCEPT"
        const val ACTION_DECLINE = "com.syncro.app.DECLINE"
        const val ACTION_CANCEL = "com.syncro.app.CANCEL"
        const val ACTION_HIDE = "com.syncro.app.HIDE"
        private const val EXTRA_ID = "transfer_id"

        fun intent(context: Context, action: String, transferId: String?): PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, transferId)
            return PendingIntent.getBroadcast(
                context,
                (action + transferId).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
