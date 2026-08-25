package com.cmac.opscommand

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Brings the dashboard back up by itself after a power cut or a TV firmware
 * restart. A wall display nobody logs into needs to recover without someone
 * walking over with a remote — this is the main operational win of shipping
 * native rather than leaving a browser tab open.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(launch) }
    }
}
